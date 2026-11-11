package io.snello.service;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.snello.model.Condition;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.SelectQuery;
import io.snello.model.pojo.EntityQueryView;
import io.snello.api.service.JdbcRepository;
import io.snello.service.actions.ActionEvent;
import io.snello.util.FieldDefinitionUtils;
import io.snello.util.ParamUtils;
import io.snello.util.SqlSafety;
import io.snello.util.SqlWhere;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.snello.management.AppConstants.*;
import static io.snello.service.repository.mysql.MysqlConstants.DROP_TABLE;
import static io.snello.service.repository.mysql.MysqlConstants.TRUNCATE_TABLE;

@ApplicationScoped
public class ApiService {

    @Inject
    MetadataService metadataService;

    /** Read path (count / list / fetch) resolves entities through query views only. */
    @Inject
    EntityCatalog catalog;

    @Inject
    JdbcRepository jdbcRepository;

    @Inject
    SecurityIdentity securityIdentity;

    public ApiService() {
    }

    public Metadata metadata(String metadata_name) throws Exception {
        Metadata metadata = metadataService.metadataMap().get(metadata_name);
        return metadata;
    }

    public Metadata metadataWithFields(String metadata_name) throws Exception {
        Metadata metadata = metadataService.metadataMap().get(metadata_name);
        if (metadata == null) {
            return null;
        }
        metadata.fields = metadataService.fielddefinitions(metadata.table_name);
        return metadata;
    }

    public boolean isPassivable(String metadata_name) throws Exception {
        Metadata metadata = metadataService.metadataMap().get(metadata_name);
        if (metadata == null) {
            throw new Exception("metadata not found for table: " + metadata_name);
        }
        metadata.fields = metadataService.fielddefinitions(metadata.table_name);
        return isPassivable(metadata.fields);
    }

    public boolean isPassivable(List<FieldDefinition> fields) throws Exception {
        if (fields == null || fields.isEmpty()) return false;
        for (FieldDefinition fd : fields) {
            if (FieldDefinitionUtils.isPassivation(fd)) {
                return true;
            }
        }
        return false;
    }

    public String getPassivableField(List<FieldDefinition> fields) throws Exception {
        for (FieldDefinition fd : fields) {
            if (FieldDefinitionUtils.isPassivation(fd)) {
                return fd.name;
            }
        }
        return null;
    }

    public String slugField(String metadata_name) throws Exception {
        Metadata metadata = metadataService.metadataMap().get(metadata_name);
        if (metadata == null) {
            throw new Exception("metadata not found for table: " + metadata_name);
        }
        return metadata.table_key_addition;
    }

    public String table_key(String metadata_name) throws Exception {
        Metadata metadata = metadataService.metadataMap().get(metadata_name);
        if (metadata == null) {
            throw new Exception("metadata not found for table: " + metadata_name);
        }
        return metadata.table_key;
    }

    private static final String SELECT_QUERY_OWNER_WRAP_PREFIX = "SELECT * FROM (";
    private static final String SELECT_QUERY_OWNER_WRAP_SUFFIX = ") snello_owner_scope";

    /** True when a protected SelectQuery was given its owner value (non-admin callers always are). */
    private static boolean ownerScoped(SelectQuery selectQuery, MultivaluedMap<String, String> httpParameters) {
        return selectQuery.api_protected
                && selectQuery.username_field != null && !selectQuery.username_field.isBlank()
                && httpParameters != null
                && httpParameters.getFirst(selectQuery.username_field) != null;
    }

    /** Request parameters for an owner-scoped query: the owner value, plus the caller's filters only if with_params. */
    private static MultivaluedMap<String, String> ownerScopedParameters(SelectQuery selectQuery,
            MultivaluedMap<String, String> httpParameters) {
        String column = selectQuery.username_field;
        MultivaluedMap<String, String> scoped = new MultivaluedHashMap<>();
        if (selectQuery.with_params) {
            scoped.putAll(httpParameters);
        }
        scoped.put(column, List.of(httpParameters.getFirst(column)));
        return scoped;
    }

    /**
     * Count of an owner-scoped SelectQuery, computed over the same wrap as the list. The stored select_query_count
     * is not used: nothing guarantees it exposes the owner column, and run unfiltered it would reveal how many rows
     * all users have (MySQL executes it; H2/PostgreSQL return 0).
     */
    private long ownerScopedCount(String table, SelectQuery selectQuery, MultivaluedMap<String, String> httpParameters)
            throws Exception {
        String countSql = "SELECT COUNT(*) AS " + SIZE_OF_COLUMN + " FROM ("
                + stripTrailingSemicolons(selectQuery.select_query) + SELECT_QUERY_OWNER_WRAP_SUFFIX;
        long count = readCount(jdbcRepository.list(countSql, ownerScopedParameters(selectQuery, httpParameters),
                null, null, 0, 0));
        Log.infof("selectquery %s: owner-scoped count on column=%s -> %d", table, selectQuery.username_field, count);
        return count;
    }

    private static long readCount(List<Map<String, Object>> rows) {
        if (rows != null && !rows.isEmpty()) {
            for (Map.Entry<String, Object> e : rows.get(0).entrySet()) {
                if (SIZE_OF_COLUMN.equalsIgnoreCase(e.getKey()) && e.getValue() instanceof Number n) {
                    return n.longValue();
                }
            }
        }
        return 0;
    }

    private static final java.util.Set<String> NON_FILTER_PARAMS =
            java.util.Set.of(ParamUtils._LIMIT, ParamUtils._START, ParamUtils._SORT, ParamUtils._SELECT_FIELDS);

    private static boolean hasFilters(MultivaluedMap<String, String> params) {
        return params != null && params.keySet().stream().anyMatch(k -> !NON_FILTER_PARAMS.contains(k));
    }

    /** Request parameters a SelectQuery is filtered by: all of them when with_params, none otherwise. */
    private static MultivaluedMap<String, String> selectQueryFilters(SelectQuery selectQuery,
            MultivaluedMap<String, String> httpParameters) {
        return selectQuery.with_params && httpParameters != null ? httpParameters : new MultivaluedHashMap<>();
    }

    /**
     * Total of a (not owner-scoped) SelectQuery on every database. The repositories' count(select_query, ...) is a
     * stub returning 0 on H2 and PostgreSQL, so paging never worked there. The stored select_query_count is used
     * when no filters apply; with filters the total is counted over the query itself, so it matches the list.
     */
    private long selectQueryCount(String table, SelectQuery selectQuery, MultivaluedMap<String, String> httpParameters) {
        MultivaluedMap<String, String> filters = selectQueryFilters(selectQuery, httpParameters);
        boolean filtered = hasFilters(filters);
        String storedCount = selectQuery.select_query_count;
        String countSql = !filtered && storedCount != null && !storedCount.isBlank()
                ? stripTrailingSemicolons(storedCount)
                : "SELECT COUNT(*) AS " + SIZE_OF_COLUMN + " FROM (" + stripTrailingSemicolons(selectQuery.select_query)
                  + ") snello_count";
        try {
            long count = readCount(jdbcRepository.list(countSql, filtered ? filters : new MultivaluedHashMap<>(),
                    null, null, 0, 0));
            Log.infof("selectquery %s: count filtered=%s -> %d", table, filtered, count);
            return count;
        } catch (Exception e) {
            // e.g. a filter on a column the query does not expose: keep the list usable, report no total
            Log.warnf("selectquery %s: count failed (%s), x-total-count=0 | sql=%s", table, e.getMessage(), countSql);
            return 0;
        }
    }

    private static final String SIZE_OF_COLUMN = "size_of";

    private static String stripTrailingSemicolons(String sql) {
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    public long count(String table, UriInfo uriInfo) throws Exception {
        MultivaluedMap<String, String> queryParameters = uriInfo != null
                ? uriInfo.getQueryParameters()
                : null;
        return count(table, queryParameters);
    }

    public long count(String table, MultivaluedMap<String, String> queryParameters) throws Exception {
        String alias_condition = null;
        List<Condition> conditions = null;
        SelectQuery selectQuery = metadataService.selectqueryMap().get(table);
        if (selectQuery != null && ownerScoped(selectQuery, queryParameters)) {
            return ownerScopedCount(table, selectQuery, queryParameters);
        }
        if (selectQuery != null) {
            return selectQueryCount(table, selectQuery, queryParameters);
        }

        Optional<EntityQueryView> view = catalog.queryView(table);
        if (view.isPresent()) {
            conditions = metadataService.conditionsMap().get(table);
            if (view.get().hasAlias()) {
                table = view.get().aliasTable();
                alias_condition = view.get().aliasCondition();
            }
        }
        Log.debugf("count resolved table=%s alias_condition=%s conditions=%d", table, alias_condition,
                conditions == null ? 0 : conditions.size());
        return jdbcRepository.count(table, alias_condition, queryParameters, conditions);
    }

    public boolean tableExists(String table) throws Exception {
        return table != null && !table.isBlank() && jdbcRepository.verifyTable(table);
    }

    public boolean exist(String table, String table_key, Object uuid) throws Exception {
        return jdbcRepository.exist(table, table_key, uuid);
    }

    /**
     * Runs a SelectQuery (stored or built on the fly, e.g. by a print template): owner scope when protected, filters
     * when with_params, then sort and paging. {@code httpParameters} must already carry the owner value for
     * non-admin callers (see ApiRequestUtils.rowFilter).
     */
    public List<Map<String, Object>> listSelectQuery(String table, SelectQuery selectQuery,
            MultivaluedMap<String, String> httpParameters, String sort, int limit, int start) throws Exception {
        SqlSafety.requireSafeSort(sort);
        List<Condition> conditions = null;
        if (ownerScoped(selectQuery, httpParameters)) {
            // Row-level security: the owner value was bound by ApiServiceRs. Wrap the stored SQL so the filter
            // applies to the query result whatever its own WHERE / JOIN / GROUP BY look like, and ignore every
            // other request parameter when the query is not with_params.
            String wrapped = SELECT_QUERY_OWNER_WRAP_PREFIX + stripTrailingSemicolons(selectQuery.select_query)
                    + SELECT_QUERY_OWNER_WRAP_SUFFIX;
            Log.infof("selectquery %s: owner-scoped list on column=%s with_params=%s", table,
                    selectQuery.username_field, selectQuery.with_params);
            return jdbcRepository.list(wrapped, ownerScopedParameters(selectQuery, httpParameters), conditions, sort,
                    limit, start);
        }
        // Filters (with_params only), sort and paging are appended to the stored SQL. When it already ends with
        // GROUP BY / ORDER BY / LIMIT / ... appending is a syntax error, so it is wrapped as a subquery first.
        // Non-with_params queries used to ignore sort and paging and return every row.
        MultivaluedMap<String, String> filters = selectQueryFilters(selectQuery, httpParameters);
        String sql = stripTrailingSemicolons(selectQuery.select_query);
        boolean appends = hasFilters(filters) || (sort != null && !sort.isBlank()) || limit > 0 || start > 0;
        if (appends && SqlWhere.needsWrap(sql)) {
            sql = "SELECT * FROM (" + sql + ") snello_q";
            Log.infof("selectquery %s: wrapped as subquery for filters/sort/paging", table);
        }
        return jdbcRepository.list(sql, filters, null, sort, limit, start);
    }

    public List<Map<String, Object>> list(String table, MultivaluedMap<String, String> httpParameters, String sort,
            int limit,
            int start) throws Exception {
        SqlSafety.requireSafeSort(sort);
        // select fields:
        String select_fields = ParamUtils.select_fields(httpParameters);
        String alias_condition = null;
        List<Condition> conditions = null;
        if (metadataService.selectqueryMap().containsKey(table)) {
            return listSelectQuery(table, metadataService.selectqueryMap().get(table), httpParameters, sort, limit, start);
        }
        Optional<EntityQueryView> resolved = catalog.queryView(table);
        if (resolved.isPresent()) {
            EntityQueryView view = resolved.get();
            conditions = metadataService.conditionsMap().get(table);
            if (select_fields == null) {
                if (view.selectFields() != null && !view.selectFields().isBlank()) {
                    select_fields = view.selectFields();
                }
            } else {
                Log.info("uso i httpParameters:" + select_fields);
            }
            if (view.hasAlias()) {
                table = view.aliasTable();
                alias_condition = view.aliasCondition();
            }
            if ((sort == null || sort.isEmpty()) && view.orderBy() != null && !view.orderBy().isBlank()) {
                sort = view.orderBy();
            }
            Log.debugf("list resolved entity=%s table=%s select_fields=%s sort=%s", view.tableName(), table, select_fields, sort);
        }
        return jdbcRepository.list(table, select_fields, alias_condition, httpParameters, conditions, sort, limit,
                start);
    }

    @ActionEvent
    public Map<String, Object> create(String table, Map<String, Object> map, String table_key) throws Exception {
        table = initTable(table);
        table_key = metadataService.initTableKey(table, table_key);
        return jdbcRepository.create(table, table_key, map);
    }

    public Map<String, Object> createFromMap(String table, Map<String, Object> map) throws Exception {
        return jdbcRepository.create(table, UUID, map);
    }

    public List<Map<String, Object>> list(String query) throws Exception {
        return jdbcRepository.list(query);
    }

    @ActionEvent
    public Map<String, Object> merge(String table, Map<String, Object> map, String key, String table_key)
            throws Exception {
        table = initTable(table);
        table_key = metadataService.initTableKey(table, table_key);
        return jdbcRepository.update(table, table_key, map, key);
    }

    public boolean truncateTable(String uuid) throws Exception {
        Metadata metadata = metadataService.byUUid(uuid);
        if (metadata == null) {
            return false;
        }
        String table;
        if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
            table = metadata.alias_table;
        }
        table = metadata.table_name;
        return jdbcRepository.query(TRUNCATE_TABLE + table, null);
    }

    public boolean deleteTable(String uuid) throws Exception {
        Metadata metadata = metadataService.byUUid(uuid);
        if (metadata == null) {
            return false;
        }
        metadata.fields = metadataService.fielddefinitions(metadata.table_name);
        List<FieldDefinition> fields = metadata.fields == null ? List.of() : metadata.fields;
        for (FieldDefinition fd : fields) {
            if (FieldDefinitionUtils.isMultiJoin(fd)) {
                String join_table_name = metadata.table_name + "_" + fd.join_table_name;
                jdbcRepository.query(DROP_TABLE + join_table_name, null);
            }
        }
        // DEVO ELIMINARE TUTTE LE CONDITIONS COLLEGATE
        jdbcRepository.delete(CONDITIONS, "metadata_multijoin_uuid", metadata.uuid);
        if (metadata.already_exist) {
            throw new Exception("metadata was already_exist: we can't destroy!");
        }
        String table;
        if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
            table = metadata.alias_table;
        }
        table = metadata.table_name;
        return jdbcRepository.query(DROP_TABLE + table, null);
    }

    public Map<String, Object> createIfNotExists(String table, Map<String, Object> map, String table_key)
            throws Exception {
        alreadyExists(table);
        return jdbcRepository.create(table, table_key, map);
    }

    public Map<String, Object> mergeIfNotExists(String table, Map<String, Object> map, String key, String table_key)
            throws Exception {
        alreadyExists(table);
        return jdbcRepository.update(table, table_key, map, key);
    }

    public void alreadyExists(String table) throws Exception {
        if (metadataService.metadataMap().containsKey(table) || metadataService.selectqueryMap().containsKey(table)) {
            throw new Exception(" metadata or selectquery already exists! ");
        }
    }

    public String initTable(String table) throws Exception {
        if (metadataService.metadataMap().containsKey(table)) {
            Metadata metadata = metadataService.metadataMap().get(table);
            if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
                return metadata.alias_table;
            }
            return metadata.table_name;
        }
        return table;
    }

    public Map<String, Object> fetch(MultivaluedMap<String, String> queryParameters, String table, String uuid,
            String table_key) throws Exception {
        String select_fields = ParamUtils.select_fields(queryParameters);
        Optional<EntityQueryView> view = catalog.queryView(table);
        if (view.isPresent() && view.get().hasAlias()) {
            table = view.get().aliasTable();
            table_key = view.get().tableKey();
        }
        return jdbcRepository.fetch(select_fields, table, table_key, uuid);
    }

    @ActionEvent
    public boolean delete(String table, String uuid, String table_key) throws Exception {
        if (metadataService.metadataMap().containsKey(table)) {
            Metadata metadata = metadataService.metadataMap().get(table);
            if (metadata.fields == null || metadata.fields.isEmpty()) {
                metadata.fields = metadataService.fielddefinitions(metadata.table_name);
            }
            if (isPassivable(metadata.fields)) {
                String passivableField = getPassivableField(metadata.fields);
                if (passivableField != null) {
                    Map<String, Object> map = Map.of(passivableField, false);
                    jdbcRepository.update(table, table_key, map, uuid);
                    return true;
                }
            }
            if (metadata.alias_table != null && !metadata.alias_table.trim().isEmpty()) {
                table = metadata.alias_table;
                table_key = metadata.table_key;
            }
        }
        return jdbcRepository.delete(table, table_key, uuid);
    }

    public boolean deleteFieldDefinitionsByMetadataUuid(String uuid) throws Exception {
        List<Object> uuidList = new ArrayList<>();
        uuidList.add(uuid);
        return jdbcRepository.query(DELETE_FROM_FD, uuidList);
    }

    public void batch(String[] queries) throws Exception {
        jdbcRepository.batch(queries);
    }

    public Metadata createMetadataTable(String uuid) throws Exception {
        return metadataService.createTableFromMetadata(uuid);
    }

    public boolean query(String query, List<Object> values) throws Exception {
        return jdbcRepository.query(query, values);
    }

}
