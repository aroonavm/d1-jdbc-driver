package org.isaacmcfadyen;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class D1DriverTest {
    static FakeD1Server server;
    Connection conn;

    @BeforeAll
    static void start() throws Exception {
        server = new FakeD1Server(0, "jdbc:sqlite::memory:", FakeD1Server.ACCOUNT, FakeD1Server.TOKEN);
    }

    @AfterAll
    static void stop() throws Exception {
        server.close();
    }

    @BeforeEach
    void reset() throws Exception {
        try (Statement s = server.db().createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS d1_migrations");
            s.executeUpdate("DROP TABLE IF EXISTS child");
            s.executeUpdate("DROP TABLE IF EXISTS parent");
            s.executeUpdate("DROP VIEW IF EXISTS v");
            s.executeUpdate("CREATE TABLE parent (id INTEGER PRIMARY KEY, name VARCHAR(50) NOT NULL, score REAL, data BLOB, created DATETIME)");
            s.executeUpdate("CREATE TABLE child (a INTEGER, b TEXT, parent_id INTEGER REFERENCES parent ON DELETE CASCADE, PRIMARY KEY (a, b))");
            s.executeUpdate("CREATE UNIQUE INDEX parent_name ON parent(name)");
            s.executeUpdate("CREATE VIEW v AS SELECT id FROM parent");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS _cf_KV (key TEXT PRIMARY KEY, value BLOB)"); // exists in every D1 database
            s.executeUpdate("INSERT INTO parent (id, name, score) VALUES (1, 'one', 1.5), (2, 'two', NULL)");
        }
        conn = DriverManager.getConnection(server.url(), FakeD1Server.ACCOUNT, FakeD1Server.TOKEN);
    }

    @Test
    void rejectsBadCredentialsAtConnect() {
        SQLException e = assertThrows(SQLException.class, () -> DriverManager.getConnection(server.url(), FakeD1Server.ACCOUNT, "wrong"));
        assertEquals("28000", e.getSQLState());
        assertThrows(SQLException.class, () -> DriverManager.getConnection(server.url(), "", ""));
    }

    @Test
    void debugPropertyLogsStatements() throws Exception {
        java.nio.file.Path log = java.nio.file.Files.createTempFile("d1-debug", ".log"); // the driver only writes .log files
        Properties p = new Properties();
        p.setProperty("user", FakeD1Server.ACCOUNT);
        p.setProperty("password", FakeD1Server.TOKEN);
        p.setProperty("debug", log.toString());
        try (Connection c = DriverManager.getConnection(server.url(), p); Statement s = c.createStatement()) {
            s.execute("SELECT * FROM nope");
        } catch (SQLException expected) {
            // the failing statement must be logged too
        }
        String text = java.nio.file.Files.readString(log);
        assertTrue(text.contains("OK    SELECT 1"), text);
        assertTrue(text.contains("ERROR SELECT * FROM nope"), text);
        assertFalse(text.contains(FakeD1Server.TOKEN));
    }

    @Test
    void singleTableSelectsReportTheirSourceTable() throws Exception {
        // DBeaver/DataGrip locate the row key through ResultSetMetaData.getTableName.
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT p.id, name, id + 1 AS next FROM parent p WHERE id > 0")) {
            java.sql.ResultSetMetaData md = rs.getMetaData();
            assertEquals("parent", md.getTableName(2));
            assertEquals("", md.getTableName(3));
            assertEquals(java.sql.ResultSetMetaData.columnNoNulls, md.isNullable(2));
            assertEquals("VARCHAR", md.getColumnTypeName(2));
        }
        for (String sql : new String[]{"SELECT * FROM parent JOIN child ON 1", "SELECT * FROM parent, child", "SELECT * FROM (SELECT 1)", "SELECT 1 UNION SELECT 2",
                "SELECT * FROM parent UNION SELECT * FROM parent"}) {
            assertNull(SqlScanner.singleTable(sql), sql);
        }
        assertEquals("parent", SqlScanner.singleTable("select * from main.\"parent\" order by 1"));
        assertEquals("parent", SqlScanner.singleTable("SELECT a, (SELECT 1 FROM child) x FROM [parent] AS p WHERE p.id IN (SELECT id FROM child);"));
        assertEquals("we ird", SqlScanner.singleTable("select * from `we ird`"));
        assertNull(SqlScanner.singleTable("UPDATE parent SET name = 'x'"));
    }

    @Test
    void rateLimitIsRetriedForAnyStatement() throws Exception {
        server.failNext.add(429);
        int before = server.requests.get();
        try (Statement s = conn.createStatement()) {
            assertEquals(1, s.executeUpdate("INSERT INTO parent (name) VALUES ('retried')"));
        }
        assertEquals(2, server.requests.get() - before);
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT count(*) FROM parent WHERE name = 'retried'")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    /** A gateway error can follow a write D1 already applied; resending it could apply it twice. */
    @Test
    void writesAreNotRetriedAfterServerErrorsButReadsAre() throws Exception {
        server.failNext.add(503);
        int before = server.requests.get();
        try (Statement s = conn.createStatement()) {
            assertThrows(SQLException.class, () -> s.executeUpdate("INSERT INTO parent (name) VALUES ('once')"));
            assertEquals(1, server.requests.get() - before);
            server.failNext.add(502);
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM parent")) {
                rs.next();
                assertEquals(2, rs.getInt(1));
            }
        }
        assertFalse(SqlScanner.isSingleRead("WITH x AS (SELECT 1) DELETE FROM parent"));
        assertFalse(SqlScanner.isSingleRead("SELECT 1; DELETE FROM parent"));
        assertFalse(SqlScanner.isSingleRead("PRAGMA foreign_keys = ON"));
        assertTrue(SqlScanner.isSingleRead("select * from parent;"));
    }

    /** An alias that names another column must not be attributed to the table, or a grid edit would hit the wrong column. */
    @Test
    void aliasedColumnsAreNotAttributedToTheTable() throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT id, name AS score, score name, p.name FROM parent p")) {
            java.sql.ResultSetMetaData md = rs.getMetaData();
            assertEquals("parent", md.getTableName(1));
            assertEquals("", md.getTableName(2));
            assertEquals("", md.getTableName(3));
            assertEquals("", md.getTableName(4)); // label "name" was also declared as an alias above, so it is ambiguous
        }
        assertEquals(java.util.Set.of("n", "x"), SqlScanner.selectAliases("SELECT DISTINCT count(*) n, a AS \"x\", t.b, c FROM t"));
    }

    @Test
    void ignoresOtherUrls() throws Exception {
        assertNull(new D1Driver().connect("jdbc:sqlite::memory:", new Properties()));
    }

    @Test
    void queryReturnsTypedValuesAndNulls() throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT id, name, score FROM parent ORDER BY id")) {
            assertEquals(3, rs.getMetaData().getColumnCount());
            assertEquals("score", rs.getMetaData().getColumnLabel(3));
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("id"));
            assertEquals("one", rs.getString(2));
            assertEquals(1.5, rs.getDouble(3));
            assertTrue(rs.next());
            assertEquals(0.0, rs.getDouble(3));
            assertTrue(rs.wasNull());
            assertNull(rs.getString(3));
            assertFalse(rs.next());
        }
    }

    /** Upstream issues #3 and #1: column subsets, DISTINCT and expressions used to fail because columns came from PRAGMA table_info. */
    @Test
    void columnSubsetsDistinctAndExpressions() throws Exception {
        try (Statement s = conn.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT DISTINCT name FROM parent ORDER BY name")) {
                assertTrue(rs.next());
                assertEquals("one", rs.getString("name"));
            }
            try (ResultSet rs = s.executeQuery("SELECT count(*) AS n, upper(name) FROM parent")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt("n"));
            }
        }
    }

    /** Upstream issue #9. */
    @Test
    void deleteWithPreparedStatement() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM parent WHERE id = ?")) {
            ps.setInt(1, 2);
            assertEquals(1, ps.executeUpdate());
            assertEquals(1, ps.getUpdateCount() + 0);
        }
    }

    @Test
    void emptyResultKeepsColumns() throws Exception {
        try (Statement s = conn.createStatement()) {
            assertTrue(s.execute("SELECT id, name FROM parent WHERE id = 99"));
            ResultSet rs = s.getResultSet();
            assertEquals(2, rs.getMetaData().getColumnCount());
            assertFalse(rs.next());
        }
    }

    @Test
    void duplicateColumnNamesFromJoinsSurvive() throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT p.id, c.a AS id FROM parent p LEFT JOIN child c ON c.parent_id = p.id")) {
            assertEquals(2, rs.getMetaData().getColumnCount());
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void updateCountsAndGeneratedKeys() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO parent (name, score) VALUES (?, ?)")) {
            ps.setString(1, "three");
            ps.setDouble(2, 3.25);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                assertEquals(3, keys.getLong(1));
            }
        }
        try (Statement s = conn.createStatement()) {
            assertEquals(2, s.executeUpdate("UPDATE parent SET score = 9 WHERE id < 3"));
            assertFalse(s.execute("DELETE FROM parent WHERE id = 1"));
            assertEquals(1, s.getUpdateCount());
        }
    }

    /** The editing path DBeaver takes from the table grid: UPDATE ... SET col = ? WHERE key = ?. */
    @Test
    void gridStyleEditWithPreparedStatement() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE parent SET name = ?, score = ? WHERE id = ?")) {
            ps.setString(1, "uno");
            ps.setNull(2, Types.REAL);
            ps.setObject(3, 1);
            assertEquals(1, ps.executeUpdate());
        }
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT name, score FROM parent WHERE id = 1")) {
            assertTrue(rs.next());
            assertEquals("uno", rs.getString(1));
            assertNull(rs.getObject(2));
        }
    }

    @Test
    void placeholdersInsideLiteralsAreNotParameters() throws Exception {
        assertEquals(List.of(), SqlScanner.placeholders("SELECT '?', \"?\", `?`, [?] /* ? */ -- ?"));
        assertEquals(2, SqlScanner.placeholders("SELECT 'it''s ?' WHERE a = ? AND b = ?").size());
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM parent WHERE id = ?")) {
            assertThrows(SQLException.class, ps::execute); // parameter 1 not set
            assertThrows(SQLException.class, () -> ps.setInt(2, 1));
        }
    }

    @Test
    void blobsRoundTrip() throws Exception {
        byte[] data = {0, 1, (byte) 0xFE, (byte) 0xFF, 42};
        try (PreparedStatement ps = conn.prepareStatement("UPDATE parent SET data = ? WHERE id = ?")) {
            ps.setBytes(1, data);
            ps.setInt(2, 1);
            assertEquals(1, ps.executeUpdate());
        }
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT data FROM parent WHERE id = 1")) {
            assertTrue(rs.next());
            assertArrayEquals(data, rs.getBytes(1));
        }
    }

    @Test
    void batchRunsEveryParameterSet() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO parent (name) VALUES (?)")) {
            for (String n : new String[]{"a", "b", "c"}) {
                ps.setString(1, n);
                ps.addBatch();
            }
            assertArrayEquals(new int[]{1, 1, 1}, ps.executeBatch());
        }
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT count(*) FROM parent")) {
            rs.next();
            assertEquals(5, rs.getInt(1));
        }
    }

    @Test
    void sqlErrorsCarryD1Message() {
        SQLException e = assertThrows(SQLException.class, () -> {
            try (Statement s = conn.createStatement()) {
                s.execute("SELECT * FROM nope");
            }
        });
        assertTrue(e.getMessage().contains("no such table"), e.getMessage());
    }

    @Test
    void unauthorisedIntrospectionPragmaIsAnsweredLocally() throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("PRAGMA database_list")) {
            assertTrue(rs.next());
            assertEquals("main", rs.getString("name"));
        }
    }

    @Test
    void multipleBindingsOfDatesAndBooleans() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE parent SET created = ?, score = ? WHERE id = 1")) {
            ps.setTimestamp(1, java.sql.Timestamp.valueOf("2024-05-06 07:08:09"));
            ps.setBoolean(2, true);
            ps.executeUpdate();
        }
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT created, score FROM parent WHERE id = 1")) {
            rs.next();
            assertEquals("2024-05-06 07:08:09", rs.getString(1));
            assertEquals(java.sql.Timestamp.valueOf("2024-05-06 07:08:09"), rs.getTimestamp(1));
            assertTrue(rs.getBoolean(2));
        }
    }

    // ---- metadata

    private List<String> column(ResultSet rs, String name) throws SQLException {
        List<String> out = new ArrayList<>();
        while (rs.next()) out.add(rs.getString(name));
        return out;
    }

    @Test
    void tablesAndViews() throws Exception {
        DatabaseMetaData md = conn.getMetaData();
        assertEquals(List.of("child", "parent", "v"), column(md.getTables(null, null, "%", new String[]{"TABLE", "VIEW"}), "TABLE_NAME"));
        assertEquals(List.of("v"), column(md.getTables(null, null, "%", new String[]{"VIEW"}), "TABLE_NAME"));
        assertEquals(List.of("parent"), column(md.getTables(null, null, "par%", new String[]{"TABLE"}), "TABLE_NAME"));
        assertFalse(md.getSchemas().next());
        assertFalse(md.getCatalogs().next());
    }

    @Test
    void columnsCarryTypesSizesAndNullability() throws Exception {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "parent", "%")) {
            assertTrue(rs.next());
            assertEquals("id", rs.getString("COLUMN_NAME"));
            assertEquals(Types.BIGINT, rs.getInt("DATA_TYPE"));
            assertEquals("YES", rs.getString("IS_AUTOINCREMENT"));
            assertTrue(rs.next());
            assertEquals("name", rs.getString("COLUMN_NAME"));
            assertEquals("VARCHAR", rs.getString("TYPE_NAME"));
            assertEquals(50, rs.getInt("COLUMN_SIZE"));
            assertEquals(DatabaseMetaData.columnNoNulls, rs.getInt("NULLABLE"));
            assertEquals(2, rs.getInt("ORDINAL_POSITION"));
            assertTrue(rs.next());
            assertEquals("score", rs.getString("COLUMN_NAME"));
            assertEquals(Types.REAL, rs.getInt("DATA_TYPE"));
        }
    }

    @Test
    void primaryKeysKeepOrder() throws Exception {
        DatabaseMetaData md = conn.getMetaData();
        assertEquals(List.of("id"), column(md.getPrimaryKeys(null, null, "parent"), "COLUMN_NAME"));
        try (ResultSet rs = md.getPrimaryKeys(null, null, "child")) {
            assertTrue(rs.next());
            assertEquals("a", rs.getString("COLUMN_NAME"));
            assertEquals(1, rs.getInt("KEY_SEQ"));
            assertTrue(rs.next());
            assertEquals("b", rs.getString("COLUMN_NAME"));
            assertEquals(2, rs.getInt("KEY_SEQ"));
        }
        assertFalse(md.getPrimaryKeys(null, null, "v").next());
    }

    /** DBeaver only uses a key for grid editing when its columns are NOT NULL; SQLite does not imply that for TEXT keys. */
    @Test
    void primaryKeyColumnsAreReportedNotNull() throws Exception {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "child", "%")) {
            List<String> nullable = new ArrayList<>();
            while (rs.next()) nullable.add(rs.getString("COLUMN_NAME") + "=" + rs.getString("IS_NULLABLE"));
            assertEquals(List.of("a=NO", "b=NO", "parent_id=YES"), nullable);
        }
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT b, parent_id FROM child")) {
            assertEquals(java.sql.ResultSetMetaData.columnNoNulls, rs.getMetaData().isNullable(1));
            assertEquals(java.sql.ResultSetMetaData.columnNullable, rs.getMetaData().isNullable(2));
        }
    }

    /** Whole-schema loads must skip _cf_KV: real D1 answers any PRAGMA on it with SQLITE_AUTH. */
    @Test
    void cloudflareInternalTableIsSkippedEverywhere() throws Exception {
        DatabaseMetaData md = conn.getMetaData();
        assertFalse(column(md.getTables(null, null, "%", null), "TABLE_NAME").contains("_cf_KV"));
        assertFalse(column(md.getColumns(null, null, "%", "%"), "TABLE_NAME").contains("_cf_KV"));
        assertFalse(column(md.getPrimaryKeys(null, null, "%"), "TABLE_NAME").contains("_cf_KV"));
        md.getImportedKeys(null, null, "%").close();
        md.getIndexInfo(null, null, "%", false, false).close();
    }


    // ---- review fixes

    /** A partial unique index only constrains some rows: it must not be offered as a row key. Expression indexes have no column. */
    @Test
    void partialUniqueExpressionAndDescendingIndexes() throws Exception {
        try (Statement s = server.db().createStatement()) {
            s.executeUpdate("CREATE UNIQUE INDEX parent_live ON parent(score) WHERE score > 0");
            s.executeUpdate("CREATE INDEX parent_lower ON parent(lower(name))");
            s.executeUpdate("CREATE INDEX parent_recent ON parent(created DESC)");
        }
        DatabaseMetaData md = conn.getMetaData();
        java.util.Map<String, Boolean> nonUnique = new java.util.HashMap<>();
        java.util.Map<String, String> direction = new java.util.HashMap<>();
        try (ResultSet rs = md.getIndexInfo(null, null, "parent", false, false)) {
            while (rs.next()) {
                assertNotNull(rs.getString("COLUMN_NAME"), rs.getString("INDEX_NAME"));
                nonUnique.put(rs.getString("INDEX_NAME"), rs.getBoolean("NON_UNIQUE"));
                direction.put(rs.getString("INDEX_NAME"), rs.getString("ASC_OR_DESC"));
            }
        }
        assertEquals(false, nonUnique.get("parent_name"));
        assertEquals(true, nonUnique.get("parent_live"));
        assertFalse(nonUnique.containsKey("parent_lower"));
        assertEquals("D", direction.get("parent_recent"));
        assertEquals("A", direction.get("parent_name"));
        assertEquals(List.of("parent_name"), column(md.getIndexInfo(null, null, "parent", true, false), "INDEX_NAME"));
    }

    @Test
    void malformedIdsAndInsecureEndpointsFailAsSqlExceptions() {
        for (String url : new String[]{"jdbc:d1://bad id", "jdbc:d1://x/../../y", "jdbc:d1://" + FakeD1Server.DATABASE + "?endpoint=http://example.com/client/v4",
                "jdbc:d1://" + FakeD1Server.DATABASE + "?endpoint=ftp://127.0.0.1", "jdbc:d1://" + FakeD1Server.DATABASE + "?endpoint=%20%25", "jdbc:d1://" + FakeD1Server.DATABASE + "?endpoint=https://"}) {
            assertThrows(SQLException.class, () -> DriverManager.getConnection(url, FakeD1Server.ACCOUNT, FakeD1Server.TOKEN), url);
        }
        assertThrows(SQLException.class, () -> DriverManager.getConnection(server.url(), "bad/account", FakeD1Server.TOKEN));
    }

    @Test
    void credentialsNeverAppearInTheReportedUrl() throws Exception {
        for (String credentials : new String[]{"&accountId=" + FakeD1Server.ACCOUNT + "&apiToken=" + FakeD1Server.TOKEN, "&user=" + FakeD1Server.ACCOUNT + "&password=" + FakeD1Server.TOKEN}) {
            try (Connection c = DriverManager.getConnection(server.url() + credentials)) {
                String url = c.getMetaData().getURL();
                assertFalse(url.contains(FakeD1Server.TOKEN) || url.contains("apiToken") || url.contains("password") || url.contains("user="), url);
                assertTrue(url.startsWith("jdbc:d1://" + FakeD1Server.DATABASE + "?endpoint="), url);
            }
        }
    }

    @Test
    void nonFiniteAndMalformedResponsesAreSqlExceptions() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE parent SET score = ? WHERE id = 1")) {
            assertThrows(SQLException.class, () -> ps.setDouble(1, Double.NaN));
            assertThrows(SQLException.class, () -> ps.setObject(1, Float.POSITIVE_INFINITY));
        }
        server.malformedNext = true;
        try (Statement s = conn.createStatement()) {
            SQLException e = assertThrows(SQLException.class, () -> s.execute("SELECT 1"));
            assertTrue(e.getMessage().contains("Unexpected response"), e.getMessage());
        }
    }

    /** Under a Turkish locale "LIMIT".toLowerCase() is "lımıt": keyword matching must not depend on the default locale. */
    @Test
    void sqlRecognitionIgnoresTheDefaultLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("parent", SqlScanner.singleTable("SELECT * FROM parent LIMIT 5"));
            assertTrue(SqlScanner.looksLikeQuery("WITH x AS (SELECT 1) SELECT * FROM x"));
            assertEquals("insert", SqlScanner.firstKeyword("INSERT INTO parent DEFAULT VALUES"));
            assertNotNull(SqlScanner.withLimit("SELECT * FROM parent", 5));
        } finally {
            Locale.setDefault(saved);
        }
    }

    /** A migration run elsewhere adds a column while the connection is open: SELECT * must pick it up as an editable column. */
    @Test
    void columnsAddedBehindTheConnectionAreAttributed() throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM parent")) {
            assertEquals("parent", rs.getMetaData().getTableName(1));
        }
        try (Statement s = server.db().createStatement()) {
            s.executeUpdate("ALTER TABLE parent ADD COLUMN extra TEXT");
        }
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM parent")) {
            int extra = rs.findColumn("extra");
            assertEquals("parent", rs.getMetaData().getTableName(extra));
        }
        try (Statement s = server.db().createStatement()) {
            s.executeUpdate("ALTER TABLE parent DROP COLUMN extra");
        }
    }

    /** last_row_id is stale after an UPDATE, so generated keys exist only for INSERT/REPLACE. */
    @Test
    void generatedKeysOnlyAfterInserts() throws Exception {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("INSERT INTO parent (name) VALUES ('k')");
            try (ResultSet keys = s.getGeneratedKeys()) {
                assertTrue(keys.next());
            }
            assertEquals(1, s.executeUpdate("UPDATE parent SET score = 1 WHERE name = 'k'"));
            try (ResultSet keys = s.getGeneratedKeys()) {
                assertFalse(keys.next());
            }
        }
    }

    @Test
    void batchFailureReportsCountsOfWhatRan() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO parent (id, name) VALUES (?, ?)")) {
            for (int[] row : new int[][]{{10, 1}, {11, 2}, {10, 3}}) { // third repeats id 10
                ps.setInt(1, row[0]);
                ps.setString(2, "b" + row[1]);
                ps.addBatch();
            }
            java.sql.BatchUpdateException e = assertThrows(java.sql.BatchUpdateException.class, ps::executeBatch);
            assertArrayEquals(new int[]{1, 1}, e.getUpdateCounts());
        }
    }

    /** setMaxRows must limit what D1 sends, not what the driver keeps. */
    @Test
    void maxRowsIsPushedDownAsLimit() throws Exception {
        try (Statement s = conn.createStatement()) {
            s.setMaxRows(1);
            try (ResultSet rs = s.executeQuery("SELECT id FROM parent ORDER BY id; ")) {
                assertTrue(rs.next());
                assertFalse(rs.next());
            }
            assertTrue(server.lastSql.endsWith("LIMIT 1"), server.lastSql);
            try (ResultSet rs = s.executeQuery("SELECT id FROM parent -- trailing comment")) {
                assertTrue(rs.next());
            }
            assertTrue(server.lastSql.endsWith("LIMIT 1"), server.lastSql);
            s.executeQuery("SELECT id FROM parent LIMIT 2").close();
            assertEquals("SELECT id FROM parent LIMIT 2", server.lastSql); // the caller's own LIMIT wins
            s.executeUpdate("UPDATE parent SET score = score WHERE 0");
            assertFalse(server.lastSql.contains("LIMIT"));
        }
    }

    @Test
    void declaredRealColumnsReadAsDoublesAndDatesAreUtc() throws Exception {
        try (Statement s = server.db().createStatement()) {
            s.executeUpdate("UPDATE parent SET score = 2.0, created = 1700000000 WHERE id = 1"); // D1 sends 2.0 as 2
        }
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT score, created FROM parent WHERE id = 1")) {
            assertTrue(rs.next());
            assertEquals(Double.class, rs.getObject(1).getClass());
            assertEquals("2.0", rs.getString(1));
            assertEquals(java.time.LocalDateTime.of(2023, 11, 14, 22, 13, 20), rs.getTimestamp(2).toLocalDateTime());
        }
    }

    @Test
    void withDeleteIsAWriteAndBlobsSurviveNumberedPlaceholders() throws Exception {
        try (Statement s = conn.createStatement()) {
            assertFalse(s.execute("WITH ids AS (SELECT 2 AS id) DELETE FROM parent WHERE id IN (SELECT id FROM ids)"));
            assertEquals(1, s.getUpdateCount());
        }
        try (PreparedStatement ps = conn.prepareStatement("UPDATE parent SET data = ?1 WHERE id = 1")) {
            ps.setBytes(1, new byte[]{1, 2});
            assertEquals(1, ps.executeUpdate());
        }
    }

    /** A whole-schema load must not cost a request per table. */
    @Test
    void wholeSchemaMetadataSharesRequests() throws Exception {
        DatabaseMetaData md = conn.getMetaData();
        int before = server.requests.get();
        md.getColumns(null, null, "%", "%").close();
        md.getPrimaryKeys(null, null, "%").close();
        md.getImportedKeys(null, null, "%").close();
        md.getIndexInfo(null, null, "%", false, false).close();
        int used = server.requests.get() - before;
        assertTrue(used <= 12, "requests used: " + used); // 4 table listings + a handful of batched PRAGMA requests
        // and an error in one batched request falls back to per-table requests without losing the rest
        assertEquals(List.of("id"), column(md.getPrimaryKeys(null, null, "parent"), "COLUMN_NAME"));
    }

    /** Each result of a multi-statement request is judged by its own statement, never by the first one. */
    @Test
    void multiStatementResultsAreJudgedOneByOne() throws Exception {
        try (Statement s = server.db().createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS other (id INTEGER PRIMARY KEY, name TEXT)");
            s.executeUpdate("INSERT OR REPLACE INTO other VALUES (7, 'y')");
        }
        try (Statement s = conn.createStatement()) {
            assertTrue(s.execute("SELECT * FROM parent WHERE 1; SELECT id, name FROM other"));
            assertEquals("parent", s.getResultSet().getMetaData().getTableName(1));
            assertTrue(s.getMoreResults());
            assertEquals("other", s.getResultSet().getMetaData().getTableName(1)); // not "parent": editing it would write into parent
            assertTrue(s.execute("SELECT 1; DELETE FROM other"));
            assertFalse(s.getMoreResults());
            assertEquals(1, s.getUpdateCount());
        } finally {
            try (Statement s = server.db().createStatement()) {
                s.executeUpdate("DROP TABLE other");
            }
        }
        List<String> parts = SqlScanner.splitStatements("SELECT ';'; -- x;\n SELECT (1);");
        assertEquals(2, parts.size(), parts.toString()); // the ';' in the string and in the comment do not split
        assertEquals("SELECT ';'", parts.get(0));
        assertTrue(parts.get(1).endsWith("SELECT (1)"), parts.get(1));
    }

    @Test
    void readOnlyConnectionsRefuseWrites() throws Exception {
        conn.setReadOnly(true);
        try (Statement s = conn.createStatement()) {
            for (String sql : new String[]{"DELETE FROM parent", "SELECT 1; DELETE FROM parent", "WITH x AS (SELECT 1) DELETE FROM parent", "PRAGMA foreign_keys = OFF",
                    "CREATE TABLE z (a)"}) {
                SQLException e = assertThrows(SQLException.class, () -> s.execute(sql), sql);
                assertEquals("25006", e.getSQLState());
            }
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM parent")) {
                rs.next();
                assertEquals(2, rs.getInt(1));
            }
            assertTrue(conn.getMetaData().getColumns(null, null, "%", "%").next()); // metadata still works
        } finally {
            conn.setReadOnly(false);
        }
    }

    @Test
    void failedExecuteLeavesNoStaleResults() throws Exception {
        try (Statement s = conn.createStatement()) {
            assertTrue(s.execute("SELECT 1; SELECT 2"));
            assertThrows(SQLException.class, () -> s.execute("SELECT * FROM nope"));
            assertFalse(s.getMoreResults());
            assertEquals(-1, s.getUpdateCount());
            assertNull(s.getResultSet());
        }
    }

    @Test
    void internalTablesCostNoLookupRequest() throws Exception {
        try (Statement s = conn.createStatement()) {
            int before = server.requests.get();
            s.executeQuery("SELECT name FROM sqlite_master").close();
            assertEquals(1, server.requests.get() - before);
        }
    }

    @Test
    void withQueriesReadEvenWhenTheyCallReplace() throws Exception {
        assertTrue(SqlScanner.onlyReads("WITH x AS (SELECT 'a' v) SELECT replace(v, 'a', 'b') FROM x"));
        assertFalse(SqlScanner.onlyReads("WITH x AS (SELECT 1) INSERT INTO parent (id, name) SELECT 9, 'n' FROM x"));
        assertFalse(SqlScanner.onlyReads("WITH x AS (SELECT 1) DELETE FROM parent"));
    }

    @Test
    void timesAsTextAndWriterEdgeCases() throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT '1:30:00.5' a, '9:05' b, 'x' c")) {
            rs.next();
            assertEquals(java.time.LocalTime.of(1, 30, 0), rs.getTime(1).toLocalTime());
            assertEquals(java.time.LocalTime.of(9, 5), rs.getTime(2).toLocalTime());
            assertEquals("22007", assertThrows(SQLException.class, () -> rs.getTime(3)).getSQLState());
        }
        try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT typeof(?), ?, ?")) {
            ps.setAsciiStream(1, new java.io.ByteArrayInputStream("abc".getBytes()), 3); // text, not a BLOB
            java.time.OffsetDateTime t = java.time.OffsetDateTime.of(2024, 5, 1, 10, 0, 0, 0, java.time.ZoneOffset.ofHoursMinutes(5, 30));
            ps.setObject(2, t);
            ps.setTimestamp(3, java.sql.Timestamp.from(t.toInstant()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals("text", rs.getString(1));
                assertEquals(rs.getString(3), rs.getString(2)); // same instant, same text
            }
        }
    }

    @Test
    void batchKeepsEachBlobEvenWhenTheBufferIsReused() throws Exception {
        try (java.sql.PreparedStatement ps = conn.prepareStatement("UPDATE parent SET data = ? WHERE id = ?")) {
            byte[] buffer = {1};
            ps.setBytes(1, buffer);
            ps.setInt(2, 1);
            ps.addBatch();
            buffer[0] = 2;
            ps.setInt(2, 2);
            ps.addBatch();
            ps.executeBatch();
        }
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT data FROM parent ORDER BY id")) {
            rs.next();
            assertArrayEquals(new byte[]{1}, rs.getBytes(1));
        }
    }

    /** Explicit column lists re-read a table's columns after a while: a migration elsewhere may have changed them. */
    @Test
    void cachedColumnInfoExpires() throws Exception {
        long saved = D1Connection.TABLE_INFO_TTL_MILLIS;
        try (Statement s = conn.createStatement()) {
            s.executeQuery("SELECT id, name FROM parent").close();
            try (Statement d = server.db().createStatement()) {
                d.executeUpdate("ALTER TABLE parent ADD COLUMN extra TEXT");
            }
            D1Connection.TABLE_INFO_TTL_MILLIS = 0;
            try (ResultSet rs = s.executeQuery("SELECT id, extra FROM parent")) {
                assertEquals("parent", rs.getMetaData().getTableName(2));
            }
        } finally {
            D1Connection.TABLE_INFO_TTL_MILLIS = saved;
            try (Statement d = server.db().createStatement()) {
                d.executeUpdate("ALTER TABLE parent DROP COLUMN extra");
            }
        }
    }

    @Test
    void realValuesAsPlainText() throws Exception {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("UPDATE parent SET score = 12345678.5 WHERE id = 1");
        }
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT score FROM parent WHERE id = 1")) {
            rs.next();
            assertEquals("12345678.5", rs.getString(1));
        }
    }

    /** D1 parses parameters as JavaScript numbers; integers beyond 2^53 must still reach the right row. */
    @Test
    void integersBeyondJavascriptPrecisionStayExact() throws Exception {
        long big = 9007199254740993L;
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO parent (id, name) VALUES (?, 'big')")) {
            ps.setLong(1, big);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("SELECT CAST(id AS TEXT) FROM parent WHERE id = ?")) {
            ps.setObject(1, big);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(Long.toString(big), rs.getString(1));
            }
        }
    }

    @Test
    void settingPragmasAreWritesAndDebugLogMustBeALogFile() throws Exception {
        assertFalse(SqlScanner.looksLikeQuery("PRAGMA foreign_keys(0)"));
        assertFalse(SqlScanner.looksLikeQuery("PRAGMA optimize"));
        assertTrue(SqlScanner.looksLikeQuery("PRAGMA table_info(\"parent\")"));
        assertTrue(SqlScanner.looksLikeQuery("PRAGMA main.index_list(parent)"));
        assertTrue(SqlScanner.looksLikeQuery("PRAGMA foreign_keys"));
        conn.setReadOnly(true);
        try (Statement s = conn.createStatement()) {
            assertThrows(SQLException.class, () -> s.execute("PRAGMA foreign_keys(0)"));
        } finally {
            conn.setReadOnly(false);
        }
        Properties p = new Properties();
        p.setProperty("user", FakeD1Server.ACCOUNT);
        p.setProperty("password", FakeD1Server.TOKEN);
        p.setProperty("debug", System.getProperty("user.home") + "/.bashrc");
        assertThrows(SQLException.class, () -> DriverManager.getConnection(server.url(), p));
        assertEquals(List.of("endpoint", "debug"), java.util.Arrays.stream(new D1Driver().getPropertyInfo(server.url(), new Properties())).map(i -> i.name).toList());
    }

    @Test
    void narrowGettersRefuseOverflowInsteadOfWrapping() throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT 5000000000, 2.7, 300, -129.5")) {
            rs.next();
            assertEquals(5000000000L, rs.getLong(1));
            assertEquals("22003", assertThrows(SQLException.class, () -> rs.getInt(1)).getSQLState());
            assertEquals(2, rs.getInt(2)); // fractions truncate, as JDBC allows
            assertThrows(SQLException.class, () -> rs.getByte(3));
            assertEquals(300, rs.getShort(3));
            assertThrows(SQLException.class, () -> rs.getByte(4));
        }
    }

    @Test
    void malformedUrlEscapesAndCloseOnCompletion() throws Exception {
        assertThrows(SQLException.class, () -> DriverManager.getConnection(server.url() + "&x=%zz", FakeD1Server.ACCOUNT, FakeD1Server.TOKEN));
        Statement s = conn.createStatement();
        s.closeOnCompletion();
        s.executeQuery("SELECT 1");
        ResultSet rs = s.executeQuery("SELECT 2"); // re-executing discards the first result set without closing the statement
        assertFalse(s.isClosed());
        rs.close();
        assertTrue(s.isClosed());
    }

    /** DBeaver loads all keys of a schema in one call, passing "%" as the table. */
    @Test
    void keyCallsAcceptWildcardTable() throws Exception {
        DatabaseMetaData md = conn.getMetaData();
        assertEquals(List.of("parent", "child", "child"), column(md.getPrimaryKeys(null, null, "%"), "TABLE_NAME").stream().sorted(java.util.Comparator.reverseOrder()).toList());
        assertEquals(List.of("child"), column(md.getImportedKeys(null, null, "%"), "FKTABLE_NAME"));
        assertEquals(List.of("child"), column(md.getExportedKeys(null, null, "%"), "FKTABLE_NAME"));
        assertFalse(column(md.getIndexInfo(null, null, "%", false, false), "INDEX_NAME").isEmpty());
    }

    /** The shape of wrangler's d1_migrations table. */
    @Test
    void autoincrementPrimaryKeyIsReported() throws Exception {
        try (Statement s = server.db().createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS d1_migrations");
            s.executeUpdate("CREATE TABLE d1_migrations (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
        DatabaseMetaData md = conn.getMetaData();
        assertEquals(List.of("id"), column(md.getPrimaryKeys(null, null, "d1_migrations"), "COLUMN_NAME"));
        assertEquals(List.of("id"), column(md.getPrimaryKeys("", "", "d1_migrations"), "COLUMN_NAME"));
        assertEquals(List.of("sqlite_autoindex_d1_migrations_1"), column(md.getIndexInfo(null, null, "d1_migrations", true, false), "INDEX_NAME"));
    }

    @Test
    void foreignKeysBothDirectionsResolveImplicitTarget() throws Exception {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getImportedKeys(null, null, "child")) {
            assertTrue(rs.next());
            assertEquals("parent", rs.getString("PKTABLE_NAME"));
            assertEquals("id", rs.getString("PKCOLUMN_NAME"));
            assertEquals("child", rs.getString("FKTABLE_NAME"));
            assertEquals("parent_id", rs.getString("FKCOLUMN_NAME"));
            assertEquals(DatabaseMetaData.importedKeyCascade, rs.getInt("DELETE_RULE"));
        }
        assertEquals(List.of("child"), column(md.getExportedKeys(null, null, "parent"), "FKTABLE_NAME"));
        assertFalse(md.getImportedKeys(null, null, "parent").next());
    }

    @Test
    void indexesIncludeUniqueFlag() throws Exception {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getIndexInfo(null, null, "parent", false, false)) {
            assertTrue(rs.next());
            assertEquals("parent_name", rs.getString("INDEX_NAME"));
            assertFalse(rs.getBoolean("NON_UNIQUE"));
            assertEquals("name", rs.getString("COLUMN_NAME"));
        }
        assertFalse(md.getIndexInfo(null, null, "v", true, false).next());
    }

    @Test
    void closedConnectionRefusesWork() throws Exception {
        conn.close();
        assertTrue(conn.isClosed());
        assertFalse(conn.isValid(1));
        assertThrows(SQLException.class, () -> conn.createStatement());
    }

    @Test
    void transactionsAreRefusedRatherThanFaked() throws Exception {
        assertThrows(SQLException.class, () -> conn.setAutoCommit(false));
        assertTrue(conn.getAutoCommit());
    }
}
