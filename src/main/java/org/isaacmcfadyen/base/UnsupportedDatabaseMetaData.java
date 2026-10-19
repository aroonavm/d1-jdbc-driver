package org.isaacmcfadyen.base;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/** Every method of {@link java.sql.DatabaseMetaData} that the driver does not implement. */
public abstract class UnsupportedDatabaseMetaData implements java.sql.DatabaseMetaData {
    protected static SQLException unsupported(String method) {
        return new SQLFeatureNotSupportedException("D1 driver: " + method + " is not supported");
    }

    @Override
    public boolean allProceduresAreCallable() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean allTablesAreSelectable() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.autoCommitFailureClosesAllResultSets");
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean deletesAreDetected(int p0) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws java.sql.SQLException {
        return false;
    }

    @Override
    public java.sql.ResultSet getAttributes(java.lang.String p0, java.lang.String p1, java.lang.String p2, java.lang.String p3) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getAttributes");
    }

    @Override
    public java.sql.ResultSet getBestRowIdentifier(java.lang.String p0, java.lang.String p1, java.lang.String p2, int p3, boolean p4) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getBestRowIdentifier");
    }

    @Override
    public java.lang.String getCatalogSeparator() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.lang.String getCatalogTerm() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.sql.ResultSet getCatalogs() throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getCatalogs");
    }

    @Override
    public java.sql.ResultSet getClientInfoProperties() throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getClientInfoProperties");
    }

    @Override
    public java.sql.ResultSet getColumnPrivileges(java.lang.String p0, java.lang.String p1, java.lang.String p2, java.lang.String p3) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getColumnPrivileges");
    }

    @Override
    public java.sql.ResultSet getColumns(java.lang.String p0, java.lang.String p1, java.lang.String p2, java.lang.String p3) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getColumns");
    }

    @Override
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getConnection");
    }

    @Override
    public java.sql.ResultSet getCrossReference(java.lang.String p0, java.lang.String p1, java.lang.String p2, java.lang.String p3, java.lang.String p4, java.lang.String p5) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getCrossReference");
    }

    @Override
    public int getDatabaseMajorVersion() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getDatabaseMinorVersion() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public java.lang.String getDatabaseProductName() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.lang.String getDatabaseProductVersion() throws java.sql.SQLException {
        return "";
    }

    @Override
    public int getDefaultTransactionIsolation() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getDriverMajorVersion() {
        return 0;
    }

    @Override
    public int getDriverMinorVersion() {
        return 0;
    }

    @Override
    public java.lang.String getDriverName() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.lang.String getDriverVersion() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.sql.ResultSet getExportedKeys(java.lang.String p0, java.lang.String p1, java.lang.String p2) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getExportedKeys");
    }

    @Override
    public java.lang.String getExtraNameCharacters() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.sql.ResultSet getFunctionColumns(java.lang.String p0, java.lang.String p1, java.lang.String p2, java.lang.String p3) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getFunctionColumns");
    }

    @Override
    public java.sql.ResultSet getFunctions(java.lang.String p0, java.lang.String p1, java.lang.String p2) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getFunctions");
    }

    @Override
    public java.lang.String getIdentifierQuoteString() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.sql.ResultSet getImportedKeys(java.lang.String p0, java.lang.String p1, java.lang.String p2) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getImportedKeys");
    }

    @Override
    public java.sql.ResultSet getIndexInfo(java.lang.String p0, java.lang.String p1, java.lang.String p2, boolean p3, boolean p4) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getIndexInfo");
    }

    @Override
    public int getJDBCMajorVersion() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getJDBCMinorVersion() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxBinaryLiteralLength() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxCatalogNameLength() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxCharLiteralLength() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnNameLength() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInGroupBy() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInIndex() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInOrderBy() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInSelect() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInTable() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxConnections() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxCursorNameLength() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxIndexLength() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxProcedureNameLength() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxRowSize() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxSchemaNameLength() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxStatementLength() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxStatements() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxTableNameLength() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxTablesInSelect() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public int getMaxUserNameLength() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public java.lang.String getNumericFunctions() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.sql.ResultSet getPrimaryKeys(java.lang.String p0, java.lang.String p1, java.lang.String p2) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getPrimaryKeys");
    }

    @Override
    public java.sql.ResultSet getProcedureColumns(java.lang.String p0, java.lang.String p1, java.lang.String p2, java.lang.String p3) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getProcedureColumns");
    }

    @Override
    public java.lang.String getProcedureTerm() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.sql.ResultSet getProcedures(java.lang.String p0, java.lang.String p1, java.lang.String p2) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getProcedures");
    }

    @Override
    public java.sql.ResultSet getPseudoColumns(java.lang.String p0, java.lang.String p1, java.lang.String p2, java.lang.String p3) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getPseudoColumns");
    }

    @Override
    public int getResultSetHoldability() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public java.sql.RowIdLifetime getRowIdLifetime() throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getRowIdLifetime");
    }

    @Override
    public java.lang.String getSQLKeywords() throws java.sql.SQLException {
        return "";
    }

    @Override
    public int getSQLStateType() throws java.sql.SQLException {
        return 0;
    }

    @Override
    public java.lang.String getSchemaTerm() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.sql.ResultSet getSchemas() throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getSchemas");
    }

    @Override
    public java.sql.ResultSet getSchemas(java.lang.String p0, java.lang.String p1) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getSchemas");
    }

    @Override
    public java.lang.String getSearchStringEscape() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.lang.String getStringFunctions() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.sql.ResultSet getSuperTables(java.lang.String p0, java.lang.String p1, java.lang.String p2) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getSuperTables");
    }

    @Override
    public java.sql.ResultSet getSuperTypes(java.lang.String p0, java.lang.String p1, java.lang.String p2) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getSuperTypes");
    }

    @Override
    public java.lang.String getSystemFunctions() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.sql.ResultSet getTablePrivileges(java.lang.String p0, java.lang.String p1, java.lang.String p2) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getTablePrivileges");
    }

    @Override
    public java.sql.ResultSet getTableTypes() throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getTableTypes");
    }

    @Override
    public java.sql.ResultSet getTables(java.lang.String p0, java.lang.String p1, java.lang.String p2, java.lang.String[] p3) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getTables");
    }

    @Override
    public java.lang.String getTimeDateFunctions() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.sql.ResultSet getTypeInfo() throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getTypeInfo");
    }

    @Override
    public java.sql.ResultSet getUDTs(java.lang.String p0, java.lang.String p1, java.lang.String p2, int[] p3) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getUDTs");
    }

    @Override
    public java.lang.String getURL() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.lang.String getUserName() throws java.sql.SQLException {
        return "";
    }

    @Override
    public java.sql.ResultSet getVersionColumns(java.lang.String p0, java.lang.String p1, java.lang.String p2) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.getVersionColumns");
    }

    @Override
    public boolean insertsAreDetected(int p0) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean isCatalogAtStart() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean isReadOnly() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean isWrapperFor(java.lang.Class<?> p0) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean locatorsUpdateCopy() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean nullPlusNonNullIsNull() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedAtEnd() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedAtStart() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedHigh() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedLow() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean othersDeletesAreVisible(int p0) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean othersInsertsAreVisible(int p0) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean othersUpdatesAreVisible(int p0) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean ownDeletesAreVisible(int p0) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean ownInsertsAreVisible(int p0) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean ownUpdatesAreVisible(int p0) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean storesLowerCaseIdentifiers() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean storesMixedCaseIdentifiers() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean storesUpperCaseIdentifiers() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsANSI92FullSQL() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsANSI92IntermediateSQL() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsAlterTableWithAddColumn() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsBatchUpdates() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsColumnAliasing() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsConvert() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsConvert(int p0, int p1) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsCorrelatedSubqueries() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsExpressionsInOrderBy() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsExtendedSQLGrammar() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsFullOuterJoins() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsGetGeneratedKeys() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsGroupBy() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsGroupByUnrelated() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsLikeEscapeClause() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsLimitedOuterJoins() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsMinimumSQLGrammar() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsMultipleOpenResults() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsMultipleResultSets() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsMultipleTransactions() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsNamedParameters() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsNonNullableColumns() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsOrderByUnrelated() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsOuterJoins() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsPositionedDelete() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsPositionedUpdate() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsResultSetConcurrency(int p0, int p1) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsResultSetHoldability(int p0) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsResultSetType(int p0) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsSavepoints() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsSelectForUpdate() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsStatementPooling() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsStoredProcedures() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsSubqueriesInComparisons() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsSubqueriesInExists() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsSubqueriesInIns() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsTableCorrelationNames() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsTransactionIsolationLevel(int p0) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsTransactions() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsUnion() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean supportsUnionAll() throws java.sql.SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(java.lang.Class<T> p0) throws java.sql.SQLException {
        throw unsupported("DatabaseMetaData.unwrap");
    }

    @Override
    public boolean updatesAreDetected(int p0) throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean usesLocalFilePerTable() throws java.sql.SQLException {
        return false;
    }

    @Override
    public boolean usesLocalFiles() throws java.sql.SQLException {
        return false;
    }
}
