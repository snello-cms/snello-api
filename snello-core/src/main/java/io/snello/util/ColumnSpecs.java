package io.snello.util;

import io.quarkus.logging.Log;
import io.snello.model.Condition;
import io.snello.model.FieldDefinition;
import io.snello.model.facet.FieldQueryFacet;
import io.snello.model.facet.FieldUiFacet;
import io.snello.model.pojo.ColumnKind;
import io.snello.model.pojo.ColumnSpec;

/**
 * The single bridge from facets to storage: turns a field into a {@link ColumnSpec}.
 * <p>
 * Resolution order: {@code query.sql_definition} (raw, wins) → UI widget {@code type}/{@code input_type} (how every
 * existing row was authored) → {@code query.sql_type}. Dialects never look at UI fields.
 */
public final class ColumnSpecs {

    private ColumnSpecs() {
    }

    public static ColumnSpec of(FieldDefinition fd) {
        return of(fd.name, fd.queryFacet(), fd.uiFacet());
    }

    public static ColumnSpec of(String name, FieldQueryFacet query, FieldUiFacet ui) {
        String type = ui == null ? null : ui.type;
        String inputType = ui == null ? null : ui.input_type;
        ColumnKind kind = kindForWidget(type, inputType);
        String source = "type='" + type + "'";
        if (kind == null && query != null && query.sql_type != null) {
            kind = kindForSqlType(query.sql_type);
            source = source + ", sql_type='" + query.sql_type + "'";
            Log.debugf("column %s: no widget kind, sql_type '%s' -> %s", name, query.sql_type, kind);
        }
        boolean multiJoin = FieldDefinitionUtils.isMultiJoinType(type);
        return new ColumnSpec(name, kind,
                query == null ? null : query.sql_definition,
                multiJoin,
                query == null ? null : query.join_table_name,
                query == null ? null : query.join_table_key,
                source);
    }

    /** Legacy widget → column mapping, formerly copied in each {@code *FieldDefinitionUtils}. */
    public static ColumnKind kindForWidget(String type, String inputType) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "input" -> {
                if (inputType == null) {
                    yield ColumnKind.SHORT_TEXT;
                }
                yield switch (inputType) {
                    case "text", "password", "email" -> ColumnKind.SHORT_TEXT;
                    case "number" -> ColumnKind.INTEGER;
                    case "decimal" -> ColumnKind.DECIMAL;
                    // the old switch fell through to the textarea branch for any other input_type
                    default -> ColumnKind.LONG_TEXT;
                };
            }
            case "gmappath", "gmaplocation", "monaco", "textarea", "tinymce" -> ColumnKind.LONG_TEXT;
            case "date" -> ColumnKind.DATE;
            case "datetime" -> ColumnKind.DATETIME;
            case "time" -> ColumnKind.TIME;
            case "checkbox", "passivation" -> ColumnKind.BOOLEAN;
            case "select", "media", "image", "tags", "multijoin", "multiselect", "multilookup",
                 "realtionship", "realtionships", "relationship", "relationships", "join", "lookup" ->
                    ColumnKind.MEDIUM_TEXT;
            default -> null;
        };
    }

    /** {@code query.sql_type} → kind, via the semantic vocabulary; null when not recognised. */
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
