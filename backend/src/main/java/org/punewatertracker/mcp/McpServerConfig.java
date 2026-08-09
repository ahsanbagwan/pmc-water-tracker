package org.punewatertracker.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.punewatertracker.model.Locality;
import org.punewatertracker.model.WaterStatus;
import org.punewatertracker.service.LocalityService;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns this app's public locality data into MCP tools -- so any MCP-compatible AI assistant
 * (Claude Desktop, etc.) can query it directly, without the user needing to know this website
 * exists. Deliberately read-only: mirrors only what's already public via GET /api/localities,
 * never the admin/write operations.
 *
 * mcpTransportProvider()'s .mcpEndpoint(...) and the SyncToolSpecification.builder() pattern
 * below were both confirmed against actual decompiled SDK source (IntelliJ "Go to
 * Declaration"), not guessed -- the remaining unverified piece is whether the other 3
 * HttpServletStreamableServerTransportProvider builder params (beyond jsonMapper/mcpEndpoint)
 * have sensible defaults when left unset. If mvn compile succeeds but the server doesn't
 * actually respond correctly at runtime, that's the next thing to check.
 */
@Configuration
public class McpServerConfig {

    private final LocalityService localityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpServerConfig(LocalityService localityService) {
        this.localityService = localityService;
    }

    /** Tool.builder().inputSchema(...) requires an actual McpSchema.JsonSchema object, not a
     *  raw JSON string -- this parses the schema text (kept as inline JSON for readability
     *  above each tool definition) into that type. */
    private JsonSchema parseSchema(String json) {
        try {
            return objectMapper.readValue(json, JsonSchema.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid tool input schema JSON: " + ex.getMessage(), ex);
        }
    }

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransportProvider() {
        // jsonMapper() and mcpEndpoint() confirmed via decompiled SDK source, not guessed.
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transportProvider) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(transportProvider, "/mcp");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    public McpSyncServer mcpSyncServer(HttpServletStreamableServerTransportProvider transportProvider) {
        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("pmc-water-tracker", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .build();

        server.addTool(getLocalityStatusTool());
        server.addTool(listByStatusTool());

        return server;
    }

    private McpServerFeatures.SyncToolSpecification getLocalityStatusTool() {
        Tool tool = Tool.builder()
                .name("get_locality_status")
                .description("Look up the current water supply status of a PMC (Pune) locality by name, "
                        + "e.g. 'Wagholi' or 'Kothrud'. Returns status, notes, and source citation.")
                .inputSchema(parseSchema("""
                        {
                          "type": "object",
                          "properties": {
                            "name": { "type": "string", "description": "Locality name to search for" }
                          },
                          "required": ["name"]
                        }
                        """))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String name = String.valueOf(request.arguments().get("name"));
                    List<Locality> matches = localityService.findVisible(null, name);

                    if (matches.isEmpty()) {
                        return CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(
                                        "No locality found matching \"" + name + "\".")))
                                .build();
                    }

                    String summary = matches.stream()
                            .map(loc -> loc.getName() + ": " + loc.getStatus()
                                    + (loc.getNotes() != null ? " -- " + loc.getNotes() : "")
                                    + (loc.getSourceUrl() != null ? " (source: " + loc.getSourceUrl() + ")" : ""))
                            .collect(Collectors.joining("\n"));

                    return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent(summary)))
                            .build();
                })
                .build();
    }

    private McpServerFeatures.SyncToolSpecification listByStatusTool() {
        Tool tool = Tool.builder()
                .name("list_localities_by_status")
                .description("List PMC localities matching a water status. Valid status values: "
                        + "MUNICIPAL (reliable piped supply), TANKER_DEPENDENT (no piped connection), "
                        + "MIXED (partial piped coverage), PIPELINE_IN_PROGRESS (pipeline work underway).")
                .inputSchema(parseSchema("""
                        {
                          "type": "object",
                          "properties": {
                            "status": {
                              "type": "string",
                              "enum": ["MUNICIPAL", "TANKER_DEPENDENT", "MIXED", "PIPELINE_IN_PROGRESS"]
                            }
                          },
                          "required": ["status"]
                        }
                        """))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String statusText = String.valueOf(request.arguments().get("status"));
                    WaterStatus status;
                    try {
                        status = WaterStatus.valueOf(statusText);
                    } catch (IllegalArgumentException ex) {
                        return CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(
                                        "Unknown status \"" + statusText + "\". Valid values: MUNICIPAL, "
                                                + "TANKER_DEPENDENT, MIXED, PIPELINE_IN_PROGRESS.")))
                                .build();
                    }

                    List<Locality> matches = localityService.findVisible(status, null);
                    String summary = matches.isEmpty()
                            ? "No localities currently have status " + status + "."
                            : matches.stream().map(Locality::getName).collect(Collectors.joining(", "));

                    return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent(summary)))
                            .build();
                })
                .build();
    }
}