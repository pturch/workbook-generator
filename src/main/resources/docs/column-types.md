# Column types


Each entry in `columns` has a `header` and a `type`, plus type-specific fields.
Any column may also set `blankProbability` (0.0-1.0) to leave that percentage of
its cells blank per row, regardless of type.


## text
Placeholder values built from two random words joined by a space, e.g. "alpha bravo".
Draws from a built-in word list by default, or from a custom `words` array if provided.


## integer
Whole numbers chosen uniformly from the inclusive range `[min, max]`.


## decimal
Decimal values formatted to two places, chosen uniformly from `[min, max]`.


## date
ISO-8601 dates (`YYYY-MM-DD`) chosen uniformly from the inclusive range `[start, end]`.


## choice
Values chosen uniformly from a fixed `options` array, e.g. ["Open", "Closed", "Pending"].


See resource workbook-tools://schema/column-spec for the exact field-level JSON schema.
