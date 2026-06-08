package com.example.workbooktools;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates .xlsx workbooks in the format the workbook-viewer-webapp expects on
 * upload: a header row followed by rows of string values, on the first sheet.
 *
 * Each public method takes and returns plain types (lists of strings, byte arrays,
 * paths) with no shared state, so they can be wrapped directly as MCP tool functions.
 */
public class WorkbookGenerator {

    /** Builds an .xlsx workbook from explicit header and row data, returned as bytes. */
    public byte[] generate(List<String> headers, List<List<String>> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            writeRow(sheet, 0, headers);
            for (int i = 0; i < rows.size(); i++) {
                writeRow(sheet, i + 1, rows.get(i));
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Builds an .xlsx workbook with randomized sample data shaped by the given column specifications. */
    public byte[] generateSample(List<ColumnSpec> columns, int rowCount) throws IOException {
        return generateSample(columns, rowCount, new Random().nextLong());
    }

    /** Same as {@link #generateSample(List, int)} but with a fixed seed for reproducible output. */
    public byte[] generateSample(List<ColumnSpec> columns, int rowCount, long seed) throws IOException {
        Random random = new Random(seed);
        List<String> headers = columns.stream().map(ColumnSpec::header).toList();
        List<List<String>> rows = new ArrayList<>(rowCount);
        for (int r = 0; r < rowCount; r++) {
            List<String> row = new ArrayList<>(columns.size());
            for (ColumnSpec column : columns) {
                row.add(randomValueFor(column, random));
            }
            rows.add(row);
        }
        return generate(headers, rows);
    }

    /** Writes workbook bytes to disk and returns the destination path. */
    public Path writeToFile(byte[] workbookBytes, Path destination) throws IOException {
        Files.write(destination, workbookBytes);
        return destination;
    }

    private void writeRow(Sheet sheet, int rowIndex, List<String> values) {
        Row row = sheet.createRow(rowIndex);
        for (int c = 0; c < values.size(); c++) {
            row.createCell(c).setCellValue(values.get(c));
        }
    }

    private String randomValueFor(ColumnSpec column, Random random) {
        return switch (column) {
            case ColumnSpec.TextColumn c ->
                    sampleWord(c.words(), random) + " " + sampleWord(c.words(), random);
            case ColumnSpec.IntegerColumn c -> String.valueOf(random.nextLong(c.min(), c.max() + 1));
            case ColumnSpec.DecimalColumn c -> String.format("%.2f", random.nextDouble(c.min(), c.max()));
            case ColumnSpec.DateColumn c -> randomDateBetween(c.start(), c.end(), random).toString();
            case ColumnSpec.ChoiceColumn c -> c.options().get(random.nextInt(c.options().size()));
            case ColumnSpec.NullableColumn c ->
                    random.nextDouble() < c.blankProbability() ? "" : randomValueFor(c.column(), random);
        };
    }

    private LocalDate randomDateBetween(LocalDate start, LocalDate end, Random random) {
        long daysBetween = ChronoUnit.DAYS.between(start, end);
        return start.plusDays(random.nextLong(0, daysBetween + 1));
    }

    private String sampleWord(List<String> words, Random random) {
        return words.get(random.nextInt(words.size()));
    }

}
