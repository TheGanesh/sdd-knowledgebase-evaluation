package com.ganesh.catalog.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds the service-catalog dependency graph + reverse impact index from the
 * per-service catalog-info.yaml descriptors.
 *
 * <p>Outputs (to {@code <catalogRoot>/generated}):
 * <ul>
 *   <li>{@code catalog-summary.yaml} — compact always-in-context index (replaces the index markdown)</li>
 *   <li>{@code graph.json} — forward edges (provides/consumes/dependsOn/topics/datastores)</li>
 *   <li>{@code reverse-index.json} — "who is affected if X changes" lookups</li>
 * </ul>
 *
 * <p>Each descriptor is validated against {@code schema/service-descriptor.schema.json}
 * first; a missing/invalid contract fails the build (the completeness gate).
 *
 * <p>Usage: {@code GraphBuilder <catalogRoot>} (defaults to the current directory).
 */
public final class GraphBuilder {

    private static final YAMLMapper YAML = new YAMLMapper();
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Path catalogRoot = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path servicesDir = catalogRoot.resolve("services");
        Path schemaFile = catalogRoot.resolve("schema/service-descriptor.schema.json");
        Path generatedDir = catalogRoot.resolve("generated");
        boolean lenient = Stream.of(args).anyMatch("--lenient"::equals);

        if (!Files.isDirectory(servicesDir)) {
            System.err.println("No services/ directory under " + catalogRoot);
            System.exit(2);
        }
        Files.createDirectories(generatedDir);

        List<Path> descriptors;
        try (Stream<Path> s = Files.list(servicesDir)) {
            descriptors = s.filter(Files::isDirectory)
                    .map(d -> d.resolve("catalog-info.yaml"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (descriptors.isEmpty()) {
            System.err.println("No services/*/catalog-info.yaml found under " + servicesDir);
            System.exit(2);
        }

        JsonSchema schema = loadSchema(schemaFile);

        // ---- read + validate ----
        List<JsonNode> services = new ArrayList<>();
        int errorCount = 0;
        for (Path descriptor : descriptors) {
            JsonNode node = YAML.readTree(descriptor.toFile());
            Set<ValidationMessage> errors = schema.validate(node);
            String name = node.path("metadata").path("name").asText("<unknown>");
            if (errors.isEmpty()) {
                System.out.println("  [ok]   " + name);
            } else {
                errorCount += errors.size();
                System.out.println("  [FAIL] " + name + " (" + descriptor + ")");
                errors.stream().sorted((a, b) -> a.getMessage().compareTo(b.getMessage()))
                        .forEach(e -> System.out.println("           - " + e.getMessage()));
            }
            services.add(node);
        }
        if (errorCount > 0 && !lenient) {
            System.err.println("\nSchema validation failed: " + errorCount
                    + " error(s). Fix the descriptors or re-run with --lenient.");
            System.exit(1);
        }

        // ---- build outputs ----
        ObjectNode graph = buildGraph(services);
        ObjectNode reverse = buildReverseIndex(services);
        writeSummary(services, generatedDir.resolve("catalog-summary.yaml"));
        writePrettyJson(graph, generatedDir.resolve("graph.json"));
        writePrettyJson(reverse, generatedDir.resolve("reverse-index.json"));

        System.out.println("\nWrote " + services.size() + " services -> " + generatedDir);
    }

    // ----------------------------------------------------------------- graph

    private static ObjectNode buildGraph(List<JsonNode> services) {
        ObjectNode out = JSON.createObjectNode();
        for (JsonNode svc : services) {
            String name = name(svc);
            JsonNode spec = svc.path("spec");
            JsonNode cat = spec.path("x-catalog");
            ObjectNode node = out.putObject(name);
            node.put("system", spec.path("system").asText(""));
            node.put("domain", spec.path("domain").asText(""));
            node.put("owner", spec.path("owner").asText(""));
            node.set("providesApis", spec.path("providesApis").deepCopy());
            node.set("consumesApis", spec.path("consumesApis").deepCopy());
            node.set("dependsOn", spec.path("dependsOn").deepCopy());
            node.set("inboundApis", cat.path("inboundApis").deepCopy());
            node.set("outboundApis", cat.path("outboundApis").deepCopy());
            node.set("topicsProduced", topicNames(cat.path("topicsProduced")));
            node.set("topicsConsumed", topicNames(cat.path("topicsConsumed")));
            node.set("datastores", cat.path("datastores").deepCopy());
        }
        return out;
    }

    // --------------------------------------------------------- reverse index

    private static ObjectNode buildReverseIndex(List<JsonNode> services) {
        // api ref -> {provider, consumers[]}
        Map<String, String> apiProvider = new TreeMap<>();
        Map<String, Set<String>> apiConsumers = new TreeMap<>();
        // topic -> producers / consumers
        Map<String, Set<String>> topicProducers = new TreeMap<>();
        Map<String, Set<String>> topicConsumers = new TreeMap<>();
        Map<String, Set<String>> tables = new TreeMap<>();
        Map<String, Set<String>> caches = new TreeMap<>();
        Map<String, Set<String>> serviceDependents = new TreeMap<>();

        for (JsonNode svc : services) {
            String name = name(svc);
            serviceDependents.computeIfAbsent(name, k -> new TreeSet<>());
            JsonNode cat = svc.path("spec").path("x-catalog");

            for (JsonNode op : cat.path("inboundApis")) {
                apiProvider.putIfAbsent(apiRef(name, op.path("method").asText(), op.path("path").asText()), name);
            }
            for (JsonNode op : cat.path("outboundApis")) {
                String target = op.path("targetService").asText();
                String ref = apiRef(target, op.path("method").asText(), op.path("path").asText());
                apiProvider.putIfAbsent(ref, target);
                apiConsumers.computeIfAbsent(ref, k -> new TreeSet<>()).add(name);
                serviceDependents.computeIfAbsent(target, k -> new TreeSet<>()).add(name);
            }
            for (JsonNode t : cat.path("topicsProduced")) {
                topicProducers.computeIfAbsent(t.path("topic").asText(), k -> new TreeSet<>()).add(name);
            }
            for (JsonNode t : cat.path("topicsConsumed")) {
                topicConsumers.computeIfAbsent(t.path("topic").asText(), k -> new TreeSet<>()).add(name);
            }
            for (JsonNode ds : cat.path("datastores")) {
                String kind = ds.path("kind").asText();
                if ("db".equals(kind)) {
                    for (JsonNode tbl : ds.path("tables")) {
                        tables.computeIfAbsent(tbl.asText(), k -> new TreeSet<>()).add(name);
                    }
                } else if ("cache".equals(kind)) {
                    for (JsonNode region : ds.path("regions")) {
                        caches.computeIfAbsent(region.asText(), k -> new TreeSet<>()).add(name);
                    }
                }
            }
        }

        // A topic's consumers are dependents of each of its producers.
        for (var e : topicProducers.entrySet()) {
            Set<String> consumers = topicConsumers.getOrDefault(e.getKey(), Set.of());
            for (String producer : e.getValue()) {
                serviceDependents.computeIfAbsent(producer, k -> new TreeSet<>()).addAll(consumers);
            }
        }

        ObjectNode out = JSON.createObjectNode();

        ObjectNode apis = out.putObject("apis");
        for (var e : apiProvider.entrySet()) {
            ObjectNode entry = apis.putObject("api:" + e.getKey());
            entry.put("provider", e.getValue());
            entry.set("consumers", toArray(apiConsumers.getOrDefault(e.getKey(), Set.of())));
        }

        ObjectNode topics = out.putObject("topics");
        Set<String> allTopics = new TreeSet<>();
        allTopics.addAll(topicProducers.keySet());
        allTopics.addAll(topicConsumers.keySet());
        for (String t : allTopics) {
            ObjectNode entry = topics.putObject(t);
            entry.set("producers", toArray(topicProducers.getOrDefault(t, Set.of())));
            entry.set("consumers", toArray(topicConsumers.getOrDefault(t, Set.of())));
        }

        out.set("tables", mapToObject(tables));
        out.set("caches", mapToObject(caches));

        ObjectNode svcs = out.putObject("services");
        for (var e : serviceDependents.entrySet()) {
            svcs.putObject(e.getKey()).set("dependents", toArray(e.getValue()));
        }
        return out;
    }

    // ------------------------------------------------------------- summary

    private static void writeSummary(List<JsonNode> services, Path out) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode arr = root.putArray("services");
        for (JsonNode svc : services) {
            JsonNode spec = svc.path("spec");
            JsonNode cat = spec.path("x-catalog");
            ObjectNode s = arr.addObject();
            s.put("name", name(svc));
            s.put("summary", svc.path("metadata").path("description").asText(""));
            s.put("system", spec.path("system").asText(""));
            s.put("domain", spec.path("domain").asText(""));
            ArrayNode apis = s.putArray("apis");
            for (JsonNode op : cat.path("inboundApis")) {
                apis.add(op.path("method").asText() + " " + op.path("path").asText()
                        + " — " + op.path("summary").asText(""));
            }
            s.set("topicsProduced", topicNames(cat.path("topicsProduced")));
            s.set("topicsConsumed", topicNames(cat.path("topicsConsumed")));
            ArrayNode outbound = s.putArray("outboundTo");
            for (JsonNode op : cat.path("outboundApis")) {
                String t = op.path("targetService").asText();
                if (!contains(outbound, t)) {
                    outbound.add(t);
                }
            }
            ArrayNode stores = s.putArray("datastores");
            for (JsonNode ds : cat.path("datastores")) {
                stores.add(ds.path("kind").asText() + ":" + ds.path("name").asText());
            }
        }
        YAMLMapper writer = YAMLMapper.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .build();
        Files.writeString(out, writer.writeValueAsString(root));
    }

    // --------------------------------------------------------------- helpers

    private static JsonSchema loadSchema(Path schemaFile) throws IOException {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (var in = Files.newInputStream(schemaFile)) {
            return factory.getSchema(in);
        }
    }

    private static String name(JsonNode svc) {
        return svc.path("metadata").path("name").asText();
    }

    private static String apiRef(String service, String method, String path) {
        return service + "/" + method + " " + path;
    }

    private static ArrayNode topicNames(JsonNode topics) {
        ArrayNode arr = JSON.createArrayNode();
        for (JsonNode t : topics) {
            arr.add(t.path("topic").asText());
        }
        return arr;
    }

    private static ArrayNode toArray(Set<String> values) {
        ArrayNode arr = JSON.createArrayNode();
        values.forEach(arr::add);
        return arr;
    }

    private static ObjectNode mapToObject(Map<String, Set<String>> map) {
        ObjectNode obj = JSON.createObjectNode();
        map.forEach((k, v) -> obj.set(k, toArray(v)));
        return obj;
    }

    private static boolean contains(ArrayNode arr, String value) {
        for (JsonNode n : arr) {
            if (n.asText().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static void writePrettyJson(JsonNode node, Path out) throws IOException {
        JSON.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), node);
    }

    private GraphBuilder() {
    }
}
