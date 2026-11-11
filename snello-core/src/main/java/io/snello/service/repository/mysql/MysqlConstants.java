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
            "  `api_protected` boolean NOT NULL DEFAULT false,\n" +
            "  `username_field` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
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

    public static String creationHistorySession = "CREATE TABLE IF NOT EXISTS `history_session` (\n" +
            "  `uuid` varchar(255) NOT NULL,\n" +
            "  `session_id` varchar(255) NOT NULL,\n" +
            "  `user_id` varchar(255) DEFAULT NULL,\n" +
            "  `message_count` BIGINT NOT NULL DEFAULT 0,\n" +
            "  `started_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
            "  PRIMARY KEY (uuid),\n" +
            "  UNIQUE KEY `uk_history_session_session_id` (`session_id`)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationHistoryEvent = "CREATE TABLE IF NOT EXISTS `history_event` (\n" +
            "  `uuid` varchar(255) NOT NULL,\n" +
            "  `occurred_at` timestamp NOT NULL,\n" +
            "  `channel` varchar(32) NOT NULL,\n" +
            "  `user_id` varchar(255) DEFAULT NULL,\n" +
            "  `username` varchar(255) DEFAULT NULL,\n" +
            "  `type` varchar(32) NOT NULL,\n" +
            "  `body` text DEFAULT NULL,\n" +
            "  `session_id` varchar(255) DEFAULT NULL,\n" +
            "  `model` varchar(16) DEFAULT NULL,\n" +
            "  `recorded_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationAiProject = "CREATE TABLE IF NOT EXISTS `ai_project` (\n" +
            "  `uuid` varchar(255) NOT NULL,\n" +
            "  `name` varchar(128) NOT NULL,\n" +
            "  `description` text DEFAULT NULL,\n" +
            "  `global_message` text NOT NULL,\n" +
            "  `sso_group` varchar(256) NOT NULL DEFAULT '',\n" +
            "  `roles` text NOT NULL,\n" +
            "  PRIMARY KEY (uuid),\n" +
            "  UNIQUE KEY `uk_ai_project_name` (`name`)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationAiSkill = "CREATE TABLE IF NOT EXISTS `ai_skill` (\n" +
            "  `uuid` varchar(255) NOT NULL,\n" +
            "  `project_uuid` varchar(36) NOT NULL,\n" +
            "  `name` varchar(128) NOT NULL,\n" +
            "  `description` varchar(1024) NOT NULL,\n" +
            "  `instructions` text NOT NULL,\n" +
            "  `allowed_roles` text DEFAULT NULL,\n" +
            "  `enabled` BOOLEAN NOT NULL DEFAULT TRUE,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationAiMcpTool = "CREATE TABLE IF NOT EXISTS `ai_mcp_tool` (\n" +
            "  `uuid` varchar(255) NOT NULL,\n" +
            "  `project_uuid` varchar(36) NOT NULL,\n" +
            "  `name` varchar(128) NOT NULL,\n" +
            "  `description` varchar(1024) NOT NULL,\n" +
            "  `http_method` varchar(16) NOT NULL DEFAULT 'GET',\n" +
            "  `url` varchar(2048) NOT NULL,\n" +
            "  `parameters_json` text NOT NULL,\n" +
            "  `arg_binding` varchar(16) NOT NULL DEFAULT 'QUERY',\n" +
            "  `arg_names_json` text DEFAULT NULL,\n" +
            "  `fixed_query_json` text DEFAULT NULL,\n" +
            "  `catalog_page_path_template` varchar(1024) DEFAULT NULL,\n" +
            "  `result_type` varchar(128) DEFAULT NULL,\n" +
            "  `result_path` varchar(256) DEFAULT NULL,\n" +
            "  `authenticated` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  `service_account_uuid` varchar(255) DEFAULT NULL,\n" +
            "  `allowed_roles` text DEFAULT NULL,\n" +
            "  `enabled` BOOLEAN NOT NULL DEFAULT TRUE,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationAiUiCard = "CREATE TABLE IF NOT EXISTS `ai_ui_card` (\n" +
            "  `uuid` varchar(255) NOT NULL,\n" +
            "  `project_uuid` varchar(36) NOT NULL,\n" +
            "  `name` varchar(128) NOT NULL,\n" +
            "  `result_type` varchar(128) DEFAULT NULL,\n" +
            "  `template` text DEFAULT NULL,\n" +
            "  `enabled` BOOLEAN NOT NULL DEFAULT TRUE,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    /** Print templates: a query + a Qute/CSV body or a JXLS xlsx (template_file, base64), rendered to pdf/html/csv/xlsx. */
    public static String creationPrintTemplates = "CREATE TABLE IF NOT EXISTS `printtemplates` (\n" +
            "  `uuid` varchar(255) NOT NULL,\n" +
            "  `name` varchar(255) NOT NULL,\n" +
            "  `description` varchar(255) DEFAULT NULL,\n" +
            "  `export_type` varchar(10) NOT NULL,\n" +
            "  `sql_query` LONGTEXT,\n" +
            "  `html_body` LONGTEXT,\n" +
            "  `template_file` LONGTEXT,\n" +
            "  `template_file_name` varchar(255) DEFAULT NULL,\n" +
            "  `api_protected` boolean NOT NULL DEFAULT false,\n" +
            "  `username_field` varchar(255) DEFAULT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    /** Jobs run by ScheduledActionTimer: a cron (frequency) and a JS body with the same bindings as an Action. */
    public static String creationScheduledActions = "CREATE TABLE IF NOT EXISTS `scheduledactions` (\n" +
            "  `uuid` varchar(255) NOT NULL,\n" +
            "  `name` varchar(255) NOT NULL,\n" +
            "  `description` varchar(255) DEFAULT NULL,\n" +
            "  `frequency` varchar(255) NOT NULL,\n" +
            "  `enabled` boolean NOT NULL DEFAULT true,\n" +
            "  `body` MEDIUMTEXT,\n" +
            "  `next_run` varchar(64) DEFAULT NULL,\n" +
            "  `last_run` varchar(64) DEFAULT NULL,\n" +
            "  `last_status` varchar(20) DEFAULT NULL,\n" +
            "  `last_message` MEDIUMTEXT,\n" +
            "  `last_duration_ms` bigint DEFAULT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationAiServiceAccount = "CREATE TABLE IF NOT EXISTS `ai_service_account` (\n" +
            "  `uuid` varchar(255) NOT NULL,\n" +
            "  `project_uuid` varchar(36) NOT NULL,\n" +
            "  `name` varchar(128) NOT NULL,\n" +
            "  `host` varchar(512) NOT NULL,\n" +
            "  `realm` varchar(128) NOT NULL,\n" +
            "  `client_id` varchar(255) NOT NULL,\n" +
            "  `client_secret` text NOT NULL,\n" +
            "  `enabled` BOOLEAN NOT NULL DEFAULT TRUE,\n" +
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
