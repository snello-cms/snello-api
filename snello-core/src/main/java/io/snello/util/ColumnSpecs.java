package io.snello.util;

import io.quarkus.logging.Log;
import io.snello.model.Condition;
import io.snello.model.FieldDefinition;
import io.snello.model.entity.FieldModel;
import io.snello.model.enums.InputType;
import io.snello.model.enums.WidgetType;
import io.snello.model.facet.EntityQueryFacet;
import io.snello.model.facet.FieldQueryFacet;
import io.snello.model.facet.FieldUiFacet;
import io.snello.model.pojo.ColumnKind;
import io.snello.model.pojo.ColumnSpec;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single bridge from facets to storage: turns a field into a {@link ColumnSpec}.
 * <p>
 * Resolution order: {@code query.sql_definition()} (raw, wins) → UI widget {@code type}/{@code input_type} (how every
 * existing row was authored) → {@code query.sql_type()}. Dialects never look at UI fields.
 */
public final class ColumnSpecs {

    /** Default VARCHAR length for generated {@code uuid}/{@code slug} key columns. */
    public static final int DEFAULT_KEY_VARCHAR = 50;

    /** Cap for slug keys sourced from unbounded {@link ColumnKind#LONG_TEXT} fields (TEXT cannot be a practical PK). */
    public static final int LONG_TEXT_KEY_VARCHAR = 2048;

    private static final Pattern VARCHAR_LEN =
            Pattern.compile("(?i)varchar(?:_ignorecase)?\\s*\\(\\s*(\\d+)\\s*\\)");

    private ColumnSpecs() {
    }

    /**
     * Declared varchar capacity of a column kind (the length used in dialect {@code columnSql}).
     * Returns {@code -1} when the kind is not a bounded varchar.
     */
    public static int varcharCapacity(ColumnKind kind) {
        if (kind == null) {
            return -1;
        }
        return switch (kind) {
            case SHORT_TEXT -> 200;
            case MEDIUM_TEXT -> 2048;
            case LONG_TEXT -> LONG_TEXT_KEY_VARCHAR;
            default -> -1;
        };
    }

    /**
     * Effective varchar capacity of a column: parses {@code VARCHAR(n)} from {@code sql_definition} when present,
     * otherwise uses {@link #varcharCapacity(ColumnKind)}.
     */
    public static int varcharCapacity(ColumnSpec column) {
        if (column == null) {
            return -1;
        }
        if (column.sqlDefinition() != null && !column.sqlDefinition().isBlank()) {
            Matcher m = VARCHAR_LEN.matcher(column.sqlDefinition());
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return varcharCapacity(column.kind());
    }

    /**
     * VARCHAR length for the generated key column. For {@code slug} keys, grows to at least the slug-source
     * field capacity when that field is wider than {@link #DEFAULT_KEY_VARCHAR}.
     */
    public static int resolveKeyVarcharLength(EntityQueryFacet query, List<ColumnSpec> columns) {
        int length = DEFAULT_KEY_VARCHAR;
        if (query == null || !"slug".equals(query.table_key_type())) {
            return length;
        }
        String source = query.table_key_addition();
        if (source == null || source.isBlank() || columns == null) {
            return length;
        }
        for (ColumnSpec column : columns) {
            if (column == null || column.name() == null || !source.equalsIgnoreCase(column.name())) {
                continue;
            }
            int capacity = varcharCapacity(column);
            if (capacity > length) {
                length = capacity;
            }
            break;
        }
        return length;
    }

    public static ColumnSpec of(FieldDefinition fd) {
        return of(fd.name, fd.queryFacet(), fd.uiFacet());
    }

    public static ColumnSpec of(FieldModel model) {
        return of(model.name, model.query, model.ui);
    }

    public static ColumnSpec of(String name, FieldQueryFacet query, FieldUiFacet ui) {
        String type = ui == null ? null : ui.type();
        String inputType = ui == null ? null : ui.input_type();
        ColumnKind kind = kindForWidget(type, inputType);
        String source = "type='" + type + "'";
        if (kind == null && query != null && query.sql_type() != null) {
            kind = kindForSqlType(query.sql_type());
            source = source + ", sql_type='" + query.sql_type() + "'";
            Log.debugf("column %s: no widget kind, sql_type '%s' -> %s", name, query.sql_type(), kind);
        }
        boolean multiJoin = FieldDefinitionUtils.isMultiJoinType(type);
        return new ColumnSpec(name, kind,
                query == null ? null : query.sql_definition(),
                multiJoin,
                query == null ? null : query.join_table_name(),
                query == null ? null : query.join_table_key(),
                source);
    }

    /** Legacy widget → column mapping, formerly copied in each {@code *FieldDefinitionUtils}. */
    public static ColumnKind kindForWidget(String type, String inputType) {
        WidgetType widget = WidgetType.from(type);
        if (widget == null) {
            return null;
        }
        InputType input = InputType.from(inputType);
        return switch (widget) {
            case INPUT -> {
                if (input == null) {
                    yield ColumnKind.SHORT_TEXT;
                }
                yield switch (input) {
                    case TEXT, PASSWORD, EMAIL -> ColumnKind.SHORT_TEXT;
                    case NUMBER -> ColumnKind.INTEGER;
                    case DECIMAL -> ColumnKind.DECIMAL;
                    default -> ColumnKind.LONG_TEXT;
                };
            }
            case GMAPPATH, GMAPLOCATION, MONACO, TEXTAREA, TINYMCE -> ColumnKind.LONG_TEXT;
            case DATE -> ColumnKind.DATE;
            case DATETIME -> ColumnKind.DATETIME;
            case TIME -> ColumnKind.TIME;
            case CHECKBOX, PASSIVATION -> ColumnKind.BOOLEAN;
            case SELECT, MEDIA, IMAGE, TAGS, MULTIJOIN, MULTISELECT, MULTILOOKUP,
                 REALTIONSHIP, REALTIONSHIPS, RELATIONSHIP, RELATIONSHIPS, JOIN, LOOKUP ->
                    ColumnKind.MEDIUM_TEXT;
            default -> null;
        };
    }

    /** {@code query.sql_type()} → kind, via the semantic vocabulary; null when not recognised. */
    public static ColumnKind kindForSqlType(String sqlType) {
        String semantic = FieldUiDerivation.semanticFromSqlType(sqlType);
        if (semantic == null) {
            return null;
        }
        String[] ui = FieldUiDerivation.uiTypeFor(semantic);
        return kindForWidget(ui[0], ui[1]);
    }

    /** {@code <table>_<join_table_name>}: holds the pairs of a multi-join field. */
    public static String joinTableName(String table, ColumnSpec column) {
        return table + "_" + column.joinTableName();
    }

    /** Condition row that lets the list API filter {@code joinTable} by an id of {@code table}. */
    public static Condition multiJoinCondition(String table, String metadataUuid, ColumnSpec column) {
        String joinTableName = joinTableName(table, column);
        String tableId = table + "_id";
        String joinTableId = column.joinTableName() + "_id";
        Condition condition = new Condition();
        condition.metadata_multijoin_uuid = metadataUuid;
        condition.uuid = java.util.UUID.randomUUID().toString();
        condition.metadata_name = column.joinTableName();
        condition.metadata_uuid = "";
        condition.condition = table + "_id_nn && join_table_nn";
        condition.query_params = tableId;
        condition.sub_query = column.joinTableKey() + " in (select " + joinTableId + " from " + joinTableName
                + " where " + tableId + " = ?)";
        return condition;
    }
}
