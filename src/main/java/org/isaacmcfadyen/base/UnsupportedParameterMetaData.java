package org.isaacmcfadyen.base;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/** Every method of {@link java.sql.ParameterMetaData} that the driver does not implement. */
public abstract class UnsupportedParameterMetaData implements java.sql.ParameterMetaData {
    protected static SQLException unsupported(String method) {
        return new SQLFeatureNotSupportedException("D1 driver: " + method + " is not supported");
    }

    @Override
    public java.lang.String getParameterClassName(int p0) throws java.sql.SQLException {
        throw unsupported("ParameterMetaData.getParameterClassName");
    }

    @Override
    public int getParameterCount() throws java.sql.SQLException {
        throw unsupported("ParameterMetaData.getParameterCount");
    }

    @Override
    public int getParameterMode(int p0) throws java.sql.SQLException {
        throw unsupported("ParameterMetaData.getParameterMode");
    }

    @Override
    public int getParameterType(int p0) throws java.sql.SQLException {
        throw unsupported("ParameterMetaData.getParameterType");
    }

    @Override
    public java.lang.String getParameterTypeName(int p0) throws java.sql.SQLException {
        throw unsupported("ParameterMetaData.getParameterTypeName");
    }

    @Override
    public int getPrecision(int p0) throws java.sql.SQLException {
        throw unsupported("ParameterMetaData.getPrecision");
    }

    @Override
    public int getScale(int p0) throws java.sql.SQLException {
        throw unsupported("ParameterMetaData.getScale");
    }

    @Override
    public int isNullable(int p0) throws java.sql.SQLException {
        throw unsupported("ParameterMetaData.isNullable");
    }

    @Override
    public boolean isSigned(int p0) throws java.sql.SQLException {
        throw unsupported("ParameterMetaData.isSigned");
    }

    @Override
    public boolean isWrapperFor(java.lang.Class<?> p0) throws java.sql.SQLException {
        throw unsupported("ParameterMetaData.isWrapperFor");
    }

    @Override
    public <T> T unwrap(java.lang.Class<T> p0) throws java.sql.SQLException {
        throw unsupported("ParameterMetaData.unwrap");
    }
}
