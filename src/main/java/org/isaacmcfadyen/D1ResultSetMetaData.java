package org.isaacmcfadyen;

import org.isaacmcfadyen.base.UnsupportedResultSetMetaData;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

final class D1ResultSetMetaData extends UnsupportedResultSetMetaData {
    private final String[] labels;
    private final String[] typeNames;
    private final int[] types;
    private final String[] tables;
    private final int[] nullable;

    /** @param tables source table per column ("" for computed columns), or null; @param nullable ResultSetMetaData.columnNullable* per column, or null. */
    D1ResultSetMetaData(String[] labels, String[] typeNames, String[] tables, int[] nullable) {
        this.labels = labels;
        this.typeNames = typeNames;
        this.tables = tables;
        this.nullable = nullable;
        this.types = new int[typeNames.length];
        for (int i = 0; i < types.length; i++) types[i] = D1Types.sqlType(typeNames[i]);
    }

    boolean isFloatingPoint(int index) {
        return types[index] == Types.REAL || types[index] == Types.FLOAT || types[index] == Types.DOUBLE;
    }

    private int check(int column) throws SQLException {
        if (column < 1 || column > labels.length) throw new SQLException("Column index out of range: " + column, "S1002");
        return column - 1;
    }

    @Override public int getColumnCount() { return labels.length; }
    @Override public String getColumnLabel(int column) throws SQLException { return labels[check(column)]; }
    @Override public String getColumnName(int column) throws SQLException { return labels[check(column)]; }
    @Override public int getColumnType(int column) throws SQLException { return types[check(column)]; }
    @Override public String getColumnTypeName(int column) throws SQLException { return typeNames[check(column)]; }
    @Override public String getColumnClassName(int column) throws SQLException { return D1Types.className(types[check(column)]); }
    @Override public String getTableName(int column) throws SQLException { int i = check(column); return tables == null ? "" : tables[i]; }
    @Override public String getSchemaName(int column) throws SQLException { check(column); return ""; }
    @Override public String getCatalogName(int column) throws SQLException { check(column); return ""; }
    @Override public boolean isAutoIncrement(int column) { return false; }
    @Override public boolean isCaseSensitive(int column) { return true; }
    @Override public boolean isSearchable(int column) { return true; }
    @Override public boolean isCurrency(int column) { return false; }
    @Override public int isNullable(int column) throws SQLException { int i = check(column); return nullable == null ? ResultSetMetaData.columnNullableUnknown : nullable[i]; }
    @Override public boolean isSigned(int column) throws SQLException {
        int t = types[check(column)];
        return t == Types.BIGINT || t == Types.INTEGER || t == Types.REAL || t == Types.DOUBLE || t == Types.NUMERIC || t == Types.DECIMAL;
    }
    @Override public int getColumnDisplaySize(int column) { return 40; }
    @Override public int getPrecision(int column) { return 0; }
    @Override public int getScale(int column) { return 0; }
    @Override public boolean isReadOnly(int column) { return false; }
    @Override public boolean isWritable(int column) { return true; }
    @Override public boolean isDefinitelyWritable(int column) { return false; }

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
