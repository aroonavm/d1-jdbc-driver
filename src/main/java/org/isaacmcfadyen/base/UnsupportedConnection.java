package org.isaacmcfadyen.base;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/** Every method of {@link java.sql.Connection} that the driver does not implement. */
public abstract class UnsupportedConnection implements java.sql.Connection {
    protected static SQLException unsupported(String method) {
        return new SQLFeatureNotSupportedException("D1 driver: " + method + " is not supported");
    }

    @Override
    public void abort(java.util.concurrent.Executor p0) throws java.sql.SQLException {
        throw unsupported("Connection.abort");
    }

    @Override
    public void clearWarnings() throws java.sql.SQLException {
        throw unsupported("Connection.clearWarnings");
    }

    @Override
    public void close() throws java.sql.SQLException {
        throw unsupported("Connection.close");
    }

    @Override
    public void commit() throws java.sql.SQLException {
        throw unsupported("Connection.commit");
    }

    @Override
    public java.sql.Array createArrayOf(java.lang.String p0, java.lang.Object[] p1) throws java.sql.SQLException {
        throw unsupported("Connection.createArrayOf");
    }

    @Override
    public java.sql.Blob createBlob() throws java.sql.SQLException {
        throw unsupported("Connection.createBlob");
    }

    @Override
    public java.sql.Clob createClob() throws java.sql.SQLException {
        throw unsupported("Connection.createClob");
    }

    @Override
    public java.sql.NClob createNClob() throws java.sql.SQLException {
        throw unsupported("Connection.createNClob");
    }

    @Override
    public java.sql.SQLXML createSQLXML() throws java.sql.SQLException {
        throw unsupported("Connection.createSQLXML");
    }

    @Override
    public java.sql.Statement createStatement() throws java.sql.SQLException {
        throw unsupported("Connection.createStatement");
    }

    @Override
    public java.sql.Statement createStatement(int p0, int p1, int p2) throws java.sql.SQLException {
        throw unsupported("Connection.createStatement");
    }

    @Override
    public java.sql.Statement createStatement(int p0, int p1) throws java.sql.SQLException {
        throw unsupported("Connection.createStatement");
    }

    @Override
    public java.sql.Struct createStruct(java.lang.String p0, java.lang.Object[] p1) throws java.sql.SQLException {
        throw unsupported("Connection.createStruct");
    }

    @Override
    public boolean getAutoCommit() throws java.sql.SQLException {
        throw unsupported("Connection.getAutoCommit");
    }

    @Override
    public java.lang.String getCatalog() throws java.sql.SQLException {
        throw unsupported("Connection.getCatalog");
    }

    @Override
    public java.util.Properties getClientInfo() throws java.sql.SQLException {
        throw unsupported("Connection.getClientInfo");
    }

    @Override
    public java.lang.String getClientInfo(java.lang.String p0) throws java.sql.SQLException {
        throw unsupported("Connection.getClientInfo");
    }

    @Override
    public int getHoldability() throws java.sql.SQLException {
        throw unsupported("Connection.getHoldability");
    }

    @Override
    public java.sql.DatabaseMetaData getMetaData() throws java.sql.SQLException {
        throw unsupported("Connection.getMetaData");
    }

    @Override
    public int getNetworkTimeout() throws java.sql.SQLException {
        throw unsupported("Connection.getNetworkTimeout");
    }

    @Override
    public java.lang.String getSchema() throws java.sql.SQLException {
        throw unsupported("Connection.getSchema");
    }

    @Override
    public int getTransactionIsolation() throws java.sql.SQLException {
        throw unsupported("Connection.getTransactionIsolation");
    }

    @Override
    public java.util.Map<java.lang.String, java.lang.Class<?>> getTypeMap() throws java.sql.SQLException {
        throw unsupported("Connection.getTypeMap");
    }

    @Override
    public java.sql.SQLWarning getWarnings() throws java.sql.SQLException {
        throw unsupported("Connection.getWarnings");
    }

    @Override
    public boolean isClosed() throws java.sql.SQLException {
        throw unsupported("Connection.isClosed");
    }

    @Override
    public boolean isReadOnly() throws java.sql.SQLException {
        throw unsupported("Connection.isReadOnly");
    }

    @Override
    public boolean isValid(int p0) throws java.sql.SQLException {
        throw unsupported("Connection.isValid");
    }

    @Override
    public boolean isWrapperFor(java.lang.Class<?> p0) throws java.sql.SQLException {
        throw unsupported("Connection.isWrapperFor");
    }

    @Override
    public java.lang.String nativeSQL(java.lang.String p0) throws java.sql.SQLException {
        throw unsupported("Connection.nativeSQL");
    }

    @Override
    public java.sql.CallableStatement prepareCall(java.lang.String p0, int p1, int p2, int p3) throws java.sql.SQLException {
        throw unsupported("Connection.prepareCall");
    }

    @Override
    public java.sql.CallableStatement prepareCall(java.lang.String p0, int p1, int p2) throws java.sql.SQLException {
        throw unsupported("Connection.prepareCall");
    }

    @Override
    public java.sql.CallableStatement prepareCall(java.lang.String p0) throws java.sql.SQLException {
        throw unsupported("Connection.prepareCall");
    }

    @Override
    public java.sql.PreparedStatement prepareStatement(java.lang.String p0, int[] p1) throws java.sql.SQLException {
        throw unsupported("Connection.prepareStatement");
    }

    @Override
    public java.sql.PreparedStatement prepareStatement(java.lang.String p0, java.lang.String[] p1) throws java.sql.SQLException {
        throw unsupported("Connection.prepareStatement");
    }

    @Override
    public java.sql.PreparedStatement prepareStatement(java.lang.String p0, int p1, int p2, int p3) throws java.sql.SQLException {
        throw unsupported("Connection.prepareStatement");
    }

    @Override
    public java.sql.PreparedStatement prepareStatement(java.lang.String p0, int p1, int p2) throws java.sql.SQLException {
        throw unsupported("Connection.prepareStatement");
    }

    @Override
    public java.sql.PreparedStatement prepareStatement(java.lang.String p0, int p1) throws java.sql.SQLException {
        throw unsupported("Connection.prepareStatement");
    }

    @Override
    public java.sql.PreparedStatement prepareStatement(java.lang.String p0) throws java.sql.SQLException {
        throw unsupported("Connection.prepareStatement");
    }

    @Override
    public void releaseSavepoint(java.sql.Savepoint p0) throws java.sql.SQLException {
        throw unsupported("Connection.releaseSavepoint");
    }

    @Override
    public void rollback() throws java.sql.SQLException {
        throw unsupported("Connection.rollback");
    }

    @Override
    public void rollback(java.sql.Savepoint p0) throws java.sql.SQLException {
        throw unsupported("Connection.rollback");
    }

    @Override
    public void setAutoCommit(boolean p0) throws java.sql.SQLException {
        throw unsupported("Connection.setAutoCommit");
    }

    @Override
    public void setCatalog(java.lang.String p0) throws java.sql.SQLException {
        throw unsupported("Connection.setCatalog");
    }

    @Override
    public void setClientInfo(java.lang.String p0, java.lang.String p1) throws java.sql.SQLClientInfoException {
        throw new java.sql.SQLClientInfoException();
    }

    @Override
    public void setClientInfo(java.util.Properties p0) throws java.sql.SQLClientInfoException {
        throw new java.sql.SQLClientInfoException();
    }

    @Override
    public void setHoldability(int p0) throws java.sql.SQLException {
        throw unsupported("Connection.setHoldability");
    }

    @Override
    public void setNetworkTimeout(java.util.concurrent.Executor p0, int p1) throws java.sql.SQLException {
        throw unsupported("Connection.setNetworkTimeout");
    }

    @Override
    public void setReadOnly(boolean p0) throws java.sql.SQLException {
        throw unsupported("Connection.setReadOnly");
    }

    @Override
    public java.sql.Savepoint setSavepoint() throws java.sql.SQLException {
        throw unsupported("Connection.setSavepoint");
    }

    @Override
    public java.sql.Savepoint setSavepoint(java.lang.String p0) throws java.sql.SQLException {
        throw unsupported("Connection.setSavepoint");
    }

    @Override
    public void setSchema(java.lang.String p0) throws java.sql.SQLException {
        throw unsupported("Connection.setSchema");
    }

    @Override
    public void setTransactionIsolation(int p0) throws java.sql.SQLException {
        throw unsupported("Connection.setTransactionIsolation");
    }

    @Override
    public void setTypeMap(java.util.Map<java.lang.String, java.lang.Class<?>> p0) throws java.sql.SQLException {
        throw unsupported("Connection.setTypeMap");
    }

    @Override
    public <T> T unwrap(java.lang.Class<T> p0) throws java.sql.SQLException {
        throw unsupported("Connection.unwrap");
    }
}
