package io.snello.cms.util;

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

    private static final Pattern DATETIME = Pattern.compile("^\\d{4}-(?:0[0-9]|1[0-2])-[0-9]{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3,9})?Z$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-(?:0[0-9]|1[0-2])-[0-9]{2}$");
    private static final Pattern TIME = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}$");
    private static final Pattern UUID = Pattern.compile("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$");
    private static final boolean castUUID = false;


    public static void fillStatement(PreparedStatement statement, Map<String, Object> in) throws SQLException {
        if (in == null) {
            in = Collections.emptyMap();
        }
        int i = 0;
        for (Object value : in.values()) {
            if (value != null) {
                if (value instanceof String) {
                    statement.setObject(i + 1, optimisticCast((String) value));
                } else {
                    statement.setObject(i + 1, value);
                }
            } else {
                statement.setObject(i + 1, null);
            }
            i++;
        }

    }

    public static void fillStatement(PreparedStatement statement, List<Object> in) throws SQLException {
        if (in == null || in.size() == 0) {
            return;
        }
        int i = 0;
        for (Object value : in) {
            if (value != null) {
                if (value instanceof String) {
                    statement.setObject(i + 1, optimisticCast((String) value));
                } else {
                    statement.setObject(i + 1, value);
                }
            } else {
                statement.setObject(i + 1, null);
            }
            i++;
        }
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
            return OffsetDateTime.ofInstant(((Timestamp) value).toInstant(), ZoneOffset.UTC).format(ISO_OFFSET_DATE_TIME);
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

//        // arrays
//        if (value instanceof Array) {
//            Array a = (Array) value;
//            try {
//                Object[] arr = (Object[]) a.getArray();
//                if (arr != null) {
//                    JsonArray jsonArray = new JsonArray();
//                    for (Object o : arr) {
//                        jsonArray.add(convertSqlValue(o));
//                    }
//                    return jsonArray;
//                }
//            } finally {
//                a.free();
//            }
//        }

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
                Instant instant = Instant.from(ISO_INSTANT.parse(value));
                return Timestamp.from(instant);
            }

            // sql uuid
            if (castUUID && UUID.matcher(value).matches()) {
                return java.util.UUID.fromString(value);
            }

//            if ("true".equals(value)) {
//                return Boolean.TRUE;
//            }
//
//            if ("false".equals(value)) {
//                return Boolean.FALSE;
//            }

        } catch (RuntimeException e) {
            System.out.println(e.getCause());
        }

        // unknown
        return value;
    }
}
