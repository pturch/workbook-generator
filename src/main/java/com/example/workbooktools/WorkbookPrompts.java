package com.example.workbooktools;

import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptArgument;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.util.List;
import java.util.Map;

public class WorkbookPrompts {

    static SyncPromptSpecification designWorkbookPrompt() {
        Prompt prompt = Prompt.builder("design_workbook")
            .title("Design a workbook")
            .description("Makes a workbook")
            .arguments(List.of(
                PromptArgument.builder("description").build(),
                PromptArgument.builder("rowCount").build()))
            .build();


        return new SyncPromptSpecification(prompt, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            String description = String.valueOf(args.get("description"));
            String rowCount = String.valueOf(args.get("rowCount"));


            String text = """
                Design a sample .xlsx workbook for: %s
                Target row count: %s 


                Steps:
                1. Confirm you have both a workbook description and a target row count. If either says \
                "(not provided)" above, ask the user for it and wait for their answer before continuing.
                2. Read the resources workbook-tools://schema/column-spec (column spec schema) and \
                workbook-tools://docs/column-types (how each column type behaves) before choosing columns.
                3. Choose a file name and a set of columns (header + type + type-specific fields) that \
                reflect the description above.
                4. Call generate_workbook with those columns and the confirmed row count.
                5. Call list_workbooks to confirm the file was written, and optionally read \
                workbook-tools://workbooks/{fileName} to spot-check the generated data.


                """.formatted(description, rowCount);



            return GetPromptResult.builder(List.of(
                    PromptMessage.builder(Role.USER, TextContent.builder(text).build()).build()))
                .build();
        });
    }

    static SyncPromptSpecification reviewWorkbookPrompt() {
        Prompt prompt = Prompt.builder("review_workbook")
            .title("Review a generated workbook")
            .description("Reviews a previously generated workbook's data for quality and consistency with its column specs")
            .arguments(List.of(
                PromptArgument.builder("fileName")
                    .description("Name of the generated workbook file, e.g. \"orders.xlsx\"")
                    .required(true)
                    .build()))
            .build();


        return new SyncPromptSpecification(prompt, (exchange, request) -> {
            String fileName = String.valueOf(request.arguments().get("fileName"));


            String text = """
                Review the generated workbook "%s" for data quality.


                Steps:
                1. Confirm the file name above is a real value (not blank or "null") and ends in .xlsx. \
                If it isn't, ask the user for the file name (adding .xlsx if they omit it) and wait for \
                their answer before continuing.
                2. Call list_workbooks to confirm the file exists. If it doesn't appear in that list, \
                tell the user it wasn't found, show them the list of available workbooks, and ask which \
                one they meant instead of guessing.
                3. Read the resource workbook-tools://workbooks/%s to get its contents as CSV.
                4. Check the data for internal consistency: values within a column share the same type, \
                numbers and dates fall within a sane range for that column, choice-like columns only use \
                a small consistent set of values, and blanks are not so frequent that a column is unusable.
                5. Report a verdict of PASS or FAIL, followed by a bullet list of any anomalies found \
                (or "none" if the data looks correct).


                Use only the tools and resources provided by this MCP server (e.g. list_workbooks, \
                the workbook-tools://workbooks/%s resource) to perform this review. Do not inspect the \
                .xlsx file directly via the filesystem, shell commands, or other means.
                """.formatted(fileName, fileName, fileName);



            return GetPromptResult.builder(List.of(
                    PromptMessage.builder(Role.USER, TextContent.builder(text).build()).build()))
                .description("Review workbook: " + fileName)
                .build();
        });
    }


}
