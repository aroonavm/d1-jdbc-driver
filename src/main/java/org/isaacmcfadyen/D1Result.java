package org.isaacmcfadyen;

import java.util.List;

/** One statement's outcome from the D1 REST API: column names, row values, and the write metadata. */
final class D1Result {
    final List<String> columns;
    final List<Object[]> rows;
    final long changes;
    final long lastRowId;

    D1Result(List<String> columns, List<Object[]> rows, long changes, long lastRowId) {
        this.columns = columns;
        this.rows = rows;
        this.changes = changes;
        this.lastRowId = lastRowId;
    }

    boolean hasResultSet() {
        return !columns.isEmpty();
    }
}
