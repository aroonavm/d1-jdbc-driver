package org.isaacmcfadyen;

import org.isaacmcfadyen.base.UnsupportedResultSet;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** A fully materialised result: D1's REST API returns every row in one response. */
final class D1ResultSet extends UnsupportedResultSet {
    private final Statement statement;
    private final String[] labels;
    private final List<Object[]> rows;
    private final D1ResultSetMetaData metaData;
    private int pos = 0; // 0 = before first, rows.size() + 1 = after last
    private boolean closed;
    private boolean wasNull;
    private int fetchSize;

    /** @param typeNames SQLite type names per column, or null to infer them from the first non-null value. */
    D1ResultSet(Statement statement, List<String> columns, List<Object[]> rows, String[] typeNames) {
        this(statement, columns, rows, typeNames, null, null);
    }

    D1ResultSet(Statement statement, List<String> columns, List<Object[]> rows, String[] typeNames, String[] tables, int[] nullable) {
        this.statement = statement;
        this.labels = columns.toArray(new String[0]);
        this.rows = rows;
        if (typeNames == null) {
            typeNames = new String[labels.length];
            for (int c = 0; c < typeNames.length; c++) {
                typeNames[c] = "TEXT";
                for (Object[] row : rows) {
                    if (c < row.length && row[c] != null) {
                        typeNames[c] = D1Types.nameOf(row[c]);
                        break;
                    }
                }
            }
        }
        this.metaData = new D1ResultSetMetaData(labels, typeNames, tables, nullable);
    }

    static D1ResultSet of(List<String> columns, List<Object[]> rows) {
        return new D1ResultSet(null, columns, rows, null);
    }

    // ---- cursor

    private void open() throws SQLException {
        if (closed) throw new SQLException("ResultSet is closed", "HY010");
    }

    private Object value(int column) throws SQLException {
        open();
        if (pos < 1 || pos > rows.size()) throw new SQLException("No current row", "HY109");
        if (column < 1 || column > labels.length) throw new SQLException("Column index out of range: " + column, "S1002");
        Object[] row = rows.get(pos - 1);
        Object v = column <= row.length ? row[column - 1] : null;
        wasNull = v == null;
        // D1 sends 2.0 as 2; a column declared REAL/FLOAT/DOUBLE should still read as a floating-point number.
        if (v instanceof Long l && metaData.isFloatingPoint(column - 1)) return l.doubleValue();
        return v;
    }

    @Override public boolean next() throws SQLException { open(); if (pos <= rows.size()) pos++; return pos <= rows.size(); }
    @Override public boolean previous() throws SQLException { open(); if (pos > 0) pos--; return pos >= 1; }
    @Override public void beforeFirst() throws SQLException { open(); pos = 0; }
    @Override public void afterLast() throws SQLException { open(); pos = rows.size() + 1; }
    @Override public boolean first() throws SQLException { open(); pos = rows.isEmpty() ? 0 : 1; return !rows.isEmpty(); }
    @Override public boolean last() throws SQLException { open(); pos = rows.isEmpty() ? 0 : rows.size(); return !rows.isEmpty(); }
    @Override public boolean isBeforeFirst() throws SQLException { open(); return pos == 0 && !rows.isEmpty(); }
    @Override public boolean isAfterLast() throws SQLException { open(); return pos > rows.size() && !rows.isEmpty(); }
    @Override public boolean isFirst() throws SQLException { open(); return pos == 1; }
    @Override public boolean isLast() throws SQLException { open(); return !rows.isEmpty() && pos == rows.size(); }
    @Override public int getRow() throws SQLException { open(); return pos >= 1 && pos <= rows.size() ? pos : 0; }

    @Override
    public boolean absolute(int row) throws SQLException {
        open();
        pos = row > 0 ? Math.min(row, rows.size() + 1) : row < 0 ? Math.max(rows.size() + row + 1, 0) : 0;
        return pos >= 1 && pos <= rows.size();
    }

    @Override
    public boolean relative(int delta) throws SQLException {
        open();
        pos = Math.max(0, Math.min(pos + delta, rows.size() + 1));
        return pos >= 1 && pos <= rows.size();
    }

    /** Closed by its statement moving on (re-execution, getMoreResults): that must not count as completion. */
    void discard() {
        closed = true;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            if (statement != null && statement.isCloseOnCompletion()) statement.close();
        } catch (SQLException ignored) {
            // closing is best effort
        }
    }
    @Override public boolean isClosed() { return closed; }
    @Override public boolean wasNull() throws SQLException { open(); return wasNull; }
    @Override public Statement getStatement() { return statement; }
    @Override public ResultSetMetaData getMetaData() throws SQLException { open(); return metaData; }
    @Override public SQLWarning getWarnings() { return null; }
    @Override public void clearWarnings() {}
    @Override public int getType() { return ResultSet.TYPE_SCROLL_INSENSITIVE; }
    @Override public int getConcurrency() { return ResultSet.CONCUR_READ_ONLY; }
    @Override public int getHoldability() { return ResultSet.CLOSE_CURSORS_AT_COMMIT; }
    @Override public int getFetchDirection() { return ResultSet.FETCH_FORWARD; }
    @Override public void setFetchDirection(int direction) {}
    @Override public int getFetchSize() { return fetchSize; }
    @Override public void setFetchSize(int rows) { fetchSize = rows; }
    @Override public boolean rowUpdated() { return false; }
    @Override public boolean rowInserted() { return false; }
    @Override public boolean rowDeleted() { return false; }

    @Override
    public int findColumn(String label) throws SQLException {
        open();
        for (int i = 0; i < labels.length; i++) if (labels[i].equalsIgnoreCase(label)) return i + 1;
        throw new SQLException("No such column: " + label, "S0022");
    }

    // ---- getters

    @Override public Object getObject(int c) throws SQLException { return value(c); }
    @Override public Object getObject(String l) throws SQLException { return getObject(findColumn(l)); }

    @Override
    public <T> T getObject(int c, Class<T> type) throws SQLException {
        Object v = value(c);
        if (v == null) return null;
        if (type == Object.class || type.isInstance(v)) return type.cast(v);
        if (type == String.class) return type.cast(getString(c));
        if (type == Integer.class) return type.cast(getInt(c));
        if (type == Long.class) return type.cast(getLong(c));
        if (type == Short.class) return type.cast(getShort(c));
        if (type == Byte.class) return type.cast(getByte(c));
        if (type == Double.class) return type.cast(getDouble(c));
        if (type == Float.class) return type.cast(getFloat(c));
        if (type == Boolean.class) return type.cast(getBoolean(c));
        if (type == BigDecimal.class) return type.cast(getBigDecimal(c));
        if (type == BigInteger.class) return type.cast(getBigDecimal(c).toBigInteger());
        if (type == Date.class) return type.cast(getDate(c));
        if (type == Time.class) return type.cast(getTime(c));
        if (type == Timestamp.class) return type.cast(getTimestamp(c));
        if (type == byte[].class) return type.cast(getBytes(c));
        if (type == LocalDate.class) return type.cast(getDate(c).toLocalDate());
        if (type == LocalTime.class) return type.cast(getTime(c).toLocalTime());
        if (type == LocalDateTime.class) return type.cast(getTimestamp(c).toLocalDateTime());
        throw new SQLException("Cannot convert column " + c + " to " + type.getName(), "22018");
    }

    @Override public <T> T getObject(String l, Class<T> type) throws SQLException { return getObject(findColumn(l), type); }

    @Override
    public String getString(int c) throws SQLException {
        Object v = value(c);
        if (v == null) return null;
        if (v instanceof byte[] b) return new String(b, StandardCharsets.UTF_8);
        if (v instanceof Double d) return java.math.BigDecimal.valueOf(d).toPlainString(); // 12345678.0, not 1.2345678E7
        return v.toString();
    }

    @Override public String getNString(int c) throws SQLException { return getString(c); }
    @Override public String getString(String l) throws SQLException { return getString(findColumn(l)); }
    @Override public String getNString(String l) throws SQLException { return getString(findColumn(l)); }

    @Override
    public boolean getBoolean(int c) throws SQLException {
        Object v = value(c);
        if (v == null) return false;
        if (v instanceof Number n) return n.doubleValue() != 0;
        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("t") || s.equals("yes") || s.equals("y")) return true;
        if (s.equals("false") || s.equals("f") || s.equals("no") || s.equals("n") || s.isEmpty()) return false;
        try {
            return Double.parseDouble(s) != 0;
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot convert '" + v + "' to boolean", "22018");
        }
    }

    private Number number(int c) throws SQLException {
        Object v = value(c);
        if (v == null) return null;
        if (v instanceof Number n) return n;
        try {
            String s = v.toString().trim();
            return s.matches("[+-]?\\d+") ? (Number) Long.valueOf(s) : (Number) Double.valueOf(s);
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot convert '" + v + "' to a number", "22018");
        }
    }

    /** The value as a whole number within [min, max]; fractions are truncated as JDBC allows, overflow is an error, not a wrapped value. */
    private long whole(int c, long min, long max) throws SQLException {
        Number n = number(c);
        if (n == null) return 0;
        double d = n.doubleValue();
        long v = n instanceof Long ? n.longValue() : (long) d;
        if (n instanceof Long ? v < min || v > max : d < min || d >= max + 1.0 || Double.isNaN(d)) {
            throw new SQLException("Value " + n + " in column " + c + " is out of range for the requested type", "22003");
        }
        return v;
    }

    @Override public byte getByte(int c) throws SQLException { return (byte) whole(c, Byte.MIN_VALUE, Byte.MAX_VALUE); }
    @Override public short getShort(int c) throws SQLException { return (short) whole(c, Short.MIN_VALUE, Short.MAX_VALUE); }
    @Override public int getInt(int c) throws SQLException { return (int) whole(c, Integer.MIN_VALUE, Integer.MAX_VALUE); }
    @Override public long getLong(int c) throws SQLException { return whole(c, Long.MIN_VALUE, Long.MAX_VALUE); }
    @Override public float getFloat(int c) throws SQLException { Number n = number(c); return n == null ? 0 : n.floatValue(); }
    @Override public double getDouble(int c) throws SQLException { Number n = number(c); return n == null ? 0 : n.doubleValue(); }
    @Override public byte getByte(String l) throws SQLException { return getByte(findColumn(l)); }
    @Override public short getShort(String l) throws SQLException { return getShort(findColumn(l)); }
    @Override public int getInt(String l) throws SQLException { return getInt(findColumn(l)); }
    @Override public long getLong(String l) throws SQLException { return getLong(findColumn(l)); }
    @Override public float getFloat(String l) throws SQLException { return getFloat(findColumn(l)); }
    @Override public double getDouble(String l) throws SQLException { return getDouble(findColumn(l)); }
    @Override public boolean getBoolean(String l) throws SQLException { return getBoolean(findColumn(l)); }

    @Override
    public BigDecimal getBigDecimal(int c) throws SQLException {
        Number n = number(c);
        if (n == null) return null;
        if (n instanceof Long) return BigDecimal.valueOf(n.longValue());
        return new BigDecimal(value(c) instanceof String s ? s.trim() : String.valueOf(n.doubleValue()));
    }

    @Override public BigDecimal getBigDecimal(String l) throws SQLException { return getBigDecimal(findColumn(l)); }

    @Override
    public BigDecimal getBigDecimal(int c, int scale) throws SQLException {
        BigDecimal d = getBigDecimal(c);
        return d == null ? null : d.setScale(scale, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public byte[] getBytes(int c) throws SQLException {
        Object v = value(c);
        if (v == null) return null;
        if (v instanceof byte[] b) return b;
        return v.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override public byte[] getBytes(String l) throws SQLException { return getBytes(findColumn(l)); }

    @Override
    public InputStream getBinaryStream(int c) throws SQLException {
        byte[] b = getBytes(c);
        return b == null ? null : new ByteArrayInputStream(b);
    }

    @Override public InputStream getBinaryStream(String l) throws SQLException { return getBinaryStream(findColumn(l)); }
    @Override public InputStream getAsciiStream(int c) throws SQLException { return getBinaryStream(c); }

    @Override
    public Reader getCharacterStream(int c) throws SQLException {
        String s = getString(c);
        return s == null ? null : new StringReader(s);
    }

    @Override public Reader getCharacterStream(String l) throws SQLException { return getCharacterStream(findColumn(l)); }

    // Dates are stored as ISO-8601 text or epoch numbers; accept both. SQLite's own CURRENT_TIMESTAMP is UTC, so everything is
    // shown as its UTC wall-clock time, whichever form it was stored in.
    private LocalDateTime dateTime(int c) throws SQLException {
        Object v = value(c);
        if (v == null) return null;
        if (v instanceof Number n) {
            // Epoch numbers: seconds below 1e11, milliseconds above (so milliseconds before March 1973 read as seconds).
            long ms = n.doubleValue() > 1e11 ? n.longValue() : n.longValue() * 1000;
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneOffset.UTC);
        }
        String s = v.toString().trim();
        try {
            if (s.length() <= 10) return LocalDate.parse(s).atStartOfDay();
            if (s.endsWith("Z") || s.matches(".*[+-]\\d\\d:?\\d\\d$")) return OffsetDateTime.parse(s.replace(' ', 'T')).withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime();
            return LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (RuntimeException e) {
            throw new SQLException("Cannot convert '" + s + "' to a date/time", "22007");
        }
    }

    @Override
    public Timestamp getTimestamp(int c) throws SQLException {
        LocalDateTime t = dateTime(c);
        return t == null ? null : Timestamp.valueOf(t);
    }

    @Override
    public Date getDate(int c) throws SQLException {
        LocalDateTime t = dateTime(c);
        return t == null ? null : Date.valueOf(t.toLocalDate());
    }

    @Override
    public Time getTime(int c) throws SQLException {
        Object v = value(c);
        if (v instanceof String s && s.trim().matches("\\d{1,2}:\\d\\d(:\\d\\d(\\.\\d+)?)?")) {
            String t = s.trim();
            try {
                return Time.valueOf(LocalTime.parse(t.indexOf(':') == 1 ? "0" + t : t));
            } catch (RuntimeException e) {
                throw new SQLException("Cannot convert '" + t + "' to a time", "22007");
            }
        }
        LocalDateTime t = dateTime(c);
        return t == null ? null : Time.valueOf(t.toLocalTime());
    }

    @Override public Timestamp getTimestamp(int c, Calendar cal) throws SQLException { return getTimestamp(c); }
    @Override public Date getDate(int c, Calendar cal) throws SQLException { return getDate(c); }
    @Override public Time getTime(int c, Calendar cal) throws SQLException { return getTime(c); }
    @Override public Timestamp getTimestamp(String l) throws SQLException { return getTimestamp(findColumn(l)); }
    @Override public Date getDate(String l) throws SQLException { return getDate(findColumn(l)); }
    @Override public Time getTime(String l) throws SQLException { return getTime(findColumn(l)); }

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
