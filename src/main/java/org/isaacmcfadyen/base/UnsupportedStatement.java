package org.isaacmcfadyen.base;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/** Every method of {@link java.sql.Statement} that the driver does not implement. */
public abstract class UnsupportedStatement implements java.sql.Statement {
    protected static SQLException unsupported(String method) {
        return new SQLFeatureNotSupportedException("D1 driver: " + method + " is not supported");
    }

    @Override
    public void addBatch(java.lang.String p0) throws java.sql.SQLException {
        throw unsupported("Statement.addBatch");
    }

    @Override
    public void cancel() throws java.sql.SQLException {
        throw unsupported("Statement.cancel");
    }

    @Override
    public void clearBatch() throws java.sql.SQLException {
        throw unsupported("Statement.clearBatch");
    }

    @Override
    public void clearWarnings() throws java.sql.SQLException {
        throw unsupported("Statement.clearWarnings");
    }

    @Override
    public void close() throws java.sql.SQLException {
        throw unsupported("Statement.close");
    }

    @Override
    public void closeOnCompletion() throws java.sql.SQLException {
        throw unsupported("Statement.closeOnCompletion");
    }

    @Override
    public boolean execute(java.lang.String p0, int[] p1) throws java.sql.SQLException {
        throw unsupported("Statement.execute");
    }

    @Override
    public boolean execute(java.lang.String p0, java.lang.String[] p1) throws java.sql.SQLException {
        throw unsupported("Statement.execute");
    }

    @Override
    public boolean execute(java.lang.String p0, int p1) throws java.sql.SQLException {
        throw unsupported("Statement.execute");
    }

    @Override
    public boolean execute(java.lang.String p0) throws java.sql.SQLException {
        throw unsupported("Statement.execute");
    }

    @Override
    public int[] executeBatch() throws java.sql.SQLException {
        throw unsupported("Statement.executeBatch");
    }

    @Override
    public java.sql.ResultSet executeQuery(java.lang.String p0) throws java.sql.SQLException {
        throw unsupported("Statement.executeQuery");
    }

    @Override
    public int executeUpdate(java.lang.String p0, int[] p1) throws java.sql.SQLException {
        throw unsupported("Statement.executeUpdate");
    }

    @Override
    public int executeUpdate(java.lang.String p0, java.lang.String[] p1) throws java.sql.SQLException {
        throw unsupported("Statement.executeUpdate");
    }

    @Override
    public int executeUpdate(java.lang.String p0, int p1) throws java.sql.SQLException {
        throw unsupported("Statement.executeUpdate");
    }

    @Override
    public int executeUpdate(java.lang.String p0) throws java.sql.SQLException {
        throw unsupported("Statement.executeUpdate");
    }

    @Override
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        throw unsupported("Statement.getConnection");
    }

    @Override
    public int getFetchDirection() throws java.sql.SQLException {
        throw unsupported("Statement.getFetchDirection");
    }

    @Override
    public int getFetchSize() throws java.sql.SQLException {
        throw unsupported("Statement.getFetchSize");
    }

    @Override
    public java.sql.ResultSet getGeneratedKeys() throws java.sql.SQLException {
        throw unsupported("Statement.getGeneratedKeys");
    }

    @Override
    public int getMaxFieldSize() throws java.sql.SQLException {
        throw unsupported("Statement.getMaxFieldSize");
    }

    @Override
    public int getMaxRows() throws java.sql.SQLException {
        throw unsupported("Statement.getMaxRows");
    }

    @Override
    public boolean getMoreResults() throws java.sql.SQLException {
        throw unsupported("Statement.getMoreResults");
    }

    @Override
    public boolean getMoreResults(int p0) throws java.sql.SQLException {
        throw unsupported("Statement.getMoreResults");
    }

    @Override
    public int getQueryTimeout() throws java.sql.SQLException {
        throw unsupported("Statement.getQueryTimeout");
    }

    @Override
    public java.sql.ResultSet getResultSet() throws java.sql.SQLException {
        throw unsupported("Statement.getResultSet");
    }

    @Override
    public int getResultSetConcurrency() throws java.sql.SQLException {
        throw unsupported("Statement.getResultSetConcurrency");
    }

    @Override
    public int getResultSetHoldability() throws java.sql.SQLException {
        throw unsupported("Statement.getResultSetHoldability");
    }

    @Override
    public int getResultSetType() throws java.sql.SQLException {
        throw unsupported("Statement.getResultSetType");
    }

    @Override
    public int getUpdateCount() throws java.sql.SQLException {
        throw unsupported("Statement.getUpdateCount");
    }

    @Override
    public java.sql.SQLWarning getWarnings() throws java.sql.SQLException {
        throw unsupported("Statement.getWarnings");
    }

    @Override
    public boolean isCloseOnCompletion() throws java.sql.SQLException {
        throw unsupported("Statement.isCloseOnCompletion");
    }

    @Override
    public boolean isClosed() throws java.sql.SQLException {
        throw unsupported("Statement.isClosed");
    }

    @Override
    public boolean isPoolable() throws java.sql.SQLException {
        throw unsupported("Statement.isPoolable");
    }

    @Override
    public boolean isWrapperFor(java.lang.Class<?> p0) throws java.sql.SQLException {
        throw unsupported("Statement.isWrapperFor");
    }

    @Override
    public void setCursorName(java.lang.String p0) throws java.sql.SQLException {
        throw unsupported("Statement.setCursorName");
    }

    @Override
    public void setEscapeProcessing(boolean p0) throws java.sql.SQLException {
        throw unsupported("Statement.setEscapeProcessing");
    }

    @Override
    public void setFetchDirection(int p0) throws java.sql.SQLException {
        throw unsupported("Statement.setFetchDirection");
    }

    @Override
    public void setFetchSize(int p0) throws java.sql.SQLException {
        throw unsupported("Statement.setFetchSize");
    }

    @Override
    public void setMaxFieldSize(int p0) throws java.sql.SQLException {
        throw unsupported("Statement.setMaxFieldSize");
    }

    @Override
    public void setMaxRows(int p0) throws java.sql.SQLException {
        throw unsupported("Statement.setMaxRows");
    }

    @Override
    public void setPoolable(boolean p0) throws java.sql.SQLException {
        throw unsupported("Statement.setPoolable");
    }

    @Override
    public void setQueryTimeout(int p0) throws java.sql.SQLException {
        throw unsupported("Statement.setQueryTimeout");
    }

    @Override
    public <T> T unwrap(java.lang.Class<T> p0) throws java.sql.SQLException {
        throw unsupported("Statement.unwrap");
    }
}
