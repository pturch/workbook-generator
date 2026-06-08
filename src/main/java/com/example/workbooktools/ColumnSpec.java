package com.example.workbooktools;

import java.time.LocalDate;
import java.util.List;

/**
 * Per-column data shape for {@link WorkbookGenerator#generateSample}.
 * Each implementation is a record with plain-type fields so they map cleanly
 * to JSON for MCP tool use.
 */
public sealed interface ColumnSpec
        permits ColumnSpec.TextColumn, ColumnSpec.IntegerColumn, ColumnSpec.DecimalColumn,
        ColumnSpec.DateColumn, ColumnSpec.ChoiceColumn, ColumnSpec.NullableColumn {

    List<String> DEFAULT_WORDS = List.of(
            "alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel",
            "india", "juliet", "kilo", "lima", "mike", "november", "oscar", "papa"
    );

    String header();

    /** Returns a copy of this column that leaves cells blank with the given probability per row. */
    default ColumnSpec withBlankProbability(double blankProbability) {
        return new NullableColumn(this, blankProbability);
    }

    /** Column of placeholder text values drawn from the built-in word list. */
    static ColumnSpec text(String header) {
        return new TextColumn(header, DEFAULT_WORDS);
    }

    /** Column of placeholder text values drawn from {@code words} — each value pairs two random words. */
    static ColumnSpec text(String header, List<String> words) {
        return new TextColumn(header, words);
    }

    /** Column of whole numbers chosen uniformly from {@code [min, max]} (inclusive). */
    static ColumnSpec integer(String header, long min, long max) {
        return new IntegerColumn(header, min, max);
    }

    /** Column of decimal values formatted to two places, chosen uniformly from {@code [min, max]}. */
    static ColumnSpec decimal(String header, double min, double max) {
        return new DecimalColumn(header, min, max);
    }

    /** Column of ISO-8601 dates chosen uniformly from {@code [start, end]} (inclusive). */
    static ColumnSpec date(String header, LocalDate start, LocalDate end) {
        return new DateColumn(header, start, end);
    }

    /** Column whose values are chosen uniformly from a fixed set of options. */
    static ColumnSpec choice(String header, List<String> options) {
        return new ChoiceColumn(header, options);
    }

    record TextColumn(String header, List<String> words) implements ColumnSpec {
        public TextColumn {
            if (words.isEmpty()) throw new IllegalArgumentException("words must not be empty");
            words = List.copyOf(words);
        }
    }

    record IntegerColumn(String header, long min, long max) implements ColumnSpec {
        public IntegerColumn {
            if (min > max) throw new IllegalArgumentException("min must be <= max");
        }
    }

    record DecimalColumn(String header, double min, double max) implements ColumnSpec {
        public DecimalColumn {
            if (min > max) throw new IllegalArgumentException("min must be <= max");
        }
    }

    record DateColumn(String header, LocalDate start, LocalDate end) implements ColumnSpec {
        public DateColumn {
            if (start.isAfter(end)) throw new IllegalArgumentException("start must be on or before end");
        }
    }

    record ChoiceColumn(String header, List<String> options) implements ColumnSpec {
        public ChoiceColumn {
            if (options.isEmpty()) throw new IllegalArgumentException("options must not be empty");
            options = List.copyOf(options);
        }
    }

    record NullableColumn(ColumnSpec column, double blankProbability) implements ColumnSpec {
        public NullableColumn {
            if (blankProbability < 0.0 || blankProbability > 1.0)
                throw new IllegalArgumentException("blankProbability must be between 0.0 and 1.0");
        }

        @Override
        public String header() {
            return column.header();
        }
    }
}
