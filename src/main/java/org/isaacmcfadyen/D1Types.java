package org.isaacmcfadyen;

import java.sql.Types;
import java.util.Locale;

/** Maps SQLite declared types and runtime values to java.sql.Types. */
final class D1Types {
    private D1Types() {}

    /** SQLite type name of a runtime value (what typeof() would say, in declared-type spelling). */
    static String nameOf(Object v) {
        if (v instanceof Long) return "INTEGER";
        if (v instanceof Double) return "REAL";
        if (v instanceof byte[]) return "BLOB";
        return "TEXT";
    }

    /** JDBC type of a declared column type, following SQLite's affinity rules with a few friendly extras. */
    static int sqlType(String declared) {
        String t = declared == null ? "" : declared.toUpperCase(Locale.ROOT).replaceAll("\\(.*", "").trim();
        switch (t) {
            case "BOOLEAN": case "BOOL": return Types.BOOLEAN;
            case "DATE": return Types.DATE;
            case "TIME": return Types.TIME;
            case "DATETIME": case "TIMESTAMP": return Types.TIMESTAMP;
            case "TINYINT": return Types.TINYINT;
            case "SMALLINT": return Types.SMALLINT;
            case "MEDIUMINT": return Types.INTEGER;
            case "INT": case "INTEGER": case "BIGINT": case "UNSIGNED BIG INT": case "INT8": return Types.BIGINT;
            case "FLOAT": return Types.FLOAT;
            case "DOUBLE": case "DOUBLE PRECISION": return Types.DOUBLE;
            case "REAL": return Types.REAL;
            case "DECIMAL": return Types.DECIMAL;
            case "NUMERIC": return Types.NUMERIC;
            case "TEXT": case "CLOB": return Types.VARCHAR;
            case "BLOB": return Types.BLOB;
            case "JSON": return Types.VARCHAR;
            default: break;
        }
        if (t.contains("INT")) return Types.BIGINT;
        if (t.contains("CHAR") || t.contains("CLOB") || t.contains("TEXT")) return Types.VARCHAR;
        if (t.isEmpty() || t.contains("BLOB")) return t.isEmpty() ? Types.OTHER : Types.BLOB;
        if (t.contains("REAL") || t.contains("FLOA") || t.contains("DOUB")) return Types.DOUBLE;
        return Types.NUMERIC;
    }

    static String className(int sqlType) {
        switch (sqlType) {
            case Types.BIGINT: return Long.class.getName();
            case Types.INTEGER: case Types.SMALLINT: case Types.TINYINT: return Integer.class.getName();
            case Types.REAL: case Types.FLOAT: case Types.DOUBLE: return Double.class.getName();
            case Types.NUMERIC: case Types.DECIMAL: return java.math.BigDecimal.class.getName();
            case Types.BOOLEAN: return Boolean.class.getName();
            case Types.DATE: return java.sql.Date.class.getName();
            case Types.TIME: return java.sql.Time.class.getName();
            case Types.TIMESTAMP: return java.sql.Timestamp.class.getName();
            case Types.BLOB: case Types.VARBINARY: case Types.BINARY: return "[B";
            default: return String.class.getName();
        }
    }
}
