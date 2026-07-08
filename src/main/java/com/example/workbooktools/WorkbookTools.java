package com.example.workbooktools;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class WorkbookTools {

    private static final Path OUTPUT_DIR = Path.of(System.getProperty("workbook.output.dir", "generated-workbooks"));
    private static final WorkbookGenerator GENERATOR = new WorkbookGenerator();

    private record WorkbookResult(String filePath, int rowCount) {
    }

    static SyncToolSpecification greetingTool() {

        String schema = """
            {
              "type": "object",
              "properties": {
                "name": { "type": "string" }
              },
              "required": ["name"]
            }
            """;

        return SyncToolSpecification.builder()
            .tool(Tool.builder("get_greeting", McpJsonDefaults.getMapper(), schema)
                .description("Returns a greeting for a given name")
                .build())
            .callHandler((exchange, request) -> {
                String name = (String) request.arguments().get("name");
                return CallToolResult.builder()
                    .addTextContent("Hello, " + name + "!")
                    .build();
            })
            .build();
    }

    static SyncToolSpecification generateWorkbookTool() {

        String schema = """
            {
              "type": "object",
              "properties": {
                "fileName": { "type": "string", "description": "Destination file name, e.g. \\"orders.xlsx\\"" },
                "rowCount": { "type": "integer", "minimum": 1 },
                "seed": { "type": "integer", "description": "Optional seed for reproducible output" },
                "columns": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "object",
                    "properties": {
                      "header": { "type": "string" },
                      "type": { "type": "string", "enum": ["text", "integer", "decimal", "date", "choice"] },
                      "words": { "type": "array", "items": { "type": "string" } },
                      "min": { "type": "number" },
                      "max": { "type": "number" },
                      "start": { "type": "string", "description": "ISO-8601 date, for type \\"date\\"" },
                      "end": { "type": "string", "description": "ISO-8601 date, for type \\"date\\"" },
                      "options": { "type": "array", "items": { "type": "string" } },
                      "blankProbability": { "type": "number", "minimum": 0, "maximum": 1 }
                    },
                    "required": ["header", "type"]
                  }
                }
              },
              "required": ["fileName", "rowCount", "columns"]
            }
            """;

        String outputSchema = """
            {
              "type": "object",
              "properties": {
                "filePath": { "type": "string" },
                "rowCount": { "type": "integer" }
              },
              "required": ["filePath", "rowCount"]
            }
            """;
        
        return SyncToolSpecification.builder()
            .tool(Tool.builder("generate_workbook", McpJsonDefaults.getMapper(), schema)
                .description("Generates a sample .xlsx workbook from column specifications and writes it to the output directory. "
                    + "See resource workbook-tools://schema/column-spec for the annotated column spec schema.")
                .outputSchema(McpJsonDefaults.getMapper(), outputSchema)
                .build())
            .callHandler((exchange, request) -> {
                Map<String, Object> args = request.arguments();
                Path destination;
                int rowCount;
                List<ColumnSpec> columns;
                try {
                    destination = OUTPUT_DIR.resolve(fileNameOf(args));
                    rowCount = ((Number) args.get("rowCount")).intValue();
                    columns = columnsOf(args);
                } catch (RuntimeException e) {
                    return CallToolResult.builder()
                        .addTextContent("Invalid argument: " + e.getMessage())
                        .isError(true)
                        .build();
                }

                try {
                    byte[] bytes = args.get("seed") instanceof Number seed
                        ? GENERATOR.generateSample(columns, rowCount, seed.longValue())
                        : GENERATOR.generateSample(columns, rowCount);


                    Files.createDirectories(OUTPUT_DIR);
                    GENERATOR.writeToFile(bytes, destination);
                } catch (IOException e) {
                    throw McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                        .message("Unexpected failure writing workbook: " + e.getMessage())
                        .build();
                }

                return CallToolResult.builder()
                    .addTextContent("Wrote " + destination)
                    .structuredContent(new WorkbookResult(destination.toString(), rowCount))
                    .build();
            })
            .build();
    }

    static SyncToolSpecification listWorkbooksTool() {
        return SyncToolSpecification.builder()
            .tool(Tool.builder("list_workbooks", McpJsonDefaults.getMapper(), "{\"type\":\"object\"}")
                .description("Lists the .xlsx workbooks currently in the output directory")
                .build())
            .callHandler((exchange, request) -> {
                if (!Files.isDirectory(OUTPUT_DIR)) {
                    return CallToolResult.builder().addTextContent("No workbooks generated yet.").build();
                }
                try (Stream<Path> files = Files.list(OUTPUT_DIR)) {
                    List<String> names = files
                        .filter(p -> p.toString().endsWith(".xlsx"))
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .toList();
                    return CallToolResult.builder()
                        .addTextContent(names.isEmpty() ? "No workbooks generated yet." : String.join("\n", names))
                        .build();
                } catch (IOException e) {
                    throw McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                        .message("Unexpected failure listing workbooks: " + e.getMessage())
                        .build();
                }
            })
            .build();
    }

    static SyncToolSpecification clearWorkbooksTool() {
        return SyncToolSpecification.builder()
            .tool(Tool.builder("clear_workbooks", McpJsonDefaults.getMapper(), "{\"type\":\"object\"}")
                .description("Deletes all workbooks from the output directory")
                .build())
            .callHandler((exchange, request) -> {
                if (!Files.isDirectory(OUTPUT_DIR)) {
                    return CallToolResult.builder().addTextContent("Nothing to clear.").build();
                }
                try (Stream<Path> files = Files.list(OUTPUT_DIR)) {
                    List<Path> toDelete = files.filter(p -> p.toString().endsWith(".xlsx")).toList();
                    for (Path path : toDelete) {
                        Files.delete(path);
                    }
                    return CallToolResult.builder()
                        .addTextContent("Deleted " + toDelete.size() + " workbook(s).")
                        .build();
                } catch (IOException e) {
                    throw McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                        .message("Unexpected failure clearing workbooks: " + e.getMessage())
                        .build();
                }
            })
            .build();
    }

    private static String fileNameOf(Map<String, Object> args) {
        String fileName = (String) args.get("fileName");
        return fileName.endsWith(".xlsx") ? fileName : fileName + ".xlsx";
    }

    @SuppressWarnings("unchecked")
    private static List<ColumnSpec> columnsOf(Map<String, Object> args) {
        List<Map<String, Object>> rawColumns = (List<Map<String, Object>>) args.get("columns");
        return rawColumns.stream().map(WorkbookTools::parseColumn).toList();
    }

    private static ColumnSpec parseColumn(Map<String, Object> col) {
        String header = (String) col.get("header");
        String type = (String) col.get("type");
        ColumnSpec spec = switch (type) {
            case "text" -> col.containsKey("words")
                ? ColumnSpec.text(header, stringListOf(col.get("words")))
                : ColumnSpec.text(header);
            case "integer" -> ColumnSpec.integer(header,
                ((Number) col.get("min")).longValue(), ((Number) col.get("max")).longValue());
            case "decimal" -> ColumnSpec.decimal(header,
                ((Number) col.get("min")).doubleValue(), ((Number) col.get("max")).doubleValue());
            case "date" -> ColumnSpec.date(header,
                LocalDate.parse((String) col.get("start")), LocalDate.parse((String) col.get("end")));
            case "choice" -> ColumnSpec.choice(header, stringListOf(col.get("options")));
            default -> throw new IllegalArgumentException("Unknown column type: " + type);
        };
        return col.get("blankProbability") instanceof Number blankProbability
            ? spec.withBlankProbability(blankProbability.doubleValue())
            : spec;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringListOf(Object value) {
        return ((List<Object>) value).stream().map(String::valueOf).toList();
    }
}
