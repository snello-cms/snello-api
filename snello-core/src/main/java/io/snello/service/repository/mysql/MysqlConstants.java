package io.snello.service.repository.mysql;

public class MysqlConstants {

    public static final String COUNT_QUERY = " SELECT COUNT(*) AS SIZE_OF FROM ";
    public static final String DELETE_FROM = "DELETE FROM ";
    public static final String SHOW_TABLES_INIT = "SHOW TABLES ";
    public static final String SHOW_TABLES_END = " LIKE ?";
    public static final String SELECT_FROM = " SELECT * FROM ";
    public static final String INSERT_INTO = "INSERT INTO ";
    public static final String TRUNCATE_TABLE = "TRUNCATE TABLE ";
    public static final String DROP_TABLE = "DROP TABLE ";

    public static final String ESCAPE = "`";

    public static String creationActionExtensions = "CREATE TABLE IF NOT EXISTS actions (\n" +
            "  `uuid` varchar(255) NOT NULL,\n" +
            "  `name` varchar(255) NOT NULL,\n" +
            "  `description` varchar(255) NOT NULL,\n" +
            "  `metadata_name` varchar(255) NOT NULL,\n" +
            "  `condition` varchar(255) ,\n" +
            "  `phase` varchar(10) DEFAULT 'POST',\n" +
            "  `body` varchar(1000) ,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationQueryConditions = "CREATE TABLE IF NOT EXISTS `conditions` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `separator` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `condition` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `query_params` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `sub_query` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_multijoin_uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationQueryDocuments = "CREATE TABLE IF NOT EXISTS `documents` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `original_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `path` varchar(255) COLLATE utf8mb4_unicode_ci ,\n" +
            "  `formats` varchar(2055) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `mimetype` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `size` int(12),\n" +
            "  `creation_date` datetime DEFAULT CURRENT_TIMESTAMP,\n" +
            "  `table_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `table_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationQueryFieldDefinitions = "CREATE TABLE IF NOT EXISTS `fielddefinitions` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `label` varchar(255) COLLATE utf8mb4_unicode_ci  NOT NULL,\n" +
            "  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `input_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `options` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `group_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `tab_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `validations` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `table_key` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  `input_disabled` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  `function_def` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL ,\n" +
            "  `sql_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `sql_definition` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `default_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `pattern` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `join_table_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `join_table_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `join_table_select_fields` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `searchable` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  `search_condition` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `search_field_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `show_in_list` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  `view_index` int(12),\n" +
            "  `mandatory` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  `order_num` int(12),\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationQueryMetadatas = "CREATE TABLE IF NOT EXISTS `metadatas` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `table_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `icon` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `select_fields` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `search_fields` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `alias_table` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `alias_condition` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `table_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `table_key_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `table_key_addition` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `creation_query` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `order_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `metadata_group` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `tab_groups` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `already_exist` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  `calendar_enabled` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  `calendar_field` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `calendar_label` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `created` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationQuerySelectQueries = "CREATE TABLE IF NOT EXISTS `selectqueries` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `query_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `with_params` boolean DEFAULT false,\n" +
            "  `select_query` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `select_query_count` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationLinksQueries = "CREATE TABLE IF NOT EXISTS `links` (\n" +
            "  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `labels` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `metadata_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `metadata_searchable_field` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `metadata_lock_field` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `metadata_generated_uuid` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `created` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  PRIMARY KEY (name)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationChatInteractions = "CREATE TABLE IF NOT EXISTS `chat_interactions` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `conversation_uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `user_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `user_message` text COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `ai_response` text COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `creation_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String joinTableQuery = "CREATE TABLE IF NOT EXISTS `%1$s` ( " +
            "`%2$s` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL, " +
            "%3$s varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL, " +
            "id int NOT NULL AUTO_INCREMENT, PRIMARY KEY (id) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationAiTools = "CREATE TABLE IF NOT EXISTS `ai_tools` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `method_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `description` text COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `sql_query` text COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `parameters_schema` text COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `active` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationQuerySystemEventLogs = "CREATE TABLE IF NOT EXISTS `systemeventlogs` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `query_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `with_params` boolean DEFAULT false,\n" +
            "  `select_query` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

}
