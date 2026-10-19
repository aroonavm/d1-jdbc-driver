package org.isaacmcfadyen;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * Stand-in for the D1 REST `/raw` endpoint, backed by a real SQLite database (sqlite-jdbc), so tests run
 * genuine SQL. Also runnable by hand: {@code FakeD1Server <port> <sqlite-file> <account> <token>}.
 */
public final class FakeD1Server implements AutoCloseable {
    public static final String ACCOUNT = "acct";
    public static final String TOKEN = "secret";
    public static final String DATABASE = "11111111-2222-3333-4444-555555555555";

    private final HttpServer server;
    /** HTTP statuses to answer the next requests with, without running their SQL. */
    public final java.util.Deque<Integer> failNext = new java.util.concurrent.ConcurrentLinkedDeque<>();
    /** Answer the next request with HTTP 200 and a body that does not have D1's shape. */
    public volatile boolean malformedNext;
    public volatile String lastSql;
    public final java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
    private final Connection db;
    private final String account;
    private final String token;

    public FakeD1Server(int port, String sqliteUrl, String account, String token) throws IOException, SQLException {
        this.account = account;
        this.token = token;
        this.db = DriverManager.getConnection(sqliteUrl);
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/client/v4/accounts/", ex -> {
            String path = ex.getRequestURI().getPath();
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            JSONObject out;
            int status = 200;
            requests.incrementAndGet();
            Integer injected = failNext.poll();
            if (injected != null) {
                status = injected;
                out = failure(10001, "injected " + injected);
            } else if (malformedNext) {
                malformedNext = false;
                out = new JSONObject().put("success", true).put("result", "not an array");
            } else if (!("Bearer " + this.token).equals(auth)) {
                status = 403;
                out = failure(10000, "Authentication error");
            } else if (!path.startsWith("/client/v4/accounts/" + this.account + "/d1/database/") || !path.endsWith("/raw")) {
                status = 404;
                out = failure(7404, "not found");
            } else {
                JSONObject body = new JSONObject(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try {
                    synchronized (db) {
                        if (!body.getString("sql").toLowerCase().startsWith("pragma")) lastSql = body.getString("sql"); // the driver's own metadata lookups do not count
                        out = new JSONObject().put("success", true).put("errors", new JSONArray()).put("result", runAll(body));
                    }
                } catch (SQLException e) {
                    status = 400;
                    out = failure(7500, "D1_ERROR: " + e.getMessage());
                }
            }
            byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    private static JSONObject failure(int code, String message) {
        return new JSONObject().put("success", false).put("result", new JSONArray())
                .put("errors", new JSONArray().put(new JSONObject().put("code", code).put("message", message)));
    }

    /** D1 accepts several statements in one request and answers with one result each; parameters belong to a single statement. */
    private JSONArray runAll(JSONObject body) throws SQLException {
        String all = body.getString("sql");
        JSONArray out = new JSONArray();
        if (!all.contains(";") || body.has("params")) return out.put(run(body));
        for (String one : all.split(";")) {
            if (!one.isBlank()) out.put(run(new JSONObject().put("sql", one.trim())));
        }
        return out;
    }

    private JSONObject run(JSONObject body) throws SQLException {
        String sql = body.getString("sql");
        // Real D1 rejects the pragma_*() table-valued functions (SQLITE_AUTH); only PRAGMA statements work.
        if (sql.toLowerCase().contains("_cf_kv") && sql.toLowerCase().startsWith("pragma")) throw new SQLException("not authorized: SQLITE_AUTH");
        if (sql.toLowerCase().contains("pragma_")) throw new SQLException("not authorized: SQLITE_AUTH");
        if (sql.trim().toLowerCase().startsWith("pragma database_list")) throw new SQLException("SQLITE_AUTH: not authorized");
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            JSONArray params = body.optJSONArray("params");
            for (int i = 0; params != null && i < params.length(); i++) {
                Object p = params.get(i);
                // D1 parses the request in JavaScript: integers beyond 2^53 are rounded to the nearest double, as here
                if (p instanceof Number n && !(p instanceof Double) && Math.abs(n.doubleValue()) > 9007199254740991d) p = (long) n.doubleValue();
                ps.setObject(i + 1, p == JSONObject.NULL ? null : p);
            }
            JSONArray columns = new JSONArray();
            JSONArray rows = new JSONArray();
            long changes = 0;
            long lastId = 0;
            if (ps.execute()) {
                try (ResultSet rs = ps.getResultSet()) {
                    ResultSetMetaData md = rs.getMetaData();
                    for (int c = 1; c <= md.getColumnCount(); c++) columns.put(md.getColumnLabel(c));
                    while (rs.next()) {
                        JSONArray row = new JSONArray();
                        for (int c = 1; c <= md.getColumnCount(); c++) {
                            Object v = rs.getObject(c);
                            if (v instanceof byte[] b) {
                                JSONArray blob = new JSONArray();
                                for (byte x : b) blob.put(x & 0xff);
                                row.put(blob);
                            } else row.put(v == null ? JSONObject.NULL : v);
                        }
                        rows.put(row);
                    }
                }
            } else {
                changes = ps.getUpdateCount();
                try (java.sql.Statement s = db.createStatement(); ResultSet rs = s.executeQuery("SELECT last_insert_rowid()")) {
                    lastId = rs.next() ? rs.getLong(1) : 0;
                }
            }
            return new JSONObject().put("success", true)
                    .put("results", new JSONObject().put("columns", columns).put("rows", rows))
                    .put("meta", new JSONObject().put("changes", changes).put("last_row_id", lastId));
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String url() {
        return "jdbc:d1://" + DATABASE + "?endpoint=http://127.0.0.1:" + port() + "/client/v4";
    }

    public Connection db() {
        return db;
    }

    @Override
    public void close() throws SQLException {
        server.stop(0);
        db.close();
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        FakeD1Server s = new FakeD1Server(port, "jdbc:sqlite:" + args[1], args[2], args[3]);
        System.out.println("Fake D1 listening: jdbc:d1://" + DATABASE + "?endpoint=http://127.0.0.1:" + s.port() + "/client/v4");
        Thread.currentThread().join();
    }
}
