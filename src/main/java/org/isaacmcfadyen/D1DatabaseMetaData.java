package org.isaacmcfadyen;

import org.isaacmcfadyen.base.UnsupportedDatabaseMetaData;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Schema introspection over SQLite's `sqlite_master` and plain PRAGMA statements (D1 refuses the table-valued `pragma_*` functions).
 * D1 has no catalogs or schemas, so those columns are NULL and the schema/catalog result sets are empty.
 */
final class D1DatabaseMetaData extends UnsupportedDatabaseMetaData {
    private final D1Connection connection;

    D1DatabaseMetaData(D1Connection connection) {
        this.connection = connection;
    }

    // ---- helpers

    private List<Object[]> query(String sql, Object... params) throws SQLException {
        try (D1PreparedStatement ps = (D1PreparedStatement) connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            if (!ps.execute()) return new ArrayList<>();
            List<Object[]> rows = new ArrayList<>();
            try (ResultSet rs = ps.getResultSet()) {
                int n = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Object[] row = new Object[n];
                    for (int c = 0; c < n; c++) row[c] = rs.getObject(c + 1);
                    rows.add(row);
                }
            }
            return rows;
        }
    }

    private static ResultSet result(String[] columns, String[] types, List<Object[]> rows) {
        return new D1ResultSet(null, Arrays.asList(columns), rows, types);
    }

    private static ResultSet empty(String... columns) {
        String[] types = new String[columns.length];
        Arrays.fill(types, "TEXT");
        return result(columns, types, new ArrayList<>());
    }

    /** SQL LIKE pattern to a regex; JDBC's default escape is backslash. */
    private static Pattern like(String pattern) {
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\' && i + 1 < pattern.length()) re.append(Pattern.quote(String.valueOf(pattern.charAt(++i))));
            else if (c == '%') re.append(".*");
            else if (c == '_') re.append('.');
            else re.append(Pattern.quote(String.valueOf(c)));
        }
        return Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    private static boolean matches(Pattern p, Object v) {
        return p == null || (v != null && p.matcher(v.toString()).matches());
    }

    private static boolean isSystem(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("sqlite_");
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static String typeCol(String type) {
        return type.replaceAll("\\(.*\\)", "").trim().toUpperCase(Locale.ROOT);
    }

    // ---- tables

    private static final String[] TABLE_COLS = {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE", "REMARKS", "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "SELF_REFERENCING_COL_NAME", "REF_GENERATION"};

    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
        Pattern name = tableNamePattern == null ? null : like(tableNamePattern);
        List<Object[]> rows = new ArrayList<>();
        for (Object[] r : query("SELECT name, type FROM sqlite_master WHERE type IN ('table','view') ORDER BY name")) {
            String table = str(r[0]);
            if (!matches(name, table) || table.toLowerCase(Locale.ROOT).startsWith("_cf_")) continue; // _cf_KV is Cloudflare-internal
            String type = isSystem(table) ? "SYSTEM TABLE" : "view".equals(r[1]) ? "VIEW" : "TABLE";
            if (types != null && Arrays.stream(types).noneMatch(type::equalsIgnoreCase)) continue;
            rows.add(new Object[]{null, null, table, type, null, null, null, null, null, null});
        }
        String[] t = new String[TABLE_COLS.length];
        Arrays.fill(t, "TEXT");
        return result(TABLE_COLS, t, rows);
    }

    @Override
    public ResultSet getTableTypes() {
        List<Object[]> rows = new ArrayList<>();
        for (String t : new String[]{"SYSTEM TABLE", "TABLE", "VIEW"}) rows.add(new Object[]{t});
        return result(new String[]{"TABLE_TYPE"}, new String[]{"TEXT"}, rows);
    }

    @Override public ResultSet getSchemas() { return empty("TABLE_SCHEM", "TABLE_CATALOG"); }
    @Override public ResultSet getSchemas(String catalog, String schemaPattern) { return empty("TABLE_SCHEM", "TABLE_CATALOG"); }
    @Override public ResultSet getCatalogs() { return empty("TABLE_CAT"); }

    // ---- PRAGMA helpers (D1 allows PRAGMA statements but not the pragma_*() table-valued functions)

    private static final int PRAGMAS_PER_REQUEST = 25;

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static List<java.util.Map<String, Object>> rowsOf(ResultSet rs) throws SQLException {
        List<java.util.Map<String, Object>> out = new ArrayList<>();
        java.sql.ResultSetMetaData md = rs.getMetaData();
        while (rs.next()) {
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            for (int c = 1; c <= md.getColumnCount(); c++) row.put(md.getColumnLabel(c).toLowerCase(Locale.ROOT), rs.getObject(c));
            out.add(row);
        }
        return out;
    }

    /**
     * `PRAGMA <name>(<arg>)` for each arg, as column-name -> value maps aligned with args. Several PRAGMAs share one HTTP request
     * (a whole-schema load would otherwise cost one request per table, against Cloudflare's API rate limit).
     */
    private List<List<java.util.Map<String, Object>>> pragma(String name, List<String> args) throws SQLException {
        List<List<java.util.Map<String, Object>>> out = new ArrayList<>();
        for (int from = 0; from < args.size(); from += PRAGMAS_PER_REQUEST) {
            List<String> chunk = args.subList(from, Math.min(args.size(), from + PRAGMAS_PER_REQUEST));
            List<List<java.util.Map<String, Object>>> got = null;
            if (chunk.size() > 1) {
                StringBuilder sql = new StringBuilder();
                for (String arg : chunk) sql.append(sql.length() == 0 ? "" : "; ").append("PRAGMA ").append(name).append('(').append(quote(arg)).append(')');
                try (java.sql.Statement st = connection.createStatement()) {
                    got = new ArrayList<>();
                    boolean isResultSet = st.execute(sql.toString());
                    if (((D1Statement) st).resultCount() != chunk.size()) throw new SQLException("D1 answered " + ((D1Statement) st).resultCount() + " of " + chunk.size() + " statements");
                    for (int i = 0; i < chunk.size(); i++) {
                        if (i > 0) isResultSet = st.getMoreResults();
                        if (isResultSet) try (ResultSet rs = st.getResultSet()) { got.add(rowsOf(rs)); } else got.add(new ArrayList<>());
                    }
                } catch (SQLException e) {
                    String state = e.getSQLState() == null ? "" : e.getSQLState();
                    if (state.equals("HYT00") || state.equals("70100") || state.startsWith("08")) throw e; // timeout, cancel, unreachable: stop
                    got = null; // one bad table must not hide the others: fall back to one request each
                }
            }
            if (got == null) {
                got = new ArrayList<>();
                for (String arg : chunk) {
                    try (java.sql.Statement st = connection.createStatement()) {
                        got.add(st.execute("PRAGMA " + name + "(" + quote(arg) + ")") ? rowsOf(st.getResultSet()) : new ArrayList<>());
                    }
                }
            }
            out.addAll(got);
        }
        return out;
    }

    private List<java.util.Map<String, Object>> pragma(String name, String arg) throws SQLException {
        return pragma(name, List.of(arg)).get(0);
    }

    /** Names of tables (and views unless tablesOnly) matching an exact name (any case), or all of them when name is null. */
    private List<String> tableNames(String exact, Pattern pattern, boolean tablesOnly) throws SQLException {
        // JDBC says the table argument of the key/index calls is exact, but DBeaver passes "%" to load a whole schema at once.
        if (exact != null && exact.contains("%")) {
            pattern = like(exact);
            exact = null;
        }
        List<String> out = new ArrayList<>();
        for (Object[] r : query("SELECT name FROM sqlite_master WHERE type IN ('table'" + (tablesOnly ? "" : ",'view'") + ") ORDER BY name")) {
            String n = str(r[0]);
            if (n.toLowerCase(Locale.ROOT).startsWith("_cf_")) continue; // Cloudflare-internal; D1 refuses PRAGMAs on it
            if ((exact == null || exact.equalsIgnoreCase(n)) && matches(pattern, n)) out.add(n);
        }
        return out;
    }

    // ---- columns

    private static final String[] COLUMN_COLS = {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE", "BUFFER_LENGTH",
            "DECIMAL_DIGITS", "NUM_PREC_RADIX", "NULLABLE", "REMARKS", "COLUMN_DEF", "SQL_DATA_TYPE", "SQL_DATETIME_SUB", "CHAR_OCTET_LENGTH", "ORDINAL_POSITION",
            "IS_NULLABLE", "SCOPE_CATALOG", "SCOPE_SCHEMA", "SCOPE_TABLE", "SOURCE_DATA_TYPE", "IS_AUTOINCREMENT", "IS_GENERATEDCOLUMN"};
    private static final String[] COLUMN_TYPES = {"TEXT", "TEXT", "TEXT", "TEXT", "INTEGER", "TEXT", "INTEGER", "INTEGER", "INTEGER", "INTEGER", "INTEGER", "TEXT", "TEXT",
            "INTEGER", "INTEGER", "INTEGER", "INTEGER", "TEXT", "TEXT", "TEXT", "TEXT", "INTEGER", "TEXT", "TEXT"};
    private static final Pattern SIZE = Pattern.compile("\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\)");

    @Override
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        Pattern tables = tableNamePattern == null ? null : like(tableNamePattern);
        Pattern columns = columnNamePattern == null ? null : like(columnNamePattern);
        List<Object[]> rows = new ArrayList<>();
        List<String> names = tableNames(null, tables, false);
        List<List<java.util.Map<String, Object>>> infos = pragma("table_info", names);
        for (int t = 0; t < names.size(); t++) {
            String table = names.get(t);
            List<java.util.Map<String, Object>> info = infos.get(t);
            long pkColumns = info.stream().filter(c -> num(c.get("pk")) > 0).count();
            for (java.util.Map<String, Object> c : info) {
                String column = str(c.get("name"));
                if (!matches(columns, column)) continue;
                String declared = c.get("type") == null ? "" : str(c.get("type"));
                java.util.regex.Matcher size = SIZE.matcher(declared);
                boolean sized = size.find();
                // SQLite lets non-INTEGER primary keys hold NULL for legacy reasons, but clients (DBeaver) only accept a key
                // as a row identifier when its columns are NOT NULL, so key columns are reported as such.
                boolean notNull = num(c.get("notnull")) != 0 || num(c.get("pk")) > 0;
                boolean autoIncrement = num(c.get("pk")) == 1 && pkColumns == 1 && "INTEGER".equalsIgnoreCase(declared.trim());
                rows.add(new Object[]{null, null, table, column, (long) D1Types.sqlType(declared), declared.isEmpty() ? "" : typeCol(declared),
                        sized ? Long.parseLong(size.group(1)) : 0L, null, sized && size.group(2) != null ? Long.parseLong(size.group(2)) : 0L, 10L,
                        (long) (notNull ? DatabaseMetaData.columnNoNulls : DatabaseMetaData.columnNullable), null, c.get("dflt_value"), null, null, 0L,
                        num(c.get("cid")) + 1, notNull ? "NO" : "YES", null, null, null, null, autoIncrement ? "YES" : "NO", "NO"});
            }
        }
        return result(COLUMN_COLS, COLUMN_TYPES, rows);
    }

    // ---- keys and indexes

    private static final String[] PK_COLS = {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "KEY_SEQ", "PK_NAME"};

    private static List<Object[]> primaryKeyRows(String table, List<java.util.Map<String, Object>> info) {
        List<java.util.Map<String, Object>> keys = new ArrayList<>();
        for (java.util.Map<String, Object> c : info) if (num(c.get("pk")) > 0) keys.add(c);
        keys.sort(java.util.Comparator.comparingLong(c -> num(c.get("pk"))));
        List<Object[]> rows = new ArrayList<>();
        for (java.util.Map<String, Object> k : keys) rows.add(new Object[]{null, null, table, k.get("name"), num(k.get("pk")), "PRIMARY"});
        return rows;
    }

    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        List<String> names = tableNames(table, null, true);
        List<List<java.util.Map<String, Object>>> infos = pragma("table_info", names);
        for (int i = 0; i < names.size(); i++) rows.addAll(primaryKeyRows(names.get(i), infos.get(i)));
        return result(PK_COLS, new String[]{"TEXT", "TEXT", "TEXT", "TEXT", "INTEGER", "TEXT"}, rows);
    }

    private static final String[] FK_COLS = {"PKTABLE_CAT", "PKTABLE_SCHEM", "PKTABLE_NAME", "PKCOLUMN_NAME", "FKTABLE_CAT", "FKTABLE_SCHEM", "FKTABLE_NAME", "FKCOLUMN_NAME",
            "KEY_SEQ", "UPDATE_RULE", "DELETE_RULE", "FK_NAME", "PK_NAME", "DEFERRABILITY"};
    private static final String[] FK_TYPES = {"TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "INTEGER", "INTEGER", "INTEGER", "TEXT", "TEXT", "INTEGER"};

    private static long rule(Object action) {
        String a = action == null ? "" : action.toString().toUpperCase(Locale.ROOT);
        switch (a) {
            case "CASCADE": return DatabaseMetaData.importedKeyCascade;
            case "SET NULL": return DatabaseMetaData.importedKeySetNull;
            case "SET DEFAULT": return DatabaseMetaData.importedKeySetDefault;
            case "RESTRICT": return DatabaseMetaData.importedKeyRestrict;
            default: return DatabaseMetaData.importedKeyNoAction;
        }
    }

    /** Foreign keys of the tables matching `child` (referencing side) that point at `parent` (referenced side); null means any. */
    private List<Object[]> foreignKeys(String child, String parent) throws SQLException {
        Pattern parentPattern = parent != null && parent.contains("%") ? like(parent) : null;
        List<String> tables = tableNames(child, null, true);
        List<List<java.util.Map<String, Object>>> lists = pragma("foreign_key_list", tables);
        // An FK without a target column references the parent's primary key; look those parents up (once each, in one request).
        java.util.Set<String> needPk = new java.util.LinkedHashSet<>();
        for (List<java.util.Map<String, Object>> list : lists) {
            for (java.util.Map<String, Object> f : list) if (f.get("to") == null && wanted(parent, parentPattern, str(f.get("table")))) needPk.add(str(f.get("table")));
        }
        List<String> parents = new ArrayList<>(needPk);
        java.util.Map<String, List<Object[]>> parentKeys = new java.util.HashMap<>();
        List<List<java.util.Map<String, Object>>> parentInfos = pragma("table_info", parents);
        for (int i = 0; i < parents.size(); i++) parentKeys.put(parents.get(i), primaryKeyRows(parents.get(i), parentInfos.get(i)));

        List<Object[]> rows = new ArrayList<>();
        for (int t = 0; t < tables.size(); t++) {
            for (java.util.Map<String, Object> f : lists.get(t)) {
                String target = str(f.get("table"));
                if (!wanted(parent, parentPattern, target)) continue;
                String to = str(f.get("to"));
                if (to == null) {
                    List<Object[]> pk = parentKeys.getOrDefault(target, List.of());
                    int seq = (int) num(f.get("seq"));
                    to = seq < pk.size() ? str(pk.get(seq)[3]) : null;
                }
                rows.add(new Object[]{null, null, target, to, null, null, tables.get(t), f.get("from"), num(f.get("seq")) + 1, rule(f.get("on_update")), rule(f.get("on_delete")),
                        "fk_" + tables.get(t) + "_" + f.get("id"), null, (long) DatabaseMetaData.importedKeyNotDeferrable});
            }
        }
        return rows;
    }

    private static boolean wanted(String parent, Pattern pattern, String target) {
        return parent == null || (pattern != null ? pattern.matcher(target).matches() : parent.equalsIgnoreCase(target));
    }

    @Override
    public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
        return result(FK_COLS, FK_TYPES, foreignKeys(table, null));
    }

    @Override
    public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
        return result(FK_COLS, FK_TYPES, foreignKeys(null, table));
    }

    @Override
    public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable, String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException {
        return result(FK_COLS, FK_TYPES, foreignKeys(foreignTable, parentTable));
    }

    private static final String[] INDEX_COLS = {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "NON_UNIQUE", "INDEX_QUALIFIER", "INDEX_NAME", "TYPE", "ORDINAL_POSITION",
            "COLUMN_NAME", "ASC_OR_DESC", "CARDINALITY", "PAGES", "FILTER_CONDITION"};

    @Override
    public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) throws SQLException {
        List<String> tables = tableNames(table, null, true);
        List<List<java.util.Map<String, Object>>> lists = pragma("index_list", tables);
        // A partial unique index (CREATE UNIQUE INDEX ... WHERE ...) only constrains some rows, so it must not pass as a row key.
        List<Object[]> indexes = new ArrayList<>(); // {table, index name, unique}
        for (int t = 0; t < tables.size(); t++) {
            List<java.util.Map<String, Object>> list = lists.get(t);
            list.sort(java.util.Comparator.comparingLong((java.util.Map<String, Object> i) -> -num(i.get("unique"))).thenComparing(i -> str(i.get("name"))));
            for (java.util.Map<String, Object> index : list) {
                boolean isUnique = num(index.get("unique")) != 0 && num(index.get("partial")) == 0;
                if (unique && !isUnique) continue;
                indexes.add(new Object[]{tables.get(t), str(index.get("name")), isUnique});
            }
        }
        List<String> indexNames = new ArrayList<>();
        for (Object[] i : indexes) indexNames.add((String) i[1]);
        List<List<java.util.Map<String, Object>>> columnLists = pragma("index_xinfo", indexNames); // index_xinfo also gives the sort direction
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < indexes.size(); i++) {
            for (java.util.Map<String, Object> col : columnLists.get(i)) {
                if (num(col.get("key")) == 0 || col.get("name") == null) continue; // the rowid/aux columns, or an expression rather than a column
                rows.add(new Object[]{null, null, indexes.get(i)[0], (Boolean) indexes.get(i)[2] ? 0L : 1L, null, indexes.get(i)[1], (long) DatabaseMetaData.tableIndexOther,
                        num(col.get("seqno")) + 1, col.get("name"), num(col.get("desc")) != 0 ? "D" : "A", 0L, 0L, null});
            }
        }
        return result(INDEX_COLS, new String[]{"TEXT", "TEXT", "TEXT", "INTEGER", "TEXT", "TEXT", "INTEGER", "INTEGER", "TEXT", "TEXT", "INTEGER", "INTEGER", "TEXT"}, rows);
    }

    @Override
    public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) {
        return empty("SCOPE", "COLUMN_NAME", "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE", "BUFFER_LENGTH", "DECIMAL_DIGITS", "PSEUDO_COLUMN");
    }

    @Override
    public ResultSet getVersionColumns(String catalog, String schema, String table) {
        return empty("SCOPE", "COLUMN_NAME", "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE", "BUFFER_LENGTH", "DECIMAL_DIGITS", "PSEUDO_COLUMN");
    }

    @Override public ResultSet getProcedures(String c, String s, String p) { return empty("PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME", "REMARKS", "PROCEDURE_TYPE", "SPECIFIC_NAME"); }
    @Override public ResultSet getProcedureColumns(String c, String s, String p, String col) { return empty("PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME", "COLUMN_NAME"); }
    @Override public ResultSet getFunctions(String c, String s, String p) { return empty("FUNCTION_CAT", "FUNCTION_SCHEM", "FUNCTION_NAME", "REMARKS", "FUNCTION_TYPE", "SPECIFIC_NAME"); }
    @Override public ResultSet getFunctionColumns(String c, String s, String p, String col) { return empty("FUNCTION_CAT", "FUNCTION_SCHEM", "FUNCTION_NAME", "COLUMN_NAME"); }
    @Override public ResultSet getUDTs(String c, String s, String t, int[] types) { return empty("TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "CLASS_NAME", "DATA_TYPE", "REMARKS", "BASE_TYPE"); }
    @Override public ResultSet getSuperTypes(String c, String s, String t) { return empty("TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "SUPERTYPE_CAT", "SUPERTYPE_SCHEM", "SUPERTYPE_NAME"); }
    @Override public ResultSet getSuperTables(String c, String s, String t) { return empty("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "SUPERTABLE_NAME"); }
    @Override public ResultSet getTablePrivileges(String c, String s, String t) { return empty("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "GRANTOR", "GRANTEE", "PRIVILEGE", "IS_GRANTABLE"); }
    @Override public ResultSet getColumnPrivileges(String c, String s, String t, String col) { return empty("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "GRANTOR", "GRANTEE", "PRIVILEGE", "IS_GRANTABLE"); }
    @Override public ResultSet getAttributes(String c, String s, String t, String a) { return empty("TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "ATTR_NAME"); }
    @Override public ResultSet getPseudoColumns(String c, String s, String t, String col) { return empty("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME"); }
    @Override public ResultSet getClientInfoProperties() { return empty("NAME", "MAX_LEN", "DEFAULT_VALUE", "DESCRIPTION"); }

    @Override
    public ResultSet getTypeInfo() {
        String[] cols = {"TYPE_NAME", "DATA_TYPE", "PRECISION", "LITERAL_PREFIX", "LITERAL_SUFFIX", "CREATE_PARAMS", "NULLABLE", "CASE_SENSITIVE", "SEARCHABLE",
                "UNSIGNED_ATTRIBUTE", "FIXED_PREC_SCALE", "AUTO_INCREMENT", "LOCAL_TYPE_NAME", "MINIMUM_SCALE", "MAXIMUM_SCALE", "SQL_DATA_TYPE", "SQL_DATETIME_SUB", "NUM_PREC_RADIX"};
        String[] types = {"TEXT", "INTEGER", "INTEGER", "TEXT", "TEXT", "TEXT", "INTEGER", "INTEGER", "INTEGER", "INTEGER", "INTEGER", "INTEGER", "TEXT", "INTEGER", "INTEGER", "INTEGER", "INTEGER", "INTEGER"};
        Object[][] defs = {{"INTEGER", Types.BIGINT, 19L, 0L}, {"REAL", Types.DOUBLE, 15L, 0L}, {"TEXT", Types.VARCHAR, 65535L, 1L}, {"BLOB", Types.BLOB, 65535L, 0L},
                {"NUMERIC", Types.NUMERIC, 15L, 0L}, {"BOOLEAN", Types.BOOLEAN, 1L, 0L}, {"DATE", Types.DATE, 10L, 1L}, {"DATETIME", Types.TIMESTAMP, 23L, 1L},
                {"VARCHAR", Types.VARCHAR, 65535L, 1L}, {"DECIMAL", Types.DECIMAL, 15L, 0L}};
        List<Object[]> rows = new ArrayList<>();
        for (Object[] d : defs) {
            boolean quoted = (Long) d[3] == 1L;
            rows.add(new Object[]{d[0], (long) (int) d[1], d[2], quoted ? "'" : null, quoted ? "'" : null, null, (long) typeNullable, 1L, (long) typeSearchable,
                    0L, 0L, 0L, d[0], 0L, 0L, 0L, 0L, 10L});
        }
        return result(cols, types, rows);
    }

    // ---- product / capabilities

    @Override public Connection getConnection() { return connection; }
    @Override public String getURL() { return connection.url; }
    @Override public String getUserName() { return connection.accountId; }
    @Override public String getDatabaseProductName() { return "SQLite"; }
    @Override public String getDatabaseProductVersion() { return "SQLite (Cloudflare D1)"; }
    @Override public int getDatabaseMajorVersion() { return 3; }
    @Override public int getDatabaseMinorVersion() { return 0; }
    @Override public String getDriverName() { return "D1 JDBC Driver"; }
    @Override public String getDriverVersion() { return "2.0.0"; }
    @Override public int getDriverMajorVersion() { return 2; }
    @Override public int getDriverMinorVersion() { return 0; }
    @Override public int getJDBCMajorVersion() { return 4; }
    @Override public int getJDBCMinorVersion() { return 2; }
    @Override public String getIdentifierQuoteString() { return "\""; }
    @Override public String getSearchStringEscape() { return "\\"; }
    @Override public String getExtraNameCharacters() { return ""; }
    @Override public String getSQLKeywords() { return "ABORT,AUTOINCREMENT,GLOB,INDEXED,PRAGMA,ROWID,VACUUM,WITHOUT"; }
    @Override public String getNumericFunctions() { return "abs,max,min,round,random"; }
    @Override public String getStringFunctions() { return "length,lower,upper,substr,trim,replace,instr"; }
    @Override public String getSystemFunctions() { return ""; }
    @Override public String getTimeDateFunctions() { return "date,time,datetime,julianday,strftime"; }
    @Override public String getSchemaTerm() { return ""; }
    @Override public String getProcedureTerm() { return ""; }
    @Override public String getCatalogTerm() { return ""; }
    @Override public String getCatalogSeparator() { return "."; }
    @Override public boolean isCatalogAtStart() { return true; }
    @Override public int getDefaultTransactionIsolation() { return Connection.TRANSACTION_SERIALIZABLE; }
    @Override public int getResultSetHoldability() { return ResultSet.CLOSE_CURSORS_AT_COMMIT; }
    @Override public RowIdLifetime getRowIdLifetime() { return RowIdLifetime.ROWID_UNSUPPORTED; }
    @Override public int getSQLStateType() { return DatabaseMetaData.sqlStateSQL99; }
    @Override public int getMaxConnections() { return 0; }
    @Override public boolean isReadOnly() { return connection.isReadOnly(); }
    @Override public boolean allProceduresAreCallable() { return false; }
    @Override public boolean allTablesAreSelectable() { return true; }
    @Override public boolean nullsAreSortedLow() { return true; }
    @Override public boolean supportsTransactions() { return false; }
    @Override public boolean supportsAlterTableWithAddColumn() { return true; }
    @Override public boolean supportsAlterTableWithDropColumn() { return true; }
    @Override public boolean supportsColumnAliasing() { return true; }
    @Override public boolean supportsGroupBy() { return true; }
    @Override public boolean supportsGroupByUnrelated() { return true; }
    @Override public boolean supportsUnion() { return true; }
    @Override public boolean supportsUnionAll() { return true; }
    @Override public boolean supportsOuterJoins() { return true; }
    @Override public boolean supportsLimitedOuterJoins() { return true; }
    @Override public boolean supportsFullOuterJoins() { return true; }
    @Override public boolean supportsNonNullableColumns() { return true; }
    @Override public boolean supportsMixedCaseIdentifiers() { return true; }
    @Override public boolean supportsMixedCaseQuotedIdentifiers() { return true; }
    @Override public boolean supportsOrderByUnrelated() { return true; }
    @Override public boolean supportsSubqueriesInComparisons() { return true; }
    @Override public boolean supportsSubqueriesInExists() { return true; }
    @Override public boolean supportsSubqueriesInIns() { return true; }
    @Override public boolean supportsCorrelatedSubqueries() { return true; }
    @Override public boolean supportsExpressionsInOrderBy() { return true; }
    @Override public boolean supportsMultipleResultSets() { return true; }
    @Override public boolean supportsMultipleOpenResults() { return false; }
    @Override public boolean supportsBatchUpdates() { return true; }
    @Override public boolean supportsGetGeneratedKeys() { return true; }
    @Override public boolean supportsStatementPooling() { return false; }
    @Override public boolean supportsResultSetType(int type) { return type == ResultSet.TYPE_FORWARD_ONLY || type == ResultSet.TYPE_SCROLL_INSENSITIVE; }
    @Override public boolean supportsResultSetConcurrency(int type, int concurrency) { return concurrency == ResultSet.CONCUR_READ_ONLY && supportsResultSetType(type); }
    @Override public boolean supportsResultSetHoldability(int holdability) { return holdability == ResultSet.CLOSE_CURSORS_AT_COMMIT; }
    @Override public boolean supportsSchemasInDataManipulation() { return false; }
    @Override public boolean supportsCatalogsInDataManipulation() { return false; }
    @Override public boolean supportsSelectForUpdate() { return false; }
    @Override public boolean supportsLikeEscapeClause() { return true; }
    @Override public boolean supportsConvert() { return false; }
    @Override public boolean supportsANSI92EntryLevelSQL() { return true; }
    @Override public boolean supportsDataDefinitionAndDataManipulationTransactions() { return false; }
    @Override public boolean supportsDataManipulationTransactionsOnly() { return false; }
    @Override public boolean dataDefinitionCausesTransactionCommit() { return false; }
    @Override public boolean dataDefinitionIgnoredInTransactions() { return false; }
    @Override public boolean supportsStoredProcedures() { return false; }
    @Override public boolean supportsSavepoints() { return false; }
    @Override public boolean supportsNamedParameters() { return false; }
    @Override public boolean supportsMultipleTransactions() { return false; }
    @Override public boolean supportsOpenCursorsAcrossCommit() { return false; }
    @Override public boolean supportsOpenStatementsAcrossCommit() { return true; }
    @Override public boolean usesLocalFiles() { return false; }
    @Override public boolean usesLocalFilePerTable() { return false; }
    @Override public boolean storesMixedCaseIdentifiers() { return true; }
    @Override public boolean autoCommitFailureClosesAllResultSets() { return false; }

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
