package com.example.workbooktools;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbookGeneratorTest {

    private final WorkbookGenerator generator = new WorkbookGenerator();

    @Test
    void generateWritesHeadersAndRows() throws Exception {
        List<String> headers = List.of("Name", "Quantity");
        List<List<String>> rows = List.of(
                List.of("Widget", "5"),
                List.of("Gadget", "3")
        );

        byte[] bytes = generator.generate(headers, rows);

        assertEquals(List.of(
                List.of("Name", "Quantity"),
                List.of("Widget", "5"),
                List.of("Gadget", "3")
        ), readSheet(bytes));
    }

    @Test
    void generateSampleHonorsColumnTypesAndRanges() throws Exception {
        List<ColumnSpec> columns = List.of(
                ColumnSpec.text("Description"),
                ColumnSpec.integer("Quantity", 10, 20),
                ColumnSpec.decimal("Price", 1.0, 2.0),
                ColumnSpec.date("OrderDate", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3))
        );

        List<List<String>> sheet = readSheet(generator.generateSample(columns, 20, 7L));

        assertEquals(List.of("Description", "Quantity", "Price", "OrderDate"), sheet.get(0));
        assertEquals(21, sheet.size());
        for (List<String> row : sheet.subList(1, sheet.size())) {
            long quantity = Long.parseLong(row.get(1));
            double price = Double.parseDouble(row.get(2));
            LocalDate orderDate = LocalDate.parse(row.get(3));
            assertTrue(quantity >= 10 && quantity <= 20);
            assertTrue(price >= 1.0 && price <= 2.0);
            assertTrue(!orderDate.isBefore(LocalDate.of(2024, 1, 1)) && !orderDate.isAfter(LocalDate.of(2024, 1, 3)));
        }
    }

    @Test
    void generateSampleIsReproducibleWithSameSeed() throws Exception {
        List<ColumnSpec> columns = List.of(ColumnSpec.integer("Id", 1000, 9999), ColumnSpec.text("Notes"));

        assertEquals(readSheet(generator.generateSample(columns, 5, 99L)),
                     readSheet(generator.generateSample(columns, 5, 99L)));
    }

    @Test
    void textColumnDrawsFromCustomWordList() throws Exception {
        List<String> words = List.of("red", "green", "blue");
        List<List<String>> sheet = readSheet(
                generator.generateSample(List.of(ColumnSpec.text("Color", words)), 15, 5L));

        for (List<String> row : sheet.subList(1, sheet.size())) {
            for (String word : row.get(0).split(" ")) {
                assertTrue(words.contains(word.toLowerCase()));
            }
        }
    }

    @Test
    void textColumnRejectsEmptyWordList() {
        assertThrows(IllegalArgumentException.class, () -> ColumnSpec.text("Color", List.of()));
    }

    @Test
    void choiceColumnOnlyProducesGivenOptions() throws Exception {
        List<String> statuses = List.of("Pending", "Shipped", "Delivered");
        List<List<String>> sheet = readSheet(
                generator.generateSample(List.of(ColumnSpec.choice("Status", statuses)), 20, 11L));

        for (List<String> row : sheet.subList(1, sheet.size())) {
            assertTrue(statuses.contains(row.get(0)));
        }
    }

    @Test
    void choiceColumnRejectsEmptyOptions() {
        assertThrows(IllegalArgumentException.class, () -> ColumnSpec.choice("Status", List.of()));
    }

    @Test
    void withBlankProbabilityLeavesCellsBlankAtGivenRate() throws Exception {
        List<List<String>> alwaysBlank = readSheet(generator.generateSample(
                List.of(ColumnSpec.integer("Quantity", 1, 10).withBlankProbability(1.0)), 10, 3L));
        List<List<String>> neverBlank = readSheet(generator.generateSample(
                List.of(ColumnSpec.integer("Quantity", 1, 10).withBlankProbability(0.0)), 10, 3L));

        assertEquals("Quantity", alwaysBlank.get(0).get(0));
        for (List<String> row : alwaysBlank.subList(1, alwaysBlank.size())) {
            assertEquals("", row.get(0));
        }
        for (List<String> row : neverBlank.subList(1, neverBlank.size())) {
            long quantity = Long.parseLong(row.get(0));
            assertTrue(quantity >= 1 && quantity <= 10);
        }
    }

    @Test
    void withBlankProbabilityRejectsProbabilityOutsideUnitRange() {
        ColumnSpec inner = ColumnSpec.text("Notes");
        assertThrows(IllegalArgumentException.class, () -> inner.withBlankProbability(-0.1));
        assertThrows(IllegalArgumentException.class, () -> inner.withBlankProbability(1.1));
    }

    @Test
    void columnSpecRejectsInvertedRanges() {
        assertThrows(IllegalArgumentException.class, () -> ColumnSpec.integer("Quantity", 20, 10));
        assertThrows(IllegalArgumentException.class, () -> ColumnSpec.decimal("Price", 2.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> ColumnSpec.date("OrderDate", LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 1)));
    }

    @Test
    void writeToFileSavesBytes() throws Exception {
        byte[] bytes = generator.generate(List.of("A"), List.of(List.of("1")));
        Path destination = Files.createTempFile("workbook-generator-test", ".xlsx");

        Path written = generator.writeToFile(bytes, destination);

        assertArrayEquals(bytes, Files.readAllBytes(written));
        Files.deleteIfExists(written);
    }

    private List<List<String>> readSheet(byte[] xlsxBytes) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<List<String>> values = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (Cell cell : row) {
                    cells.add(cell.getStringCellValue());
                }
                values.add(cells);
            }
            return values;
        }
    }
}
