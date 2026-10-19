package org.isaacmcfadyen;

import org.isaacmcfadyen.base.UnsupportedStatement;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class D1Statement extends UnsupportedStatement {
    final D1Connection connection;
    private final List<String> batch = new ArrayList<>();
    private boolean closed;
    private int maxRows;
    private int queryTimeout;
    private int fetchSize;
    private boolean closeOnCompletion;
    private volatile D1Client.Call call = new D1Client.Call();

    private List<D1Result> results = Collections.emptyList();
    private List<String> resultSql = Collections.emptyList();
    private int resultIndex = -1;
    private D1ResultSet current;
    private long updateCount = -1;
    private D1Result lastWrite;

    protected D1Statement(D1Connection connection) {
        this.connection = connection;
    }

    protected void open() throws SQLException {
        if (closed || connection.isClosed()) throw new SQLException("Statement is closed", "HY010");
    }

    /** Sends one SQL string (it may hold several statements) and positions on its first result. */
    protected boolean run(String sql, List<Object> params) throws SQLException {
        open();
        if (sql == null || sql.isBlank()) throw new SQLException("Empty SQL statement", "42000");
        closeCurrent();
        results = Collections.emptyList(); // a failed request must not leave the previous results reachable
        resultSql = Collections.emptyList();
        resultIndex = -1;
        if (connection.isReadOnly() && !SqlScanner.onlyReads(sql)) {
            throw new SQLException("The connection is read-only: only SELECT, VALUES, EXPLAIN and reading PRAGMAs are allowed", "25006");
        }
        D1Client.Call fresh = new D1Client.Call();
        call = fresh;
        // setMaxRows must not mean "transfer the whole table and drop rows locally"
        String limited = SqlScanner.withLimit(sql, maxRows);
        results = connection.client.execute(limited != null ? limited : sql, params, queryTimeout, fresh);
        List<String> statements = SqlScanner.splitStatements(sql);
        for (String statement : statements) {
            String keyword = SqlScanner.firstKeyword(statement);
            if (keyword.equals("create") || keyword.equals("alter") || keyword.equals("drop")) connection.schemaChanged();
        }
        // Each result is judged by its own statement. If the split does not line up (a trigger body holds ';'), nothing is guessed.
        resultSql = statements.size() == results.size() ? statements : Collections.nCopies(results.size(), (String) null);
        resultIndex = -1;
        return advance();
    }

    /** Number of statements the last request answered (more than one when the SQL held several). */
    int resultCount() {
        return results.size();
    }

    private void closeCurrent() {
        if (current != null) current.discard();
        current = null;
        updateCount = -1;
        lastWrite = null;
    }

    private boolean advance() {
        closeCurrent();
        resultIndex++;
        if (resultIndex >= results.size()) return false;
        D1Result r = results.get(resultIndex);
        String sql = resultSql.get(resultIndex); // null when the statement behind this result is unknown
        boolean isQuery = r.hasResultSet() || (sql != null && r.rows.isEmpty() && SqlScanner.looksLikeQuery(sql));
        if (isQuery) {
            List<Object[]> rows = maxRows > 0 && r.rows.size() > maxRows ? r.rows.subList(0, maxRows) : r.rows;
            current = sql == null ? new D1ResultSet(this, r.columns, rows, null) : withSourceTable(r, rows, sql);
            current.setFetchSize(fetchSize);
            return true;
        }
        updateCount = r.changes;
        String keyword = sql == null ? "" : SqlScanner.firstKeyword(sql);
        lastWrite = keyword.equals("insert") || keyword.equals("replace") ? r : null; // last_row_id is meaningless after UPDATE/DELETE
        return false;
    }

    /**
     * For a plain single-table SELECT, tells the client which table each column comes from (DBeaver and DataGrip need it to find the
     * row key for editing) and uses the declared column types; computed columns and everything else stay unattributed.
     */
    private D1ResultSet withSourceTable(D1Result r, List<Object[]> rows, String sql) {
        String table = SqlScanner.singleTable(sql);
        if (table != null && table.toLowerCase(java.util.Locale.ROOT).startsWith("sqlite_")) table = null; // internal: no lookup request
        java.util.Map<String, Object[]> info = table == null ? null : connection.tableInfo(table);
        if (info != null && SqlScanner.selectsAllColumns(sql) && !info.keySet().equals(lowerCased(r.columns))) {
            info = connection.tableInfo(table, true); // SELECT * disagrees with the cached columns: the table changed
        }
        if (info == null) return new D1ResultSet(this, r.columns, rows, null);
        int n = r.columns.size();
        String[] types = new String[n];
        String[] tables = new String[n];
        int[] nullable = new int[n];
        D1ResultSet inferred = new D1ResultSet(this, r.columns, rows, null);
        // An alias can carry another column's name ("SELECT name AS score"); attributing it would let a grid edit the wrong column.
        java.util.Set<String> aliases = SqlScanner.selectAliases(sql);
        for (int c = 0; c < n; c++) {
            String label = r.columns.get(c).toLowerCase(java.util.Locale.ROOT);
            Object[] col = aliases.contains(label) ? null : info.get(label);
            try {
                types[c] = inferred.getMetaData().getColumnTypeName(c + 1);
            } catch (SQLException e) {
                types[c] = "TEXT";
            }
            tables[c] = col == null ? "" : table;
            nullable[c] = col == null ? java.sql.ResultSetMetaData.columnNullableUnknown
                    : notNull(col) ? java.sql.ResultSetMetaData.columnNoNulls : java.sql.ResultSetMetaData.columnNullable;
            if (col != null && col[2] != null && !col[2].toString().isBlank()) types[c] = col[2].toString().replaceAll("\\(.*\\)", "").trim();
        }
        return new D1ResultSet(this, r.columns, rows, types, tables, nullable);
    }

    private static java.util.Set<String> lowerCased(List<String> names) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String n : names) out.add(n.toLowerCase(java.util.Locale.ROOT));
        return out;
    }

    /** NOT NULL or part of the primary key (see D1DatabaseMetaData.getColumns); col is a PRAGMA table_info row. */
    private static boolean notNull(Object[] col) {
        return col[3] instanceof Number n && n.longValue() != 0 || col[5] instanceof Number pk && pk.longValue() > 0;
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        return run(sql, Collections.emptyList());
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        if (!execute(sql)) throw new SQLException("The statement did not return a result set", "02000");
        return current;
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        execute(sql);
        return (int) Math.max(updateCount, 0);
    }

    @Override public boolean execute(String sql, int autoGeneratedKeys) throws SQLException { return execute(sql); }
    @Override public boolean execute(String sql, int[] columnIndexes) throws SQLException { return execute(sql); }
    @Override public boolean execute(String sql, String[] columnNames) throws SQLException { return execute(sql); }
    @Override public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException { return executeUpdate(sql); }
    @Override public int executeUpdate(String sql, int[] columnIndexes) throws SQLException { return executeUpdate(sql); }
    @Override public int executeUpdate(String sql, String[] columnNames) throws SQLException { return executeUpdate(sql); }

    @Override public ResultSet getResultSet() throws SQLException { open(); return current; }
    @Override public int getUpdateCount() throws SQLException { open(); return (int) updateCount; }
    @Override public boolean getMoreResults() throws SQLException { open(); return advance(); }
    @Override public boolean getMoreResults(int mode) throws SQLException { return getMoreResults(); }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        open();
        List<Object[]> rows = new ArrayList<>();
        if (lastWrite != null && lastWrite.changes > 0) rows.add(new Object[]{lastWrite.lastRowId});
        return new D1ResultSet(this, List.of("last_insert_rowid()"), rows, new String[]{"INTEGER"});
    }

    // ---- batch: statements run one after another; D1's REST API has no cross-request transaction

    @Override
    public void addBatch(String sql) throws SQLException {
        open();
        batch.add(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        open();
        batch.clear();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        open();
        int[] counts = new int[batch.size()];
        try {
            for (int i = 0; i < counts.length; i++) {
                try {
                    counts[i] = executeUpdate(batch.get(i));
                } catch (SQLException e) {
                    throw new BatchUpdateException(e.getMessage(), e.getSQLState(), e.getErrorCode(), Arrays.copyOf(counts, i), e);
                }
            }
        } finally {
            batch.clear();
        }
        return counts;
    }

    // ---- settings

    @Override public void close() { closed = true; closeCurrent(); }
    @Override public boolean isClosed() { return closed || connection.isClosedQuietly(); }
    @Override public void cancel() { call.cancel(); }
    @Override public Connection getConnection() { return connection; }
    @Override public int getMaxRows() { return maxRows; }
    @Override public void setMaxRows(int max) throws SQLException { if (max < 0) throw new SQLException("maxRows must be >= 0", "S1009"); maxRows = max; }
    @Override public int getQueryTimeout() { return queryTimeout; }
    @Override public void setQueryTimeout(int seconds) throws SQLException { if (seconds < 0) throw new SQLException("timeout must be >= 0", "S1009"); queryTimeout = seconds; }
    @Override public int getFetchSize() { return fetchSize; }
    @Override public void setFetchSize(int rows) { fetchSize = rows; }
    @Override public int getFetchDirection() { return ResultSet.FETCH_FORWARD; }
    @Override public void setFetchDirection(int direction) {}
    @Override public int getMaxFieldSize() { return 0; }
    @Override public void setMaxFieldSize(int max) {}
    @Override public void setEscapeProcessing(boolean enable) {}
    @Override public void setCursorName(String name) {}
    @Override public SQLWarning getWarnings() { return null; }
    @Override public void clearWarnings() {}
    @Override public int getResultSetConcurrency() { return ResultSet.CONCUR_READ_ONLY; }
    @Override public int getResultSetType() { return ResultSet.TYPE_SCROLL_INSENSITIVE; }
    @Override public int getResultSetHoldability() { return ResultSet.CLOSE_CURSORS_AT_COMMIT; }
    @Override public boolean isPoolable() { return false; }
    @Override public void setPoolable(boolean poolable) {}
    @Override public void closeOnCompletion() { closeOnCompletion = true; }
    @Override public boolean isCloseOnCompletion() { return closeOnCompletion; }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) return iface.cast(this);
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }
}
