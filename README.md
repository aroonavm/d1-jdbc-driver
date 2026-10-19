> **Experimental.** This is a fork of [isaac-mcfadyen/d1-jdbc-driver](https://github.com/isaac-mcfadyen/d1-jdbc-driver), made because I ran into
> multiple issues using the original (no grid editing in DBeaver, failing introspection, missing prepared statements). It works for me
> against my own D1 databases in DBeaver, but it needs a lot more testing. Developers should try it carefully first, on a copy or a
> staging database and preferably with a read-only token, before pointing it at anything that matters.

# D1 JDBC Driver

A JDBC driver for [Cloudflare D1](https://developers.cloudflare.com/d1/), usable from DBeaver, DataGrip and other JDBC tools.

- Result columns come from D1's `/raw` endpoint, so any `SELECT` works (column subsets, `DISTINCT`, joins with duplicate names, expressions, empty results).
- Real `PreparedStatement` (parameters, batches, generated keys, BLOB parameters), so editing rows in a tool's table grid works.
- Introspection: tables, views, columns (types, sizes, nullability), primary keys, foreign keys (both directions), indexes.
- Clear errors: wrong token, account or database ID fail when connecting, with D1's own message.

## Limitations

- `setMaxRows` (what DBeaver uses for paging) is pushed down as `LIMIT n` for plain SELECTs; other statements return their whole result.

- No transactions across requests (the REST API has none); `setAutoCommit(false)` is refused. Batches run statement by statement.
- A query returns its whole result in one response; use `LIMIT` on large tables.
- Tables without a primary key cannot be edited in a grid (D1 exposes no row identity to the driver).
- Dates are read from ISO-8601 text or epoch numbers and shown as UTC. Values written back from a date editor are ISO-8601 text, so
  editing a date column that stores epoch numbers changes its storage format; declare such columns `INTEGER` to edit them as numbers.
  Julian-day numbers (`julianday()`) are not recognised as dates. In a time zone with daylight saving, a stored time that falls in the
  local clock change hour cannot be represented by `java.sql.Timestamp` and shifts by an hour if edited.
- D1's API carries numbers as JavaScript numbers, exact only up to 2^53 (9007199254740991). The driver sends larger integer parameters as
  text, which SQLite stores and compares exactly in INTEGER columns, but larger integers in results arrive rounded; select them with
  `CAST(col AS TEXT)` to see the exact value. (Into a column declared without a type, such a parameter is stored as text.)
- A read-only connection (`Connection.setReadOnly(true)`, DBeaver's "Read-only connection") refuses everything except SELECT, VALUES,
  EXPLAIN and reading PRAGMAs.

## Install

1. Build with `mvn package` (JDK 17+). `target/d1-jdbc-driver-<version>.jar` contains everything it needs.
2. Add a driver in your tool: class `org.isaacmcfadyen.D1Driver`, URL template `jdbc:d1://{database}`.
   In DataGrip set the SQL dialect to SQLite. In DBeaver use the *Generic* provider (Database > Driver Manager > New).
3. Connect: URL `jdbc:d1://<database-id>`, user = Cloudflare **Account ID**, password = an [API token](https://dash.cloudflare.com/profile/api-tokens) with the **D1 Edit** permission.

Optional URL parameters (or driver properties): `debug=<file.log>` appends every statement (SQL text, including any literal values, but no bound parameter values or credentials) and a result summary to that file; `endpoint=<api base url>` (default `https://api.cloudflare.com/client/v4`); `accountId` and `apiToken` may be given instead of user/password.

## Source layout

`src/main/java/org/isaacmcfadyen/base/Unsupported*.java` are boilerplate derived from the `java.sql` interfaces: every method throws
`SQLFeatureNotSupportedException` (or returns a neutral default for `DatabaseMetaData` capability flags). The driver classes extend them
and override what D1 supports. Update them if the JDBC interfaces change; put driver behaviour in the subclasses, not here.

## Tests

`mvn test` runs the driver against `FakeD1Server` (src/test), an HTTP server that speaks D1's `/raw` protocol on top of a real SQLite database. It can also be run by hand to try a tool without touching a real database:

    java -cp <test-classpath> org.isaacmcfadyen.FakeD1Server 18787 /tmp/fake.db acct secret

## Credits

Ideas reused from open pull requests #7/#13/#14 (SQLite-compatible product name, local answers for the PRAGMAs D1 forbids, hiding `_cf_KV`, prepared statements and key metadata for grid editing).
