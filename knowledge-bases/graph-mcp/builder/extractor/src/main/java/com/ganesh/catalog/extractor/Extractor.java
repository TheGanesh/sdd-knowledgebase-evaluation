package com.ganesh.catalog.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * v1 weekly-batch extractor. Scans one Spring Boot service's source and emits
 * {@code catalog-info.yaml}. Heuristic but deterministic; covers the common Spring idioms:
 * <ul>
 *   <li>inbound REST — {@code @RestController} + mapping annotations</li>
 *   <li>outbound REST — {@code @FeignClient} interfaces</li>
 *   <li>Kafka — {@code @KafkaListener} (consume) and {@code KafkaTemplate.send(TOPIC, ..)} (produce)</li>
 *   <li>DB — {@code @Entity}/{@code @Table}</li>
 *   <li>cache — {@code @Cacheable}/{@code @CacheEvict}/{@code @CachePut}</li>
 * </ul>
 * OpenAPI/AsyncAPI are harvested separately (springdoc /v3/api-docs).
 *
 * <p>Usage: {@code Extractor <serviceModuleDir> <outputDir>}
 */
public final class Extractor {

    private static final List<String> HTTP = List.of("GetMapping", "PostMapping", "PutMapping", "PatchMapping", "DeleteMapping");
    private static final Map<String, String> HTTP_VERB = Map.of(
            "GetMapping", "GET", "PostMapping", "POST", "PutMapping", "PUT",
            "PatchMapping", "PATCH", "DeleteMapping", "DELETE");

    private final Map<String, String> constants = new HashMap<>();
    private final List<ClassOrInterfaceDeclaration> types = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: Extractor <serviceModuleDir> <outputDir>");
            System.exit(2);
        }
        Path module = Path.of(args[0]).toAbsolutePath().normalize();
        Path outDir = Path.of(args[1]).toAbsolutePath().normalize();
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        new Extractor().run(module, outDir);
    }

    void run(Path module, Path outDir) throws Exception {
        Path srcRoot = module.resolve("src/main/java");
        try (Stream<Path> files = Files.walk(srcRoot)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(this::parse);
        }
        collectConstants();

        Map<String, Object> meta = readAppMeta(module.resolve("src/main/resources/application.yml"));
        String name = (String) meta.getOrDefault("name", module.getFileName().toString());

        List<Map<String, Object>> inbound = new ArrayList<>();
        List<Map<String, Object>> outbound = new ArrayList<>();
        List<Map<String, Object>> produced = new ArrayList<>();
        List<Map<String, Object>> consumed = new ArrayList<>();
        Set<String> tables = new TreeSet<>();
        Set<String> cacheRegions = new TreeSet<>();
        Set<String> consumesServices = new LinkedHashSet<>();

        for (ClassOrInterfaceDeclaration type : types) {
            if (has(type, "RestController")) {
                inbound.addAll(inboundApis(type));
            }
            if (has(type, "FeignClient")) {
                List<Map<String, Object>> calls = feignApis(type);
                outbound.addAll(calls);
                calls.forEach(c -> consumesServices.add((String) c.get("targetService")));
            }
            if (has(type, "Entity")) {
                tables.add(tableName(type));
            }
            consumed.addAll(kafkaConsumed(type));
            produced.addAll(kafkaProduced(type));
            cacheRegions.addAll(cacheRegions(type));
        }

        Map<String, Object> descriptor = buildDescriptor(
                name, meta, inbound, outbound, produced, consumed, tables, cacheRegions, consumesServices);

        Files.createDirectories(outDir);
        Path out = outDir.resolve("catalog-info.yaml");
        YAMLMapper writer = YAMLMapper.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .build();
        Files.writeString(out, writer.writeValueAsString(descriptor));
        System.out.println("Extracted " + name + " -> " + out);
        System.out.println("  inbound=" + inbound.size() + " outbound=" + outbound.size()
                + " produced=" + produced.size() + " consumed=" + consumed.size()
                + " tables=" + tables + " caches=" + cacheRegions);
    }

    // ---------------------------------------------------------------- parsing

    private void parse(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(types::add);
        } catch (Exception e) {
            System.err.println("  [warn] could not parse " + file.getFileName() + ": " + e.getMessage());
        }
    }

    private void collectConstants() {
        for (ClassOrInterfaceDeclaration type : types) {
            for (FieldDeclaration field : type.getFields()) {
                field.getVariables().forEach(v -> v.getInitializer().ifPresent(init -> {
                    if (init.isStringLiteralExpr()) {
                        constants.put(v.getNameAsString(), init.asStringLiteralExpr().asString());
                    }
                }));
            }
        }
    }

    // ---------------------------------------------------------------- inbound

    private List<Map<String, Object>> inboundApis(ClassOrInterfaceDeclaration controller) {
        String base = annotationString(controller, "RequestMapping").orElse("");
        List<Map<String, Object>> ops = new ArrayList<>();
        for (MethodDeclaration m : controller.getMethods()) {
            for (String mapping : HTTP) {
                Optional<AnnotationExpr> a = m.getAnnotationByName(mapping);
                if (a.isEmpty()) {
                    continue;
                }
                String sub = annotationValue(a.get()).orElse("");
                Map<String, Object> op = new LinkedHashMap<>();
                op.put("operationId", m.getNameAsString());
                op.put("method", HTTP_VERB.get(mapping));
                op.put("path", join(base, sub));
                op.put("summary", javadoc(m).orElse(HTTP_VERB.get(mapping) + " " + join(base, sub)));
                requestBodyType(m).ifPresent(t -> op.put("requestSchema", t));
                op.put("responseSchema", unwrap(m.getType()));
                ops.add(op);
            }
        }
        return ops;
    }

    // --------------------------------------------------------------- outbound

    private List<Map<String, Object>> feignApis(ClassOrInterfaceDeclaration client) {
        AnnotationExpr feign = client.getAnnotationByName("FeignClient").orElseThrow();
        String target = attr(feign, "name").or(() -> attr(feign, "value")).orElse("unknown");
        String base = attr(feign, "path").orElse("");
        List<Map<String, Object>> ops = new ArrayList<>();
        for (MethodDeclaration m : client.getMethods()) {
            for (String mapping : HTTP) {
                Optional<AnnotationExpr> a = m.getAnnotationByName(mapping);
                if (a.isEmpty()) {
                    continue;
                }
                String sub = annotationValue(a.get()).orElse("");
                Map<String, Object> op = new LinkedHashMap<>();
                op.put("targetService", target);
                op.put("operationId", m.getNameAsString());
                op.put("method", HTTP_VERB.get(mapping));
                op.put("path", join(base, sub));
                requestBodyType(m).ifPresent(t -> op.put("requestSchema", t));
                op.put("responseSchema", unwrap(m.getType()));
                op.put("callSite", client.getNameAsString() + "#" + m.getNameAsString());
                ops.add(op);
            }
        }
        return ops;
    }

    // ------------------------------------------------------------------ kafka

    private List<Map<String, Object>> kafkaConsumed(ClassOrInterfaceDeclaration type) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (MethodDeclaration m : type.getMethods()) {
            Optional<AnnotationExpr> listener = m.getAnnotationByName("KafkaListener");
            if (listener.isEmpty()) {
                continue;
            }
            String topic = attr(listener.get(), "topics").orElse(null);
            if (topic == null) {
                continue;
            }
            String schema = m.getParameters().stream()
                    .map(p -> simple(p.getType().asString()))
                    .filter(t -> !t.startsWith("ConsumerRecord") && !t.equals("String"))
                    .findFirst().orElse("Object");
            out.add(topicRef(topic, schema));
        }
        return out;
    }

    private List<Map<String, Object>> kafkaProduced(ClassOrInterfaceDeclaration type) {
        String valueType = type.getFields().stream()
                .filter(f -> f.getElementType().asString().startsWith("KafkaTemplate"))
                .map(f -> kafkaTemplateValueType(f.getElementType()))
                .findFirst().orElse("Object");
        List<Map<String, Object>> out = new ArrayList<>();
        for (MethodCallExpr call : type.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("send") || call.getArguments().isEmpty()) {
                continue;
            }
            String topic = resolve(call.getArgument(0));
            if (topic != null) {
                out.add(topicRef(topic, valueType));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ db/cache

    private String tableName(ClassOrInterfaceDeclaration entity) {
        return attr(entity.getAnnotationByName("Table").orElse(null), "name")
                .orElse(entity.getNameAsString().toLowerCase());
    }

    private Set<String> cacheRegions(ClassOrInterfaceDeclaration type) {
        Set<String> regions = new TreeSet<>();
        for (AnnotationExpr a : type.findAll(AnnotationExpr.class)) {
            String n = a.getNameAsString();
            if (n.equals("Cacheable") || n.equals("CacheEvict") || n.equals("CachePut")) {
                attr(a, "value").or(() -> attr(a, "cacheNames")).or(() -> annotationValue(a))
                        .ifPresent(regions::add);
            }
        }
        return regions;
    }

    // --------------------------------------------------------------- assemble

    private Map<String, Object> buildDescriptor(String name, Map<String, Object> meta,
                                                List<Map<String, Object>> inbound, List<Map<String, Object>> outbound,
                                                List<Map<String, Object>> produced, List<Map<String, Object>> consumed,
                                                Set<String> tables, Set<String> caches, Set<String> consumesServices) {
        Set<String> topics = new TreeSet<>();
        produced.forEach(t -> topics.add((String) t.get("topic")));
        consumed.forEach(t -> topics.add((String) t.get("topic")));

        List<String> dependsOn = new ArrayList<>();
        if (!tables.isEmpty()) {
            dependsOn.add("resource:db/" + tables.iterator().next());
        }
        caches.forEach(c -> dependsOn.add("resource:cache/" + c));
        topics.forEach(t -> dependsOn.add("resource:topic/" + t));

        List<Map<String, Object>> datastores = new ArrayList<>();
        if (!tables.isEmpty()) {
            datastores.add(map("kind", "db", "name", tables.iterator().next(),
                    "engine", "postgres", "tables", new ArrayList<>(tables)));
        }
        for (String c : caches) {
            datastores.add(map("kind", "cache", "name", c, "engine", "caffeine",
                    "regions", List.of(c)));
        }

        Map<String, Object> xcatalog = new LinkedHashMap<>();
        xcatalog.put("inboundApis", inbound);
        xcatalog.put("outboundApis", outbound);
        xcatalog.put("topicsProduced", produced);
        xcatalog.put("topicsConsumed", consumed);
        xcatalog.put("datastores", datastores);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("type", "service");
        spec.put("lifecycle", "production");
        spec.put("owner", meta.getOrDefault("owner", "unknown"));
        spec.put("system", meta.getOrDefault("system", "unknown"));
        spec.put("domain", meta.getOrDefault("domain", "unknown"));
        spec.put("providesApis", inbound.isEmpty() ? List.of() : List.of(name + "-rest"));
        spec.put("consumesApis", consumesServices.stream().map(s -> s + "-rest").toList());
        spec.put("dependsOn", dependsOn);
        spec.put("x-catalog", xcatalog);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", name);
        metadata.put("description", "Extracted descriptor for " + name + " (regenerate weekly).");
        metadata.put("annotations", map("catalog.ganesh.dev/openapi", "./openapi.yaml",
                "catalog.ganesh.dev/asyncapi", "./asyncapi.yaml"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("apiVersion", "backstage.io/v1alpha1");
        root.put("kind", "Component");
        root.put("metadata", metadata);
        root.put("spec", spec);
        return root;
    }

    // --------------------------------------------------------------- helpers

    private Map<String, Object> readAppMeta(Path appYml) {
        Map<String, Object> meta = new HashMap<>();
        try {
            JsonNode root = new YAMLMapper().readTree(appYml.toFile());
            meta.put("name", root.path("spring").path("application").path("name").asText(null));
            meta.put("system", root.path("catalog").path("system").asText("unknown"));
            meta.put("domain", root.path("catalog").path("domain").asText("unknown"));
            meta.put("owner", root.path("catalog").path("owner").asText("unknown"));
        } catch (Exception e) {
            System.err.println("  [warn] no application.yml meta: " + e.getMessage());
        }
        return meta;
    }

    private Map<String, Object> topicRef(String topic, String schema) {
        return map("topic", topic, "messageSchema", schema);
    }

    private boolean has(ClassOrInterfaceDeclaration type, String annotation) {
        return type.getAnnotationByName(annotation).isPresent();
    }

    /** Resolve an annotation's "value"/"path"/named attr to a string (literal or constant). */
    private Optional<String> attr(AnnotationExpr a, String key) {
        if (a == null) {
            return Optional.empty();
        }
        if (a instanceof NormalAnnotationExpr n) {
            return n.getPairs().stream()
                    .filter(p -> p.getNameAsString().equals(key))
                    .findFirst()
                    .map(p -> resolve(p.getValue()));
        }
        return Optional.empty();
    }

    /** The single-member value of an annotation, e.g. @PostMapping("/reserve"). */
    private Optional<String> annotationValue(AnnotationExpr a) {
        if (a instanceof SingleMemberAnnotationExpr s) {
            return Optional.ofNullable(resolve(s.getMemberValue()));
        }
        if (a instanceof NormalAnnotationExpr) {
            return attr(a, "value").or(() -> attr(a, "path"));
        }
        return Optional.empty();
    }

    /** Like annotationValue but also resolves @RequestMapping on a class. */
    private Optional<String> annotationString(ClassOrInterfaceDeclaration type, String annotation) {
        return type.getAnnotationByName(annotation).flatMap(this::annotationValue);
    }

    private String resolve(Expression e) {
        if (e == null) {
            return null;
        }
        if (e.isStringLiteralExpr()) {
            return e.asStringLiteralExpr().asString();
        }
        if (e instanceof ArrayInitializerExpr arr && !arr.getValues().isEmpty()) {
            return resolve(arr.getValues().get(0));
        }
        if (e.isNameExpr()) {
            return constants.get(e.asNameExpr().getNameAsString());
        }
        if (e.isFieldAccessExpr()) {
            return constants.get(e.asFieldAccessExpr().getNameAsString());
        }
        return null;
    }

    private Optional<String> requestBodyType(MethodDeclaration m) {
        return m.getParameters().stream()
                .filter(p -> p.getAnnotationByName("RequestBody").isPresent())
                .map(p -> simple(p.getType().asString()))
                .findFirst();
    }

    private String kafkaTemplateValueType(Type templateType) {
        if (templateType instanceof ClassOrInterfaceType c && c.getTypeArguments().isPresent()) {
            var args = c.getTypeArguments().get();
            if (args.size() == 2) {
                return simple(args.get(1).asString());
            }
        }
        return "Object";
    }

    private String unwrap(Type type) {
        String s = type.asString();
        if (s.startsWith("ResponseEntity<") && s.endsWith(">")) {
            s = s.substring("ResponseEntity<".length(), s.length() - 1);
        }
        return simple(s);
    }

    private String simple(String type) {
        int lt = type.indexOf('<');
        if (lt >= 0) {
            type = type.substring(0, lt);
        }
        int dot = type.lastIndexOf('.');
        return (dot >= 0 ? type.substring(dot + 1) : type).trim();
    }

    private String join(String base, String sub) {
        String b = base == null ? "" : base.trim();
        String s = sub == null ? "" : sub.trim();
        if (s.isEmpty()) {
            return b.isEmpty() ? "/" : b;
        }
        if (b.isEmpty()) {
            return s.startsWith("/") ? s : "/" + s;
        }
        return (b.endsWith("/") ? b.substring(0, b.length() - 1) : b)
                + (s.startsWith("/") ? s : "/" + s);
    }

    private Optional<String> javadoc(MethodDeclaration m) {
        return m.getJavadoc().map(jd -> {
            String text = jd.getDescription().toText().strip();
            int dot = text.indexOf('.');
            return dot > 0 ? text.substring(0, dot + 1) : text;
        }).filter(s -> !s.isBlank());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private Extractor() {
    }
}
