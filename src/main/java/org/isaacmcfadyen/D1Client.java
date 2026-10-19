package org.isaacmcfadyen;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Talks to the D1 REST API (`/raw` endpoint) on behalf of one connection. */
final class D1Client {
    static final String DEFAULT_ENDPOINT = "https://api.cloudflare.com/client/v4";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final int MAX_ATTEMPTS = 3;

    private final String queryUrl;
    private final String token;
    private final java.nio.file.Path debugLog;

    /** @param debugLog file that receives every statement, its parameter count and a result summary; null disables logging. */
    D1Client(String endpoint, String accountId, String databaseId, String token, String debugLog) throws SQLException {
        for (String id : new String[]{accountId, databaseId}) {
            if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw new SQLException("Invalid account or database ID: only letters, digits, '-' and '_' are allowed", "08001");
        }
        URI base;
        try {
            base = URI.create(endpoint.trim());
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid endpoint URL: " + e.getMessage(), "08001");
        }
        String host = base.getHost() == null ? "" : base.getHost();
        if (host.isEmpty()) throw new SQLException("Invalid endpoint URL: no host in '" + endpoint + "'", "08001");
        boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
        if (!"https".equals(base.getScheme()) && !("http".equals(base.getScheme()) && local)) {
            throw new SQLException("The endpoint must use https (plain http is only allowed for localhost): the API token is sent with every request", "08001");
        }
        this.queryUrl = endpoint.trim().replaceAll("/+$", "") + "/accounts/" + accountId + "/d1/database/" + databaseId + "/raw";
        this.token = token;
        if (debugLog != null && !debugLog.isBlank() && !debugLog.trim().endsWith(".log")) {
            // a connection URL must not be able to append SQL text to an arbitrary file (a shell profile, say)
            throw new SQLException("The debug property must name a .log file", "08001");
        }
        this.debugLog = debugLog == null || debugLog.isBlank() ? null : java.nio.file.Path.of(debugLog.trim());
    }

    private void log(String line) {
        if (debugLog == null) return;
        try {
            if (!java.nio.file.Files.exists(debugLog)) { // the log holds SQL and data: owner-only from the start
                try {
                    java.nio.file.Files.createFile(debugLog, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                            java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
                } catch (UnsupportedOperationException | java.nio.file.FileAlreadyExistsException ignored) {
                    // non-POSIX file system, or created meanwhile: the append below creates it
                }
            }
            java.nio.file.Files.writeString(debugLog, java.time.LocalTime.now() + " " + line + "\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // logging must never break a query
        }
    }

    /** A request in flight, so that Statement.cancel() can abort it. */
    static final class Call {
        private volatile CompletableFuture<?> future;
        private volatile boolean cancelled;

        void cancel() {
            cancelled = true;
            CompletableFuture<?> f = future;
            if (f != null) f.cancel(true);
        }
    }

    List<D1Result> execute(String sql, List<Object> params, int timeoutSeconds, Call call) throws SQLException {
        try {
            List<D1Result> results = executeUnlogged(sql, params, timeoutSeconds, call);
            StringBuilder summary = new StringBuilder();
            for (D1Result r : results) summary.append(" [cols=").append(r.columns).append(" rows=").append(r.rows.size()).append(" changes=").append(r.changes).append(']');
            log("OK    " + sql.replace('\n', ' ') + " params=" + params.size() + summary);
            return results;
        } catch (SQLException e) {
            log("ERROR " + sql.replace('\n', ' ') + " params=" + params.size() + " -> " + e.getMessage());
            throw e;
        }
    }

    private List<D1Result> executeUnlogged(String sql, List<Object> params, int timeoutSeconds, Call call) throws SQLException {
        List<D1Result> mocked = mock(sql);
        if (mocked != null) return mocked;
        JSONObject body;
        try {
            body = new JSONObject().put("sql", sql);
            if (!params.isEmpty()) {
                JSONArray array = new JSONArray();
                for (Object p : params) array.put(p == null ? JSONObject.NULL : p);
                body.put("params", array);
            }
        } catch (JSONException e) {
            throw new SQLException("Cannot encode the statement parameters: " + e.getMessage(), "22023");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(queryUrl))
                .timeout(Duration.ofSeconds(timeoutSeconds > 0 ? timeoutSeconds : 120))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response = send(request, call);
            int status = response.statusCode();
            // 429 means D1 refused before running anything. A 5xx may arrive after a write was applied, so only reads are retried then.
            boolean transientFailure = status == 429 || (status == 502 || status == 503 || status == 504) && SqlScanner.isSingleRead(sql);
            if (transientFailure && attempt < MAX_ATTEMPTS && !call.cancelled) {
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted while retrying a D1 request", e);
                }
                continue;
            }
            return parse(status, response.body());
        }
    }

    /**
     * D1 rejects a few introspection PRAGMAs that SQLite clients issue on connect; answer them locally
     * (approach taken from isaac-mcfadyen/d1-jdbc-driver PR #7/#13).
     */
    static List<D1Result> mock(String sql) {
        String s = sql.trim().replaceAll(";+$", "").trim().toLowerCase(java.util.Locale.ROOT);
        if (s.equals("pragma database_list")) {
            return List.of(new D1Result(List.of("seq", "name", "file"), List.<Object[]>of(new Object[]{0L, "main", ""}), 0, 0));
        }
        if (s.equals("pragma collation_list")) {
            List<Object[]> rows = new ArrayList<>();
            String[] names = {"RTRIM", "NOCASE", "BINARY"};
            for (int i = 0; i < names.length; i++) rows.add(new Object[]{(long) i, names[i]});
            return List.of(new D1Result(List.of("seq", "name"), rows, 0, 0));
        }
        return null;
    }

    private static HttpResponse<String> send(HttpRequest request, Call call) throws SQLException {
        CompletableFuture<HttpResponse<String>> future = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        call.future = future;
        try {
            return future.get(request.timeout().orElse(Duration.ofSeconds(120)).toMillis() + 5000, TimeUnit.MILLISECONDS);
        } catch (CancellationException e) {
            throw new SQLException("Statement cancelled", "70100");
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new SQLException("D1 request timed out", "HYT00");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for D1", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof IOException && call.cancelled) throw new SQLException("Statement cancelled", "70100");
            throw new SQLException("Cannot reach the D1 API: " + cause.getMessage(), "08001", cause);
        }
    }

    static List<D1Result> parse(int status, String text) throws SQLException {
        try {
            return parseResponse(status, text);
        } catch (JSONException e) {
            throw new SQLException("Unexpected response from the D1 API (HTTP " + status + "): " + e.getMessage(), "HY000");
        }
    }

    private static List<D1Result> parseResponse(int status, String text) throws SQLException {
        JSONObject json;
        try {
            json = new JSONObject(text);
        } catch (JSONException e) {
            throw new SQLException("D1 API returned HTTP " + status + " with a non-JSON body", status == 401 || status == 403 ? "28000" : "HY000");
        }
        if (status != 200 || !json.optBoolean("success", false)) {
            throw error(status, json);
        }
        JSONArray result = json.getJSONArray("result");
        List<D1Result> out = new ArrayList<>(result.length());
        for (int i = 0; i < result.length(); i++) {
            JSONObject statement = result.getJSONObject(i);
            if (!statement.optBoolean("success", true)) throw error(status, statement);
            JSONObject meta = statement.optJSONObject("meta");
            long changes = meta == null ? 0 : meta.optLong("changes", 0);
            long lastRowId = meta == null ? 0 : meta.optLong("last_row_id", 0);
            JSONObject results = statement.optJSONObject("results");
            List<String> columns = new ArrayList<>();
            List<Object[]> rows = new ArrayList<>();
            if (results != null) {
                JSONArray cols = results.optJSONArray("columns");
                for (int c = 0; cols != null && c < cols.length(); c++) columns.add(cols.getString(c));
                JSONArray data = results.optJSONArray("rows");
                for (int r = 0; data != null && r < data.length(); r++) {
                    JSONArray row = data.getJSONArray(r);
                    Object[] values = new Object[row.length()];
                    for (int c = 0; c < values.length; c++) values[c] = normalize(row.get(c));
                    rows.add(values);
                }
            }
            out.add(new D1Result(columns, rows, changes, lastRowId));
        }
        return out;
    }

    private static SQLException error(int status, JSONObject json) {
        JSONArray errors = json.optJSONArray("errors");
        StringBuilder message = new StringBuilder();
        int code = 0;
        for (int i = 0; errors != null && i < errors.length(); i++) {
            JSONObject e = errors.optJSONObject(i);
            if (e == null) continue;
            if (message.length() > 0) message.append("; ");
            message.append(e.optString("message", "unknown error"));
            if (code == 0) code = e.optInt("code", 0);
        }
        if (message.length() == 0) message.append("D1 API request failed");
        String state = status == 401 || status == 403 ? "28000" : status == 404 ? "3D000" : "HY000";
        if (status == 401 || status == 403) message.append(" (check the Account ID and that the API token has the D1 Edit permission)");
        if (status == 404) message.append(" (check the database ID and Account ID)");
        return new SQLException(message.toString(), state, code);
    }

    /** JSON value to the Java value JDBC getters work from: Long, Double, String, byte[] or null. */
    static Object normalize(Object v) {
        if (v == null || v == JSONObject.NULL) return null;
        if (v instanceof Boolean b) return b ? 1L : 0L;
        if (v instanceof Integer || v instanceof Long || v instanceof Short) return ((Number) v).longValue();
        if (v instanceof BigInteger big) return big.bitLength() < 64 ? (Object) big.longValue() : big.toString();
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof JSONArray blob) {
            byte[] bytes = new byte[blob.length()];
            for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) blob.getInt(i);
            return bytes;
        }
        return v;
    }
}
