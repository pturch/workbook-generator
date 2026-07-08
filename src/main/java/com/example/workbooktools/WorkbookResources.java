package com.example.workbooktools;

import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ResourceTemplate;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class WorkbookResources {

    private static final Path OUTPUT_DIR = Path.of(System.getProperty("workbook.output.dir", "generated-workbooks"));

    static SyncResourceSpecification dummyResource() {
        Resource resource = Resource.builder("workbook-tools://dummy", "Dummy resource")
            .title("Dummy resource")
            .description("Placeholder resource for testing resource wiring")
            .mimeType("text/plain")
            .build();

        return new SyncResourceSpecification(resource, (exchange, request) ->
            ReadResourceResult.builder(List.of(
                    TextResourceContents.builder(request.uri(), "This is a dummy resource.").build()))
                .build());
    }

    private static final String COLUMN_SPEC_SCHEMA = """
        {
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
        """;


    static SyncResourceSpecification columnSpecSchemaResource() {
        Resource resource = Resource.builder("workbook-tools://schema/column-spec", "Column spec schema")
            .title("Column spec schema")
            .description("JSON schema for a single column specification accepted by generate_workbook")
            .mimeType("application/schema+json")
            .build();


        return new SyncResourceSpecification(resource, (exchange, request) ->
            ReadResourceResult.builder(List.of(
                    TextResourceContents.builder(request.uri(), COLUMN_SPEC_SCHEMA).build()))
                .build());
    }

    static SyncResourceTemplateSpecification workbookFileInfoResource() {
        ResourceTemplate template = ResourceTemplate.builder("workbook-tools://workbooks/{fileName}", "Workbook contents")
            .title("Workbook contents")
            .description("Full contents of a generated workbook as CSV, for verifying against the rendered viewer")
            .mimeType("text/csv")
            .build();


        return new SyncResourceTemplateSpecification(template, (exchange, request) -> {
            String fileName = request.uri().substring(request.uri().lastIndexOf('/') + 1);
            Path path = OUTPUT_DIR.resolve(fileName);


            String csv;
            try (InputStream in = Files.newInputStream(path); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
                Sheet sheet = workbook.getSheetAt(0);
                StringBuilder out = new StringBuilder();
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        cells.add(csvEscape(cell.getStringCellValue()));
                    }
                    out.append(String.join(",", cells)).append("\n");
                }
                csv = out.toString();
            } catch (NoSuchFileException e) {
                throw McpError.RESOURCE_NOT_FOUND.apply(request.uri());
            } catch (IOException e) {
                throw McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                    .message("Unexpected failure reading workbook: " + e.getMessage())
                    .build();
            }


            return ReadResourceResult.builder(List.of(
                    TextResourceContents.builder(request.uri(), csv).build()))
                .build();
        });
    }

    private static String csvEscape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static final String COLUMN_TYPES_GUIDE = readClasspathResource("/docs/column-types.md");


    private static String readClasspathResource(String path) {
        try (InputStream in = WorkbookResources.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read classpath resource: " + path, e);
        }
    }


    static SyncResourceSpecification columnTypesGuideResource() {
        Resource resource = Resource.builder("workbook-tools://docs/column-types", "Column types guide")
            .title("Column types guide")
            .description("Explains how each column type (text, integer, decimal, date, choice) behaves and how blankProbability works")
            .mimeType("text/markdown")
            .build();


        return new SyncResourceSpecification(resource, (exchange, request) ->
            ReadResourceResult.builder(List.of(
                    TextResourceContents.builder(request.uri(), COLUMN_TYPES_GUIDE).build()))
                .build());
    }




}
