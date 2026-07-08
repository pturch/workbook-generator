package com.example.workbooktools;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;

public class WorkbookMcpServer {
    public static void main(String[] args) {
        StdioServerTransportProvider transportProvider =
            new StdioServerTransportProvider(McpJsonDefaults.getMapper());

            var server = McpServer.sync(transportProvider)
            .capabilities(ServerCapabilities.builder().tools(true).resources(false, false).build())
            .serverInfo("workbook-tools", "0.0.1")
            .build();

            server.addTool(WorkbookTools.greetingTool());
            server.addTool(WorkbookTools.generateWorkbookTool());
            server.addTool(WorkbookTools.listWorkbooksTool());
            server.addTool(WorkbookTools.clearWorkbooksTool());

            server.addResource(WorkbookResources.dummyResource());
            server.addResource(WorkbookResources.columnSpecSchemaResource());
            server.addResourceTemplate(WorkbookResources.workbookFileInfoResource());
            server.addResource(WorkbookResources.columnTypesGuideResource());
    }
}
