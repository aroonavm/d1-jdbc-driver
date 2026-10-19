package org.isaacmcfadyen;

import org.isaacmcfadyen.base.UnsupportedPreparedStatement;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.BatchUpdateException;
import java.sql.Date;
import java.sql.ParameterMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/** Binds `?` parameters; they travel as JSON `params`, except BLOBs which D1's REST API cannot bind and become X'..' literals. */
final class D1PreparedStatement extends UnsupportedPreparedStatement {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String sql;
    private final List<Integer> placeholders;
    private Object[] params;
    private final List<Object[]> batches = new ArrayList<>();

    D1PreparedStatement(D1Connection connection, String sql) {
        super(connection);
        this.sql = sql;
        this.placeholders = SqlScanner.placeholders(sql);
        this.params = new Object[placeholders.size()];
        Arrays.fill(params, UNSET);
    }

    private static final Object UNSET = new Object();
    private static final long MAX_SAFE_INTEGER = (1L << 53) - 1;

    private void set(int index, Object value) throws SQLException {
        open();
        if (index < 1 || index > params.length) throw new SQLException("Parameter index out of range: " + index + " (statement has " + params.length + ")", "07009");
        if (value instanceof Double d && !Double.isFinite(d)) throw new SQLException("NaN and infinite numbers cannot be stored in D1 (parameter " + index + ")", "22003");
        // D1 parses parameters as JavaScript numbers, which are exact only up to 2^53. Larger integers travel as decimal text:
        // SQLite's integer affinity turns them back into exact integers when stored or compared with an INTEGER column.
        if (value instanceof Long l && Math.abs(l) > MAX_SAFE_INTEGER) value = l.toString();
        params[index - 1] = value;
    }

    /** Final SQL and JSON parameter list for the current bindings. */
    private Object[] bind(Object[] values, List<Object> out) throws SQLException {
        StringBuilder text = new StringBuilder(sql.length() + 16);
        int last = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == UNSET) throw new SQLException("Parameter " + (i + 1) + " is not set", "07001");
            if (values[i] instanceof byte[] blob) {
                int at = placeholders.get(i);
                text.append(sql, last, at).append("X'");
                for (byte b : blob) text.append(String.format("%02X", b));
                text.append('\'');
                last = at + 1;
                while (last < sql.length() && Character.isDigit(sql.charAt(last))) last++; // "?1" is one placeholder
            } else {
                out.add(values[i]);
            }
        }
        text.append(sql, last, sql.length());
        return new Object[]{text.toString()};
    }

    private boolean runBound(Object[] values) throws SQLException {
        List<Object> json = new ArrayList<>();
        String text = (String) bind(values, json)[0];
        return run(text, json);
    }

    @Override public boolean execute() throws SQLException { return runBound(params); }

    @Override
    public ResultSet executeQuery() throws SQLException {
        if (!execute()) throw new SQLException("The statement did not return a result set", "02000");
        return getResultSet();
    }

    @Override
    public int executeUpdate() throws SQLException {
        execute();
        return Math.max(getUpdateCount(), 0);
    }

    @Override public boolean execute(String s) throws SQLException { throw new SQLException("Method not allowed on a PreparedStatement", "S1010"); }
    @Override public ResultSet executeQuery(String s) throws SQLException { throw new SQLException("Method not allowed on a PreparedStatement", "S1010"); }
    @Override public int executeUpdate(String s) throws SQLException { throw new SQLException("Method not allowed on a PreparedStatement", "S1010"); }

    @Override
    public void addBatch() throws SQLException {
        open();
        for (int i = 0; i < params.length; i++) if (params[i] == UNSET) throw new SQLException("Parameter " + (i + 1) + " is not set", "07001");
        Object[] copy = params.clone();
        for (int i = 0; i < copy.length; i++) if (copy[i] instanceof byte[] b) copy[i] = b.clone(); // the caller may reuse its buffer
        batches.add(copy);
    }

    @Override
    public void clearBatch() throws SQLException {
        open();
        batches.clear();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        open();
        int[] counts = new int[batches.size()];
        try {
            for (int i = 0; i < counts.length; i++) {
                try {
                    runBound(batches.get(i));
                    counts[i] = Math.max(getUpdateCount(), 0);
                } catch (SQLException e) {
                    throw new BatchUpdateException(e.getMessage(), e.getSQLState(), e.getErrorCode(), Arrays.copyOf(counts, i), e);
                }
            }
        } finally {
            batches.clear();
        }
        return counts;
    }

    @Override
    public void clearParameters() throws SQLException {
        open();
        Arrays.fill(params, UNSET);
    }

    @Override public ResultSetMetaData getMetaData() { return null; }

    @Override
    public ParameterMetaData getParameterMetaData() {
        int count = params.length;
        return new org.isaacmcfadyen.base.UnsupportedParameterMetaData() {
            @Override public int getParameterCount() { return count; }
            @Override public int getParameterType(int p) { return Types.VARCHAR; }
            @Override public String getParameterTypeName(int p) { return "TEXT"; }
            @Override public int isNullable(int p) { return ParameterMetaData.parameterNullableUnknown; }
            @Override public int getParameterMode(int p) { return ParameterMetaData.parameterModeIn; }
            @Override public boolean isSigned(int p) { return false; }
            @Override public int getPrecision(int p) { return 0; }
            @Override public int getScale(int p) { return 0; }
            @Override public String getParameterClassName(int p) { return String.class.getName(); }
            @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("Not a wrapper"); }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        };
    }

    // ---- setters

    @Override public void setNull(int i, int sqlType) throws SQLException { set(i, null); }
    @Override public void setNull(int i, int sqlType, String typeName) throws SQLException { set(i, null); }
    @Override public void setBoolean(int i, boolean v) throws SQLException { set(i, v ? 1L : 0L); }
    @Override public void setByte(int i, byte v) throws SQLException { set(i, (long) v); }
    @Override public void setShort(int i, short v) throws SQLException { set(i, (long) v); }
    @Override public void setInt(int i, int v) throws SQLException { set(i, (long) v); }
    @Override public void setLong(int i, long v) throws SQLException { set(i, v); }
    @Override public void setFloat(int i, float v) throws SQLException { set(i, (double) v); }
    @Override public void setDouble(int i, double v) throws SQLException { set(i, v); }
    @Override public void setString(int i, String v) throws SQLException { set(i, v); }
    @Override public void setNString(int i, String v) throws SQLException { set(i, v); }
    @Override public void setBytes(int i, byte[] v) throws SQLException { set(i, v); }
    @Override public void setDate(int i, Date v) throws SQLException { set(i, v == null ? null : v.toLocalDate().toString()); }
    @Override public void setDate(int i, Date v, Calendar c) throws SQLException { setDate(i, v); }
    @Override public void setTime(int i, Time v) throws SQLException { set(i, v == null ? null : v.toLocalTime().toString().length() == 5 ? v.toLocalTime() + ":00" : v.toLocalTime().toString()); }
    @Override public void setTime(int i, Time v, Calendar c) throws SQLException { setTime(i, v); }
    @Override public void setTimestamp(int i, Timestamp v) throws SQLException { set(i, v == null ? null : format(v.toLocalDateTime())); }
    @Override public void setTimestamp(int i, Timestamp v, Calendar c) throws SQLException { setTimestamp(i, v); }
    @Override public void setBigDecimal(int i, BigDecimal v) throws SQLException { set(i, v == null ? null : v.scale() <= 0 && v.precision() - v.scale() < 19 ? (Object) v.longValue() : (Object) v.doubleValue()); }

    @Override
    public void setBinaryStream(int i, InputStream in, int length) throws SQLException {
        if (length < 0) throw new SQLException("Stream length out of range: " + length, "22003");
        try {
            set(i, in == null ? null : in.readNBytes(length));
        } catch (IOException e) {
            throw new SQLException("Cannot read stream: " + e.getMessage(), "HY000", e);
        }
    }

    @Override
    public void setBinaryStream(int i, InputStream in, long length) throws SQLException {
        if (length < 0 || length > Integer.MAX_VALUE) throw new SQLException("Stream length out of range: " + length, "22003");
        setBinaryStream(i, in, (int) length);
    }

    @Override
    public void setBinaryStream(int i, InputStream in) throws SQLException {
        try {
            set(i, in == null ? null : in.readAllBytes());
        } catch (IOException e) {
            throw new SQLException("Cannot read stream: " + e.getMessage(), "HY000", e);
        }
    }

    @Override
    public void setCharacterStream(int i, Reader in) throws SQLException {
        try {
            if (in == null) {
                set(i, null);
                return;
            }
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            for (int n; (n = in.read(buf)) >= 0; ) sb.append(buf, 0, n);
            set(i, sb.toString());
        } catch (IOException e) {
            throw new SQLException("Cannot read stream: " + e.getMessage(), "HY000", e);
        }
    }

    @Override public void setCharacterStream(int i, Reader in, int length) throws SQLException { setCharacterStream(i, in); }
    @Override public void setCharacterStream(int i, Reader in, long length) throws SQLException { setCharacterStream(i, in); }
    @Override public void setAsciiStream(int i, InputStream in, int length) throws SQLException {
        if (length < 0) throw new SQLException("Stream length out of range: " + length, "22003");
        try {
            set(i, in == null ? null : new String(in.readNBytes(length), java.nio.charset.StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            throw new SQLException("Cannot read stream: " + e.getMessage(), "HY000", e);
        }
    }

    @Override
    public void setObject(int i, Object v) throws SQLException {
        if (v == null) set(i, null);
        else if (v instanceof Boolean b) setBoolean(i, b);
        else if (v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) set(i, ((Number) v).longValue());
        else if (v instanceof BigInteger b) set(i, b.bitLength() < 64 ? (Object) b.longValue() : b.toString());
        else if (v instanceof BigDecimal d) setBigDecimal(i, d);
        else if (v instanceof Number n) set(i, n.doubleValue());
        else if (v instanceof byte[] b) set(i, b);
        else if (v instanceof Timestamp t) setTimestamp(i, t);
        else if (v instanceof Date d) setDate(i, d);
        else if (v instanceof Time t) setTime(i, t);
        else if (v instanceof java.util.Date d) setTimestamp(i, new Timestamp(d.getTime()));
        else if (v instanceof LocalDateTime t) set(i, format(t));
        else if (v instanceof LocalDate d) set(i, d.toString());
        else if (v instanceof LocalTime t) setTime(i, Time.valueOf(t));
        else if (v instanceof OffsetDateTime t) set(i, format(t.atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime())); // same instant as Timestamp binding
        else set(i, v.toString());
    }

    @Override public void setObject(int i, Object v, int targetSqlType) throws SQLException { setObject(i, v); }
    @Override public void setObject(int i, Object v, int targetSqlType, int scaleOrLength) throws SQLException { setObject(i, v); }

    private static String format(LocalDateTime t) {
        String base = t.format(TIMESTAMP);
        return t.getNano() == 0 ? base : base + String.format(".%03d", t.getNano() / 1_000_000);
    }
}
