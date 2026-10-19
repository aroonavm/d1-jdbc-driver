package org.isaacmcfadyen.base;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/** Every method of {@link java.sql.PreparedStatement} that the driver does not implement. */
public abstract class UnsupportedPreparedStatement extends org.isaacmcfadyen.D1Statement implements java.sql.PreparedStatement {
    protected UnsupportedPreparedStatement(org.isaacmcfadyen.D1Connection c) { super(c); }

    protected static SQLException unsupported(String method) {
        return new SQLFeatureNotSupportedException("D1 driver: " + method + " is not supported");
    }

    @Override
    public void addBatch() throws java.sql.SQLException {
        throw unsupported("PreparedStatement.addBatch");
    }

    @Override
    public void clearParameters() throws java.sql.SQLException {
        throw unsupported("PreparedStatement.clearParameters");
    }

    @Override
    public boolean execute() throws java.sql.SQLException {
        throw unsupported("PreparedStatement.execute");
    }

    @Override
    public java.sql.ResultSet executeQuery() throws java.sql.SQLException {
        throw unsupported("PreparedStatement.executeQuery");
    }

    @Override
    public int executeUpdate() throws java.sql.SQLException {
        throw unsupported("PreparedStatement.executeUpdate");
    }

    @Override
    public java.sql.ResultSetMetaData getMetaData() throws java.sql.SQLException {
        throw unsupported("PreparedStatement.getMetaData");
    }

    @Override
    public java.sql.ParameterMetaData getParameterMetaData() throws java.sql.SQLException {
        throw unsupported("PreparedStatement.getParameterMetaData");
    }

    @Override
    public void setArray(int p0, java.sql.Array p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setArray");
    }

    @Override
    public void setAsciiStream(int p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setAsciiStream");
    }

    @Override
    public void setAsciiStream(int p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setAsciiStream");
    }

    @Override
    public void setAsciiStream(int p0, java.io.InputStream p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setAsciiStream");
    }

    @Override
    public void setBigDecimal(int p0, java.math.BigDecimal p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setBigDecimal");
    }

    @Override
    public void setBinaryStream(int p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setBinaryStream");
    }

    @Override
    public void setBinaryStream(int p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setBinaryStream");
    }

    @Override
    public void setBinaryStream(int p0, java.io.InputStream p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setBinaryStream");
    }

    @Override
    public void setBlob(int p0, java.io.InputStream p1, long p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setBlob");
    }

    @Override
    public void setBlob(int p0, java.io.InputStream p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setBlob");
    }

    @Override
    public void setBlob(int p0, java.sql.Blob p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setBlob");
    }

    @Override
    public void setBoolean(int p0, boolean p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setBoolean");
    }

    @Override
    public void setByte(int p0, byte p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setByte");
    }

    @Override
    public void setBytes(int p0, byte[] p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setBytes");
    }

    @Override
    public void setCharacterStream(int p0, java.io.Reader p1, int p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setCharacterStream");
    }

    @Override
    public void setCharacterStream(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setCharacterStream");
    }

    @Override
    public void setCharacterStream(int p0, java.io.Reader p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setCharacterStream");
    }

    @Override
    public void setClob(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setClob");
    }

    @Override
    public void setClob(int p0, java.io.Reader p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setClob");
    }

    @Override
    public void setClob(int p0, java.sql.Clob p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setClob");
    }

    @Override
    public void setDate(int p0, java.sql.Date p1, java.util.Calendar p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setDate");
    }

    @Override
    public void setDate(int p0, java.sql.Date p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setDate");
    }

    @Override
    public void setDouble(int p0, double p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setDouble");
    }

    @Override
    public void setFloat(int p0, float p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setFloat");
    }

    @Override
    public void setInt(int p0, int p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setInt");
    }

    @Override
    public void setLong(int p0, long p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setLong");
    }

    @Override
    public void setNCharacterStream(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setNCharacterStream");
    }

    @Override
    public void setNCharacterStream(int p0, java.io.Reader p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setNCharacterStream");
    }

    @Override
    public void setNClob(int p0, java.io.Reader p1, long p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setNClob");
    }

    @Override
    public void setNClob(int p0, java.io.Reader p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setNClob");
    }

    @Override
    public void setNClob(int p0, java.sql.NClob p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setNClob");
    }

    @Override
    public void setNString(int p0, java.lang.String p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setNString");
    }

    @Override
    public void setNull(int p0, int p1, java.lang.String p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setNull");
    }

    @Override
    public void setNull(int p0, int p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setNull");
    }

    @Override
    public void setObject(int p0, java.lang.Object p1, int p2, int p3) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setObject");
    }

    @Override
    public void setObject(int p0, java.lang.Object p1, int p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setObject");
    }

    @Override
    public void setObject(int p0, java.lang.Object p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setObject");
    }

    @Override
    public void setRef(int p0, java.sql.Ref p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setRef");
    }

    @Override
    public void setRowId(int p0, java.sql.RowId p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setRowId");
    }

    @Override
    public void setSQLXML(int p0, java.sql.SQLXML p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setSQLXML");
    }

    @Override
    public void setShort(int p0, short p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setShort");
    }

    @Override
    public void setString(int p0, java.lang.String p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setString");
    }

    @Override
    public void setTime(int p0, java.sql.Time p1, java.util.Calendar p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setTime");
    }

    @Override
    public void setTime(int p0, java.sql.Time p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setTime");
    }

    @Override
    public void setTimestamp(int p0, java.sql.Timestamp p1, java.util.Calendar p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setTimestamp");
    }

    @Override
    public void setTimestamp(int p0, java.sql.Timestamp p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setTimestamp");
    }

    @Override
    public void setURL(int p0, java.net.URL p1) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setURL");
    }

    @Override
    public void setUnicodeStream(int p0, java.io.InputStream p1, int p2) throws java.sql.SQLException {
        throw unsupported("PreparedStatement.setUnicodeStream");
    }
}
