package com.ganesh.catalog.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Renders a human-friendly **markdown view** of the catalog from the same structured
 * descriptors the graph is built from. Output goes to {@code <catalogRoot>/views/markdown/}.
 *
 * <p>This is the "markdown KB as well" — but generated, so it can never drift from the
 * contracts. The structured catalog stays the source of truth; this is a rendering of it.
 *
 * <p>Usage: {@code MarkdownGenerator <catalogRoot>}
 */
public final class MarkdownGenerator {

    private static final YAMLMapper YAML = new YAMLMapper();

    public static void main(String[] args) throws Exception {
        Path catalogRoot = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path servicesDir = catalogRoot.resolve("services");
        // Optional 2nd arg overrides the output dir (e.g. the markdown KB folder).
        Path outDir = args.length > 1
                ? Path.of(args[1]).toAbsolutePath().normalize()
                : catalogRoot.resolve("views/markdown");
        Files.createDirectories(outDir);

        List<JsonNode> services = new ArrayList<>();
        List<Path> serviceDirs;
        try (Stream<Path> s = Files.list(servicesDir)) {
            serviceDirs = s.filter(Files::isDirectory).sorted().collect(Collectors.toList());
        }
        for (Path dir : serviceDirs) {
            Path descriptor = dir.resolve("catalog-info.yaml");
            if (!Files.isRegularFile(descriptor)) {
                continue;
            }
            JsonNode node = YAML.readTree(descriptor.toFile());
            services.add(node);
            Files.writeString(outDir.resolve(name(node) + ".md"), servicePage(node, dir));
        }
        Files.writeString(outDir.resolve("index.md"), indexPage(services));
        System.out.println("Wrote markdown view (" + services.size() + " services + index) -> " + outDir);
    }

    private static String indexPage(List<JsonNode> services) {
        StringBuilder b = new StringBuilder();
        b.append("# Service Catalog — markdown view\n\n");
        b.append("> Generated from the structured catalog (`catalog-info.yaml` per service). ")
                .append("Do not edit by hand — re-run `/build-knowledge-base`.\n\n");
        b.append("| Service | System / Domain | Inbound APIs | Produces | Consumes | Calls |\n");
        b.append("|---|---|---|---|---|---|\n");
        for (JsonNode s : services) {
            JsonNode cat = s.path("spec").path("x-catalog");
            String apis = join(stream(cat.path("inboundApis"))
                    .map(o -> o.path("method").asText() + " " + o.path("path").asText()).toList());
            String produces = join(stream(cat.path("topicsProduced")).map(t -> t.path("topic").asText()).toList());
            String consumes = join(stream(cat.path("topicsConsumed")).map(t -> t.path("topic").asText()).toList());
            String calls = join(stream(cat.path("outboundApis")).map(o -> o.path("targetService").asText())
                    .distinct().toList());
            b.append("| [").append(name(s)).append("](").append(name(s)).append(".md) | ")
                    .append(s.path("spec").path("system").asText("")).append(" / ")
                    .append(s.path("spec").path("domain").asText("")).append(" | ")
                    .append(apis).append(" | ").append(dash(produces)).append(" | ")
                    .append(dash(consumes)).append(" | ").append(dash(calls)).append(" |\n");
        }
        b.append("\n*For machine consumption (impact analysis), query the Service Catalog MCP server ")
                .append("instead of parsing this file — see `../../README.md`.*\n");
        return b.toString();
    }

    private static String servicePage(JsonNode s, Path dir) throws IOException {
        JsonNode spec = s.path("spec");
        JsonNode cat = spec.path("x-catalog");
        StringBuilder b = new StringBuilder();
        b.append("# ").append(name(s)).append("\n\n");
        b.append("> ").append(s.path("metadata").path("description").asText("")).append("\n\n");
        b.append("- **System:** ").append(spec.path("system").asText("")).append(" · ")
                .append("**Domain:** ").append(spec.path("domain").asText("")).append(" · ")
                .append("**Owner:** ").append(spec.path("owner").asText("")).append("\n\n");

        b.append("## Inbound APIs\n\n");
        b.append("| Method | Path | Summary | Request | Response | Errors |\n");
        b.append("|---|---|---|---|---|---|\n");
        for (JsonNode o : cat.path("inboundApis")) {
            b.append("| ").append(o.path("method").asText()).append(" | `").append(o.path("path").asText())
                    .append("` | ").append(o.path("summary").asText("")).append(" | ")
                    .append(dash(o.path("requestSchema").asText(""))).append(" | ")
                    .append(dash(o.path("responseSchema").asText(""))).append(" | ")
                    .append(dash(join(stream(o.path("errors")).map(JsonNode::asText).toList()))).append(" |\n");
        }

        if (cat.path("outboundApis").size() > 0) {
            b.append("\n## Outbound calls\n\n");
            b.append("| Target service | Method | Path | Request | Response | Call site |\n");
            b.append("|---|---|---|---|---|---|\n");
            for (JsonNode o : cat.path("outboundApis")) {
                b.append("| ").append(o.path("targetService").asText()).append(" | ")
                        .append(o.path("method").asText()).append(" | `").append(o.path("path").asText())
                        .append("` | ").append(dash(o.path("requestSchema").asText(""))).append(" | ")
                        .append(dash(o.path("responseSchema").asText(""))).append(" | `")
                        .append(o.path("callSite").asText("")).append("` |\n");
            }
        }

        b.append("\n## Kafka\n\n");
        b.append("- **Produces:** ").append(dash(topics(cat.path("topicsProduced")))).append("\n");
        b.append("- **Consumes:** ").append(dash(topics(cat.path("topicsConsumed")))).append("\n");

        b.append("\n## Data\n\n");
        for (JsonNode ds : cat.path("datastores")) {
            String detail = ds.has("tables")
                    ? "tables: " + join(stream(ds.path("tables")).map(JsonNode::asText).toList())
                    : "regions: " + join(stream(ds.path("regions")).map(JsonNode::asText).toList());
            b.append("- **").append(ds.path("kind").asText().toUpperCase()).append(":** ")
                    .append(ds.path("name").asText()).append(" (").append(ds.path("engine").asText())
                    .append(") — ").append(detail).append("\n");
        }

        Path narrative = dir.resolve("service.md");
        if (Files.isRegularFile(narrative)) {
            b.append("\n---\n\n").append(Files.readString(narrative));
        }
        return b.toString();
    }

    // -------- helpers --------

    private static String name(JsonNode s) {
        return s.path("metadata").path("name").asText();
    }

    private static String topics(JsonNode arr) {
        return join(stream(arr).map(t -> t.path("topic").asText() + " (" + t.path("messageSchema").asText() + ")").toList());
    }

    private static Stream<JsonNode> stream(JsonNode arr) {
        List<JsonNode> list = new ArrayList<>();
        arr.forEach(list::add);
        return list.stream();
    }

    private static String join(List<String> items) {
        return String.join(", ", items);
    }

    private static String dash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private MarkdownGenerator() {
    }
}
