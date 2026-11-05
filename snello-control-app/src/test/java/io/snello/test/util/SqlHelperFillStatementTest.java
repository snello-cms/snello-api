package io.snello.test.util;

import io.snello.util.SqlHelper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class SqlHelperFillStatementTest {

    @Test
    void shouldBindEmptyStringAsNullForDateParameters() throws Exception {
        RecordingPreparedStatement preparedStatement = new RecordingPreparedStatement(Types.DATE);

        SqlHelper.fillStatement(preparedStatement.proxy(), List.of(""));

        assertNull(preparedStatement.values().getFirst());
    }

    @Test
    void shouldBindEmptyStringAsNullForTimestampParameters() throws Exception {
        RecordingPreparedStatement preparedStatement = new RecordingPreparedStatement(Types.TIMESTAMP_WITH_TIMEZONE);

        SqlHelper.fillStatement(preparedStatement.proxy(), List.of("   "));

        assertNull(preparedStatement.values().getFirst());
    }

    @Test
    void shouldKeepEmptyStringForNonTemporalParameters() throws Exception {
        RecordingPreparedStatement preparedStatement = new RecordingPreparedStatement(Types.VARCHAR);

        SqlHelper.fillStatement(preparedStatement.proxy(), List.of(""));

        assertEquals("", preparedStatement.values().getFirst());
    }

    @Test
    void shouldCastStringToLongForNumericIntegerParameter() throws Exception {
        RecordingPreparedStatement preparedStatement = new RecordingPreparedStatement(Types.NUMERIC);

        SqlHelper.fillStatement(preparedStatement.proxy(), List.of("5"));

        Object bound = preparedStatement.values().getFirst();
        assertInstanceOf(Long.class, bound);
        assertEquals(5L, bound);
    }

    @Test
    void shouldCastStringToBigDecimalForNumericDecimalParameter() throws Exception {
        RecordingPreparedStatement preparedStatement = new RecordingPreparedStatement(Types.DECIMAL);

        SqlHelper.fillStatement(preparedStatement.proxy(), List.of("3.14"));

        Object bound = preparedStatement.values().getFirst();
        assertInstanceOf(BigDecimal.class, bound);
        assertEquals(new BigDecimal("3.14"), bound);
    }

    @Test
    void shouldCastStringToLongForIntegerParameter() throws Exception {
        RecordingPreparedStatement preparedStatement = new RecordingPreparedStatement(Types.INTEGER);

        SqlHelper.fillStatement(preparedStatement.proxy(), List.of("42"));

        Object bound = preparedStatement.values().getFirst();
        assertInstanceOf(Long.class, bound);
        assertEquals(42L, bound);
    }

    @Test
    void shouldCastStringToDoubleForDoubleParameter() throws Exception {
        RecordingPreparedStatement preparedStatement = new RecordingPreparedStatement(Types.DOUBLE);

        SqlHelper.fillStatement(preparedStatement.proxy(), List.of("2.71"));

        Object bound = preparedStatement.values().getFirst();
        assertInstanceOf(Double.class, bound);
        assertEquals(2.71, bound);
    }

    @Test
    void shouldKeepStringForVarcharParameterWithNumericValue() throws Exception {
        RecordingPreparedStatement preparedStatement = new RecordingPreparedStatement(Types.VARCHAR);

        SqlHelper.fillStatement(preparedStatement.proxy(), List.of("5"));

        assertEquals("5", preparedStatement.values().getFirst());
    }

    private static final class RecordingPreparedStatement {
        private final List<Object> values = new ArrayList<>();
        private final PreparedStatement proxy;

        private RecordingPreparedStatement(int parameterType) {
            this.proxy = createPreparedStatementProxy(values, parameterType);
        }

        private static PreparedStatement createPreparedStatementProxy(List<Object> values, int parameterType) {
            ParameterMetaData parameterMetaData = (ParameterMetaData) Proxy.newProxyInstance(
                    ParameterMetaData.class.getClassLoader(),
                    new Class[]{ParameterMetaData.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getParameterType" -> parameterType;
                        case "getParameterCount" -> 1;
                        case "isNullable" -> ParameterMetaData.parameterNullableUnknown;
                        case "isSigned" -> false;
                        case "getPrecision", "getScale" -> 0;
                        case "getParameterTypeName", "getParameterClassName" -> null;
                        case "getParameterMode" -> ParameterMetaData.parameterModeIn;
                        case "unwrap" -> null;
                        case "isWrapperFor" -> false;
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );

            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class[]{PreparedStatement.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setObject" -> {
                            int index = (Integer) args[0];
                            while (values.size() < index) {
                                values.add(null);
                            }
                            values.set(index - 1, args[1]);
                            yield null;
                        }
                        case "getParameterMetaData" -> parameterMetaData;
                        case "unwrap" -> null;
                        case "isWrapperFor" -> false;
                        case "toString" -> "RecordingPreparedStatement";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private List<Object> values() {
            return values;
        }

        private PreparedStatement proxy() {
            return proxy;
        }
    }
}