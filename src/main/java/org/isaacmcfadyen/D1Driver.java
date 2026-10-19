package org.isaacmcfadyen;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * URL: {@code jdbc:d1://<database-id>[?endpoint=<api base url>]}. User is the Cloudflare account ID,
 * password is an API token with the D1 Edit permission. {@code accountId}/{@code apiToken} properties also work.
 */
public class D1Driver implements Driver {
    static final String PREFIX = "jdbc:d1:";

    static {
        try {
            DriverManager.registerDriver(new D1Driver());
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;
        try {
            return open(url, info);
        } catch (IllegalArgumentException e) { // e.g. a malformed %-escape in the URL
            throw new SQLException("Invalid connection setting: " + e.getMessage(), "08001", e);
        }
    }

    private Connection open(String url, Properties info) throws SQLException {
        String rest = url.substring(PREFIX.length());
        if (rest.startsWith("//")) rest = rest.substring(2);
        String query = "";
        int q = rest.indexOf('?');
        if (q >= 0) {
            query = rest.substring(q + 1);
            rest = rest.substring(0, q);
        }
        String databaseId = rest.replaceAll("/+$", "");
        Properties props = new Properties();
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            props.setProperty(key, eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        if (info != null) info.forEach((k, v) -> props.setProperty(String.valueOf(k), String.valueOf(v)));

        String accountId = first(props, "user", "accountId");
        String token = first(props, "password", "apiToken");
        if (databaseId.isEmpty()) throw new SQLException("Missing database ID in URL, expected jdbc:d1://<database-id>", "08001");
        if (accountId == null) throw new SQLException("Missing Cloudflare Account ID (set it as the user name)", "28000");
        if (token == null) throw new SQLException("Missing Cloudflare API token (set it as the password)", "28000");
        String endpoint = props.getProperty("endpoint", D1Client.DEFAULT_ENDPOINT);
        D1Client client = new D1Client(endpoint, accountId, databaseId, token, props.getProperty("debug", System.getProperty("d1.debug")));
        return new D1Connection(client, redact(PREFIX + "//" + databaseId, query), accountId);
    }

    /** The URL without credential parameters: DatabaseMetaData.getURL() is shown and stored by tools. */
    static String redact(String base, String query) {
        List<String> kept = new ArrayList<>();
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) continue;
            String key = URLDecoder.decode(pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair, StandardCharsets.UTF_8);
            if (!key.equals("user") && !key.equals("password") && !key.equals("accountId") && !key.equals("apiToken")) kept.add(pair);
        }
        return kept.isEmpty() ? base : base + "?" + String.join("&", kept);
    }

    private static String first(Properties p, String... keys) {
        for (String k : keys) {
            String v = p.getProperty(k);
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith(PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        DriverPropertyInfo endpoint = new DriverPropertyInfo("endpoint", info == null ? null : info.getProperty("endpoint"));
        endpoint.description = "API base URL (default " + D1Client.DEFAULT_ENDPOINT + "); https only, except for localhost";
        DriverPropertyInfo debug = new DriverPropertyInfo("debug", info == null ? null : info.getProperty("debug"));
        debug.description = "A .log file (owner-only) that receives every statement (SQL text, including literals and BLOB parameters; no credentials) and a result summary";
        return new DriverPropertyInfo[]{endpoint, debug};
    }

    @Override
    public int getMajorVersion() {
        return 2;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
}
