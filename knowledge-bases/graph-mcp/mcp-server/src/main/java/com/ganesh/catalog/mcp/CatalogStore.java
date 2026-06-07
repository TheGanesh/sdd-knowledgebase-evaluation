package com.ganesh.catalog.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads the pre-built catalog ({@code generated/*}) and the per-service OpenAPI specs,
 * and answers the queries the MCP tools expose. Read-only; never touches service code.
 */
@Component
public class CatalogStore {

    private final ObjectMapper json = new ObjectMapper();
    private final YAMLMapper yaml = new YAMLMapper();
    private final Path catalogRoot;

    private JsonNode graph;
    private JsonNode reverse;
    private JsonNode summary;
    private final Map<String, JsonNode> openapiByService = new HashMap<>();

    public CatalogStore(@Value("${catalog.root}") String root) {
        this.catalogRoot = Path.of(root).toAbsolutePath().normalize();
    }

    @PostConstruct
    void load() throws IOException {
        Path generated = catalogRoot.resolve("generated");
        this.graph = json.readTree(generated.resolve("graph.json").toFile());
        this.reverse = json.readTree(generated.resolve("reverse-index.json").toFile());
        this.summary = yaml.readTree(generated.resolve("catalog-summary.yaml").toFile());

        Path servicesDir = catalogRoot.resolve("services");
        try (Stream<Path> dirs = Files.list(servicesDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                Path openapi = dir.resolve("openapi.yaml");
                if (Files.isRegularFile(openapi)) {
                    try {
                        openapiByService.put(dir.getFileName().toString(), yaml.readTree(openapi.toFile()));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }
    }

    // ---------------------------------------------------------------- queries

    /** Compact index of every service — small enough to keep in the agent's context. */
    public JsonNode summary() {
        return summary;
    }

    public JsonNode searchServices(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        ArrayNode results = json.createArrayNode();
        for (JsonNode svc : summary.path("services")) {
            if (svc.toString().toLowerCase(Locale.ROOT).contains(q)) {
                results.add(svc);
            }
        }
        return results;
    }

    public JsonNode getService(String name) {
        JsonNode node = graph.path(name);
        if (node.isMissingNode()) {
            return notFound("service", name);
        }
        ObjectNode out = node.deepCopy();
        out.put("name", name);
        out.set("dependents", reverse.path("services").path(name).path("dependents").deepCopy());
        return out;
    }

    /** Full request/response detail for one operation, resolved from the service's OpenAPI spec. */
    public JsonNode getApi(String service, String operationId) {
        JsonNode openapi = openapiByService.get(service);
        if (openapi == null) {
            return notFound("openapi for service", service);
        }
        JsonNode paths = openapi.path("paths");
        var fields = paths.fields();
        while (fields.hasNext()) {
            var pathEntry = fields.next();
            var methods = pathEntry.getValue().fields();
            while (methods.hasNext()) {
                var methodEntry = methods.next();
                JsonNode op = methodEntry.getValue();
                if (operationId.equals(op.path("operationId").asText())) {
                    ObjectNode out = json.createObjectNode();
                    out.put("service", service);
                    out.put("operationId", operationId);
                    out.put("method", methodEntry.getKey().toUpperCase(Locale.ROOT));
                    out.put("path", pathEntry.getKey());
                    out.put("summary", op.path("summary").asText(""));
                    out.set("request", describeBody(openapi, op.path("requestBody")
                            .path("content").path("application/json")));
                    out.set("responses", describeResponses(openapi, op.path("responses")));
                    return out;
                }
            }
        }
        return notFound("operation", service + "#" + operationId);
    }

    /**
     * Reverse-graph lookup: who is affected if {@code ref} changes.
     * ref forms: {@code api:<svc>/<METHOD> <path>}, {@code topic:<name>},
     * {@code table:<name>}, {@code cache:<name>}, {@code service:<name>} (or a bare service name).
     */
    public JsonNode getDependents(String ref) {
        ObjectNode out = json.createObjectNode();
        out.put("ref", ref);
        if (ref.startsWith("api:")) {
            JsonNode entry = reverse.path("apis").path(ref);
            out.put("kind", "api");
            out.set("provider", entry.path("provider").deepCopy());
            out.set("consumers", entry.path("consumers").deepCopy());
        } else if (ref.startsWith("topic:")) {
            String name = ref.substring("topic:".length());
            JsonNode entry = reverse.path("topics").path(name);
            out.put("kind", "topic");
            out.set("producers", entry.path("producers").deepCopy());
            out.set("consumers", entry.path("consumers").deepCopy());
        } else if (ref.startsWith("table:")) {
            out.put("kind", "table");
            out.set("usedBy", reverse.path("tables").path(ref.substring("table:".length())).deepCopy());
        } else if (ref.startsWith("cache:")) {
            out.put("kind", "cache");
            out.set("usedBy", reverse.path("caches").path(ref.substring("cache:".length())).deepCopy());
        } else {
            String name = ref.startsWith("service:") ? ref.substring("service:".length()) : ref;
            out.put("kind", "service");
            out.set("dependents", reverse.path("services").path(name).path("dependents").deepCopy());
        }
        return out;
    }

    /** Everything {@code service} depends on: outbound APIs, topics, datastores. */
    public JsonNode getDependencies(String service) {
        JsonNode node = graph.path(service);
        if (node.isMissingNode()) {
            return notFound("service", service);
        }
        ObjectNode out = json.createObjectNode();
        out.put("service", service);
        out.set("outboundApis", node.path("outboundApis").deepCopy());
        out.set("consumesApis", node.path("consumesApis").deepCopy());
        out.set("topicsProduced", node.path("topicsProduced").deepCopy());
        out.set("topicsConsumed", node.path("topicsConsumed").deepCopy());
        out.set("datastores", node.path("datastores").deepCopy());
        out.set("dependsOn", node.path("dependsOn").deepCopy());
        return out;
    }

    /**
     * One-shot impact analysis. Given the service(s) a feature directly changes, returns the
     * transitive set of affected services (via the reverse dependency graph) plus each one's
     * data-plane footprint (topics + datastores) — the raw material for the spec's
     * Cross-Service Impact section.
     */
    public JsonNode impact(List<String> seeds) {
        Set<String> affected = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            String svc = queue.poll();
            if (!affected.add(svc)) {
                continue;
            }
            for (JsonNode dep : reverse.path("services").path(svc).path("dependents")) {
                queue.add(dep.asText());
            }
        }

        ObjectNode out = json.createObjectNode();
        out.set("seeds", json.valueToTree(seeds));
        ArrayNode services = out.putArray("affectedServices");
        for (String svc : affected) {
            JsonNode node = graph.path(svc);
            ObjectNode s = services.addObject();
            s.put("name", svc);
            s.put("role", seeds.contains(svc) ? "changed" : "downstream");
            s.set("inboundApis", node.path("inboundApis").deepCopy());
            s.set("outboundApis", node.path("outboundApis").deepCopy());
            s.set("topicsProduced", node.path("topicsProduced").deepCopy());
            s.set("topicsConsumed", node.path("topicsConsumed").deepCopy());
            s.set("datastores", node.path("datastores").deepCopy());
        }
        out.put("affectedCount", affected.size());
        return out;
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode describeBody(JsonNode openapi, JsonNode content) {
        if (content.isMissingNode()) {
            return json.nullNode();
        }
        ObjectNode out = json.createObjectNode();
        JsonNode schemaRef = content.path("schema");
        out.put("schema", refName(schemaRef));
        out.set("resolvedSchema", resolve(openapi, schemaRef));
        if (content.has("example")) {
            out.set("example", content.path("example").deepCopy());
        } else if (content.has("examples")) {
            out.set("examples", content.path("examples").deepCopy());
        }
        return out;
    }

    private JsonNode describeResponses(JsonNode openapi, JsonNode responses) {
        ArrayNode arr = json.createArrayNode();
        var it = responses.fields();
        while (it.hasNext()) {
            var e = it.next();
            JsonNode content = e.getValue().path("content").path("application/json");
            ObjectNode r = arr.addObject();
            r.put("status", e.getKey());
            r.put("description", e.getValue().path("description").asText(""));
            if (!content.isMissingNode()) {
                JsonNode schemaRef = content.path("schema");
                r.put("schema", refName(schemaRef));
                r.set("resolvedSchema", resolve(openapi, schemaRef));
                if (content.has("example")) {
                    r.set("example", content.path("example").deepCopy());
                } else if (content.has("examples")) {
                    r.set("examples", content.path("examples").deepCopy());
                }
            }
        }
        return arr;
    }

    private JsonNode resolve(JsonNode openapi, JsonNode schemaRef) {
        String ref = schemaRef.path("$ref").asText("");
        if (ref.startsWith("#/")) {
            JsonNode node = openapi;
            for (String token : ref.substring(2).split("/")) {
                node = node.path(token);
            }
            return node.deepCopy();
        }
        return schemaRef.deepCopy();
    }

    private String refName(JsonNode schemaRef) {
        String ref = schemaRef.path("$ref").asText("");
        return ref.isEmpty() ? null : ref.substring(ref.lastIndexOf('/') + 1);
    }

    private JsonNode notFound(String kind, String id) {
        ObjectNode out = json.createObjectNode();
        out.put("error", "not_found");
        out.put("message", "No " + kind + " '" + id + "' in the catalog. Try list/search first.");
        out.put("known", knownServices());
        return out;
    }

    private String knownServices() {
        return Stream.of(graph).flatMap(g -> {
            List<String> names = new java.util.ArrayList<>();
            g.fieldNames().forEachRemaining(names::add);
            return names.stream();
        }).collect(Collectors.joining(", "));
    }
}
