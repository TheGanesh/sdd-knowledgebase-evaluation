package com.ganesh.catalog.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns the structured catalog into retrievable chunks (one per service, per inbound API
 * operation, and per Kafka topic). Each chunk is natural-language-rich so semantic search works,
 * and carries a stable {@code id} matching the graph/reverse-index keys for 1-hop expansion.
 */
class CatalogChunker {

    private final YAMLMapper yaml = new YAMLMapper();

    List<Document> chunk(Path catalogRoot) throws IOException {
        Path servicesDir = catalogRoot.resolve("services");
        List<JsonNode> services;
        try (Stream<Path> dirs = Files.list(servicesDir)) {
            services = dirs.filter(Files::isDirectory)
                    .map(d -> d.resolve("catalog-info.yaml"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .map(this::read)
                    .collect(Collectors.toList());
        }

        // Topic producers/consumers across all services (for topic chunks).
        Map<String, Set<String>> producers = new TreeMap<>();
        Map<String, Set<String>> consumers = new TreeMap<>();
        for (JsonNode svc : services) {
            String name = name(svc);
            for (JsonNode t : cat(svc).path("topicsProduced")) {
                producers.computeIfAbsent(t.path("topic").asText(), k -> new TreeSet<>()).add(name);
            }
            for (JsonNode t : cat(svc).path("topicsConsumed")) {
                consumers.computeIfAbsent(t.path("topic").asText(), k -> new TreeSet<>()).add(name);
            }
        }

        List<Document> docs = new ArrayList<>();
        Set<String> topicsSeen = new TreeSet<>();
        for (JsonNode svc : services) {
            String name = name(svc);
            JsonNode cat = cat(svc);
            docs.add(doc("service:" + name, "service", name, serviceText(svc, catalogRoot)));
            for (JsonNode op : cat.path("inboundApis")) {
                String ref = "api:" + name + "/" + op.path("method").asText() + " " + op.path("path").asText();
                docs.add(doc(ref, "api", name, apiText(name, op)));
            }
            for (String field : List.of("topicsProduced", "topicsConsumed")) {
                for (JsonNode t : cat.path(field)) {
                    String topic = t.path("topic").asText();
                    if (topicsSeen.add(topic)) {
                        docs.add(doc("topic:" + topic, "topic", "",
                                topicText(topic, t.path("messageSchema").asText(),
                                        producers.getOrDefault(topic, Set.of()),
                                        consumers.getOrDefault(topic, Set.of()))));
                    }
                }
            }
        }
        return docs;
    }

    // ---- chunk text builders ----

    private String serviceText(JsonNode svc, Path catalogRoot) {
        JsonNode cat = cat(svc);
        StringBuilder b = new StringBuilder();
        String name = name(svc);
        b.append("Service: ").append(name).append(". ")
                .append(svc.path("metadata").path("description").asText("")).append(" ")
                .append("System ").append(svc.path("spec").path("system").asText(""))
                .append(", domain ").append(svc.path("spec").path("domain").asText(""))
                .append(", owner ").append(svc.path("spec").path("owner").asText("")).append(". ");
        b.append("Inbound APIs: ").append(join(stream(cat.path("inboundApis"))
                .map(o -> o.path("method").asText() + " " + o.path("path").asText()
                        + " (" + o.path("summary").asText("") + ")").toList())).append(". ");
        List<String> outs = stream(cat.path("outboundApis"))
                .map(o -> o.path("targetService").asText() + " " + o.path("method").asText() + " " + o.path("path").asText())
                .toList();
        b.append("Outbound calls: ").append(outs.isEmpty() ? "none" : join(outs)).append(". ");
        b.append("Produces events: ").append(orNone(stream(cat.path("topicsProduced")).map(t -> t.path("topic").asText()).toList())).append(". ");
        b.append("Consumes events: ").append(orNone(stream(cat.path("topicsConsumed")).map(t -> t.path("topic").asText()).toList())).append(". ");
        List<String> stores = stream(cat.path("datastores"))
                .map(d -> d.path("kind").asText() + " " + d.path("name").asText()).toList();
        b.append("Datastores: ").append(orNone(stores)).append(". ");
        // Append the human narrative if present (richer semantics).
        Path narrative = catalogRoot.resolve("services").resolve(name).resolve("service.md");
        if (Files.isRegularFile(narrative)) {
            try {
                b.append("Notes: ").append(Files.readString(narrative).replaceAll("\\s+", " ").trim());
            } catch (IOException ignored) {
                // narrative is optional
            }
        }
        return b.toString();
    }

    private String apiText(String service, JsonNode op) {
        return "API operation: " + service + " " + op.path("method").asText() + " " + op.path("path").asText()
                + " — " + op.path("summary").asText("") + ". "
                + "Request schema " + op.path("requestSchema").asText("none")
                + ", response schema " + op.path("responseSchema").asText("none") + ". "
                + "Errors: " + orNone(stream(op.path("errors")).map(JsonNode::asText).toList()) + ". "
                + "Provided by service " + service + ".";
    }

    private String topicText(String topic, String schema, Set<String> producers, Set<String> consumers) {
        return "Kafka topic: " + topic + ". Message schema " + schema + ". "
                + "Produced by " + orNone(new ArrayList<>(producers)) + ". "
                + "Consumed by " + orNone(new ArrayList<>(consumers)) + ".";
    }

    // ---- helpers ----

    private Document doc(String id, String type, String service, String text) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", id);
        meta.put("type", type);
        meta.put("service", service);
        return Document.builder().text(text).metadata(meta).build();
    }

    private JsonNode read(Path p) {
        try {
            return yaml.readTree(p.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String name(JsonNode svc) {
        return svc.path("metadata").path("name").asText();
    }

    private JsonNode cat(JsonNode svc) {
        return svc.path("spec").path("x-catalog");
    }

    private static Stream<JsonNode> stream(JsonNode arr) {
        List<JsonNode> list = new ArrayList<>();
        arr.forEach(list::add);
        return list.stream();
    }

    private static String join(List<String> items) {
        return String.join(", ", items);
    }

    private static String orNone(List<String> items) {
        return items.isEmpty() ? "none" : join(items);
    }
}
