package org.isaacmcfadyen.base;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/** Every method of {@link java.sql.ResultSetMetaData} that the driver does not implement. */
public abstract class UnsupportedResultSetMetaData implements java.sql.ResultSetMetaData {
    protected static SQLException unsupported(String method) {
        return new SQLFeatureNotSupportedException("D1 driver: " + method + " is not supported");
    }

    @Override
    public java.lang.String getCatalogName(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.getCatalogName");
    }

    @Override
    public java.lang.String getColumnClassName(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.getColumnClassName");
    }

    @Override
    public int getColumnCount() throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.getColumnCount");
    }

    @Override
    public int getColumnDisplaySize(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.getColumnDisplaySize");
    }

    @Override
    public java.lang.String getColumnLabel(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.getColumnLabel");
    }

    @Override
    public java.lang.String getColumnName(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.getColumnName");
    }

    @Override
    public int getColumnType(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.getColumnType");
    }

    @Override
    public java.lang.String getColumnTypeName(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.getColumnTypeName");
    }

    @Override
    public int getPrecision(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.getPrecision");
    }

    @Override
    public int getScale(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.getScale");
    }

    @Override
    public java.lang.String getSchemaName(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.getSchemaName");
    }

    @Override
    public java.lang.String getTableName(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.getTableName");
    }

    @Override
    public boolean isAutoIncrement(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.isAutoIncrement");
    }

    @Override
    public boolean isCaseSensitive(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.isCaseSensitive");
    }

    @Override
    public boolean isCurrency(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.isCurrency");
    }

    @Override
    public boolean isDefinitelyWritable(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.isDefinitelyWritable");
    }

    @Override
    public int isNullable(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.isNullable");
    }

    @Override
    public boolean isReadOnly(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.isReadOnly");
    }

    @Override
    public boolean isSearchable(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.isSearchable");
    }

    @Override
    public boolean isSigned(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.isSigned");
    }

    @Override
    public boolean isWrapperFor(java.lang.Class<?> p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.isWrapperFor");
    }

    @Override
    public boolean isWritable(int p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.isWritable");
    }

    @Override
    public <T> T unwrap(java.lang.Class<T> p0) throws java.sql.SQLException {
        throw unsupported("ResultSetMetaData.unwrap");
    }
}
