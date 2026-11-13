package io.snello.util;

import io.quarkus.logging.Log;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

import static java.time.format.DateTimeFormatter.*;

public class SqlHelper {

    // static final String ESCAPE = "`";
    private static final Pattern DATETIME = Pattern
            .compile("^\\d{4}-(?:0[0-9]|1[0-2])-[0-9]{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3,9})?(Z|[+-]\\d{2}:\\d{2})?$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-(?:0[0-9]|1[0-2])-[0-9]{2}$");
    private static final Pattern TIME = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}$");
    /** Backup JSON often stores java.sql.Date / Instant as epoch millis. */
    private static final Pattern EPOCH_MILLIS = Pattern.compile("^-?\\d{11,14}$");
    private static final Pattern SHORT_TIME = Pattern.compile("^\\d{1,2}:\\d{2}$");
    private static final Pattern UUID = Pattern
            .compile("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$");
    private static final boolean castUUID = false;
    private static final JsonMapper objectMapper = JsonMapper.builder().build();

    public static void fillStatement(PreparedStatement statement, Map<String, Object> in) throws SQLException {
        if (in == null) {
            in = Collections.emptyMap();
        }
        int i = 0;
        for (Object value : in.values()) {
            bindValue(statement, i + 1, value);
            i++;
        }

    }

    public static void fillStatement(PreparedStatement statement, List<Object> in) throws SQLException {
        if (in == null || in.size() == 0) {
            return;
        }
        int i = 0;
        for (Object value : in) {
            bindValue(statement, i + 1, value);
            i++;
        }
    }

    private static void bindValue(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
            return;
        }
        if (value instanceof String stringValue) {
            if (shouldBindEmptyStringAsNull(statement, index, stringValue)) {
                statement.setObject(index, null);
                return;
            }
            Object casted = optimisticCast(stringValue);
            if (casted instanceof String) {
                casted = castToNumericIfNeeded(statement, index, stringValue);
            }
            // Backup JSON may stringify epoch millis for DATE / TIMESTAMP columns.
            if (casted instanceof String stillString
                    && EPOCH_MILLIS.matcher(stillString).matches()) {
                Object epochCast = castEpochNumberIfNeeded(statement, index, Long.parseLong(stillString));
                if (epochCast instanceof Date || epochCast instanceof Timestamp) {
                    casted = epochCast;
                }
            }
            if (casted instanceof String stillString && bindNativeTyped(statement, index, stillString)) {
                return;
            }
            statement.setObject(index, casted);
            return;
        }
        // Epoch millis from JSON (Jackson Number) into SQL DATE / TIMESTAMP columns (H2 is strict).
        if (value instanceof Number number) {
            statement.setObject(index, castEpochNumberIfNeeded(statement, index, number));
            return;
        }
        // Handle List/ArrayList by converting to JSON string
        if (value instanceof List) {
            try {
                String jsonString = objectMapper.writeValueAsString(value);
                statement.setObject(index, jsonString);
                return;
            } catch (Exception e) {
                Log.warn("Failed to serialize List to JSON: " + e.getMessage());
                statement.setObject(index, value.toString());
                return;
            }
        }
        // Handle Map by converting to JSON string
        if (value instanceof Map) {
            try {
                String jsonString = objectMapper.writeValueAsString(value);
                statement.setObject(index, jsonString);
                return;
            } catch (Exception e) {
                Log.warn("Failed to serialize Map to JSON: " + e.getMessage());
                statement.setObject(index, value.toString());
                return;
            }
        }
        statement.setObject(index, value);
    }

    private static Object castEpochNumberIfNeeded(PreparedStatement statement, int index, Number number) {
        try {
            ParameterMetaData pmd = statement.getParameterMetaData();
            if (pmd == null) {
                return number;
            }
            int paramType = pmd.getParameterType(index);
            long ms = number.longValue();
            if (paramType == Types.DATE) {
                return new Date(ms);
            }
            if (paramType == Types.TIMESTAMP || paramType == Types.TIMESTAMP_WITH_TIMEZONE) {
                return new Timestamp(ms);
            }
        } catch (SQLException | RuntimeException ignored) {
            // fall through
        }
        return number;
    }

    private static boolean shouldBindEmptyStringAsNull(PreparedStatement statement, int index, String value) {
        if (!value.trim().isEmpty()) {
            return false;
        }
        try {
            ParameterMetaData parameterMetaData = statement.getParameterMetaData();
            if (parameterMetaData == null) {
                return false;
            }
            int parameterType = parameterMetaData.getParameterType(index);
            return parameterType == Types.DATE
                    || parameterType == Types.TIMESTAMP
                    || parameterType == Types.TIMESTAMP_WITH_TIMEZONE;
        } catch (SQLException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Binds a row key taken from a URL path ({@code /api/{table}/{key}}): always a String, but the column may be
     * an integer (autoincrement / introspected tables), which PostgreSQL refuses to compare with varchar.
     * Only the numeric cast is applied - never {@link #optimisticCast}, which would turn a slug like
     * {@code 2024-01-01} into a date.
     */
    public static void bindKey(PreparedStatement statement, int index, String key) throws SQLException {
        if (key == null) {
            statement.setObject(index, null);
            return;
        }
        if (bindNativeTyped(statement, index, key)) {
            return;
        }
        statement.setObject(index, castToNumericIfNeeded(statement, index, key));
    }

    /**
     * Columns of a database-specific type (PostgreSQL {@code uuid}, {@code json}, {@code jsonb}, enums...) refuse a
     * varchar parameter: the text is sent untyped and the server converts it. {@code time} columns get the
     * {@code HH:mm} a time picker produces. Tables created by snello only use portable types, so this matters for
     * tables introspected from an existing schema. Returns false when the value was not bound.
     */
    private static boolean bindNativeTyped(PreparedStatement statement, int index, String value) {
        try {
            ParameterMetaData pmd = statement.getParameterMetaData();
            if (pmd == null) {
                return false;
            }
            int type = pmd.getParameterType(index);
            if (type == Types.OTHER) {
                statement.setObject(index, value, Types.OTHER);
                return true;
            }
            if (type == Types.TIME && SHORT_TIME.matcher(value).matches()) {
                statement.setTime(index, Time.valueOf(value.length() == 4 ? "0" + value + ":00" : value + ":00"));
                return true;
            }
        } catch (SQLException | RuntimeException ignore) {
            // unknown parameter type: fall back to the generic binding
        }
        return false;
    }

    private static Object castToNumericIfNeeded(PreparedStatement statement, int index, String value) {
        try {
            ParameterMetaData pmd = statement.getParameterMetaData();
            if (pmd == null) {
                return value;
            }
            int paramType = pmd.getParameterType(index);
            switch (paramType) {
                case Types.TINYINT:
                case Types.SMALLINT:
                case Types.INTEGER:
                case Types.BIGINT:
                case Types.NUMERIC:
                case Types.DECIMAL:
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException ignore) {
                    }
                    try {
                        return new BigDecimal(value);
                    } catch (NumberFormatException ignore) {
                    }
                    break;
                case Types.FLOAT:
                case Types.REAL:
                case Types.DOUBLE:
                    try {
                        return Double.parseDouble(value);
                    } catch (NumberFormatException ignore) {
                    }
                    break;
                default:
                    break;
            }
        } catch (SQLException | RuntimeException ignore) {
        }
        return value;
    }

    public static Object convertSqlValue(Object value) throws SQLException {
        if (value == null) {
            return null;
        }

        // valid json types are just returned as is
        if (value instanceof Boolean || value instanceof String || value instanceof byte[]) {
            return value;
        }

        // numeric values
        if (value instanceof Number) {
            if (value instanceof BigDecimal) {
                BigDecimal d = (BigDecimal) value;
                if (d.scale() == 0) {
                    return ((BigDecimal) value).toBigInteger();
                } else {
                    // we might loose precision here
                    return ((BigDecimal) value).doubleValue();
                }
            }

            return value;
        }

        // temporal values
        if (value instanceof Time) {
            return ((Time) value).toLocalTime().atOffset(ZoneOffset.UTC).format(ISO_LOCAL_TIME);
        }

        if (value instanceof Date) {
            return ((Date) value).toLocalDate().format(ISO_LOCAL_DATE);
        }

        if (value instanceof Timestamp) {
            return OffsetDateTime.ofInstant(((Timestamp) value).toInstant(), ZoneOffset.UTC)
                    .format(ISO_OFFSET_DATE_TIME);
        }

        // large objects
        if (value instanceof Clob) {
            Clob c = (Clob) value;
            try {
                // result might be truncated due to downcasting to int
                return c.getSubString(1, (int) c.length());
            } finally {
                try {
                    c.free();
                } catch (AbstractMethodError | SQLFeatureNotSupportedException e) {
                    // ignore since it is an optional feature since 1.6 and non existing before 1.6
                }
            }
        }

        if (value instanceof Blob) {
            Blob b = (Blob) value;
            try {
                // result might be truncated due to downcasting to int
                return b.getBytes(1, (int) b.length());
            } finally {
                try {
                    b.free();
                } catch (AbstractMethodError | SQLFeatureNotSupportedException e) {
                    // ignore since it is an optional feature since 1.6 and non existing before 1.6
                }
            }
        }

        // // arrays
        // if (value instanceof Array) {
        // Array a = (Array) value;
        // try {
        // Object[] arr = (Object[]) a.getArray();
        // if (arr != null) {
        // JsonArray jsonArray = new JsonArray();
        // for (Object o : arr) {
        // jsonArray.add(convertSqlValue(o));
        // }
        // return jsonArray;
        // }
        // } finally {
        // a.free();
        // }
        // }

        // fallback to String
        return value.toString();
    }

    public static Object optimisticCast(String value) {
        if (value == null) {
            return null;
        }

        try {
            // sql time
            if (TIME.matcher(value).matches()) {
                // convert from local time to instant
                Instant instant = LocalTime.parse(value).atDate(LocalDate.of(1970, 1, 1)).toInstant(ZoneOffset.UTC);
                // calculate the timezone offset in millis
                int offset = TimeZone.getDefault().getOffset(instant.toEpochMilli());
                // need to remove the offset since time has no TZ component
                return new Time(instant.toEpochMilli() - offset);
            }

            // sql date
            if (DATE.matcher(value).matches()) {
                // convert from local date to instant
                Instant instant = LocalDate.parse(value).atTime(LocalTime.of(0, 0, 0, 0)).toInstant(ZoneOffset.UTC);
                // calculate the timezone offset in millis
                int offset = TimeZone.getDefault().getOffset(instant.toEpochMilli());
                // need to remove the offset since time has no TZ component
                return new Date(instant.toEpochMilli() - offset);
            }

            // sql timestamp
            if (DATETIME.matcher(value).matches()) {
                try {
                    Instant instant = OffsetDateTime.parse(value, ISO_OFFSET_DATE_TIME).toInstant();
                    return Timestamp.from(instant);
                } catch (RuntimeException ignored) {
                    Instant instant = LocalDateTime.parse(value, ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);
                    return Timestamp.from(instant);
                }
            }

            // sql uuid
            if (castUUID && UUID.matcher(value).matches()) {
                return java.util.UUID.fromString(value);
            }

            if ("true".equals(value)) {
                return Boolean.TRUE;
            }

            if ("false".equals(value)) {
                return Boolean.FALSE;
            }

        } catch (RuntimeException e) {
            Log.info(e.getCause());
        }

        // unknown
        return value;
    }

}
