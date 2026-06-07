package com.ganesh.catalog.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP tools the Windsurf SDD agent calls during feature-to-story generation.
 * Each method is auto-registered as an MCP tool by its {@code name}.
 */
@Component
public class CatalogTools {

    private final CatalogStore store;

    public CatalogTools(CatalogStore store) {
        this.store = store;
    }

    @Tool(name = "list_services",
            description = "List every service in the catalog (compact index: name, summary, API operations, "
                    + "topics, datastores). Call this first to find candidate entry-point services for a feature.")
    public JsonNode listServices() {
        return store.summary();
    }

    @Tool(name = "search_services",
            description = "Search services by keyword across name, description, API paths, and topic names.")
    public JsonNode searchServices(
            @ToolParam(description = "keyword, e.g. 'inventory', 'reserve', or 'order.created'") String query) {
        return store.searchServices(query);
    }

    @Tool(name = "get_service",
            description = "Full descriptor for one service: inbound + outbound APIs, topics produced/consumed, "
                    + "datastores, and which services depend on it.")
    public JsonNode getService(
            @ToolParam(description = "service name, e.g. 'order-service'") String name) {
        return store.getService(name);
    }

    @Tool(name = "get_api",
            description = "Full request/response detail for one operation — method, path, request schema + example, "
                    + "and every response schema + example — resolved from the service's OpenAPI spec.")
    public JsonNode getApi(
            @ToolParam(description = "service name, e.g. 'inventory-service'") String service,
            @ToolParam(description = "operationId, e.g. 'reserveInventory'") String operationId) {
        return store.getApi(service, operationId);
    }

    @Tool(name = "get_dependents",
            description = "Impact analysis — who is affected if a thing changes. ref forms: "
                    + "'api:<service>/<METHOD> <path>', 'topic:<name>', 'table:<name>', 'cache:<name>', "
                    + "or 'service:<name>'. Always call this before claiming a change is isolated.")
    public JsonNode getDependents(
            @ToolParam(description = "e.g. 'api:inventory-service/POST /api/inventory/reserve' or 'topic:order.created'")
            String ref) {
        return store.getDependents(ref);
    }

    @Tool(name = "get_dependencies",
            description = "Everything a service depends on: outbound API calls, topics produced/consumed, and datastores.")
    public JsonNode getDependencies(
            @ToolParam(description = "service name, e.g. 'order-service'") String service) {
        return store.getDependencies(service);
    }

    @Tool(name = "impact_report",
            description = "One-shot cross-service impact for a feature. Pass the service(s) the feature directly "
                    + "changes; returns the transitive set of affected services (via the reverse dependency graph) "
                    + "with each one's topics and datastores. Use the result to fill the spec's Cross-Service Impact section.")
    public JsonNode impactReport(
            @ToolParam(description = "service names the feature changes, e.g. [\"order-service\"]") List<String> seeds) {
        return store.impact(seeds);
    }
}
