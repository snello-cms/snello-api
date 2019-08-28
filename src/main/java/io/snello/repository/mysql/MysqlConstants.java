package io.snello.repository.mysql;

public class MysqlConstants {

    public static final String COUNT_QUERY = " SELECT COUNT(*) AS SIZE_OF FROM ";
    public static final String DELETE_FROM = "DELETE FROM ";
    public static final String SHOW_TABLES = "SHOW TABLES FROM `matcms` LIKE ?";
    public static final String SELECT_FROM = " SELECT * FROM ";
    public static final String INSERT_INTO = "INSERT INTO ";
    public static final String TRUNCATE_TABLE = "TRUNCATE TABLE ";
    public static final String DROP_TABLE = "DROP TABLE ";


    public static final String ESCAPE = "`";


    public static final String LOGIN_QUERY = "SELECT password FROM users WHERE username = ? and active = 1";
    public static final String ROLES_QUERY = "SELECT role FROM userroles WHERE username = ?";
    public static String INSERT_ROLE_QUERY = "INSERT IGNORE INTO `userroles` (`username`, `role`) VALUES (?, ?);";

    public static String creationQueryConditions = "CREATE TABLE IF NOT EXISTS `conditions` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `separator` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `condition` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `query_params` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `sub_query` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationQueryDocuments = "CREATE TABLE IF NOT EXISTS `documents` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `original_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `path` varchar(255) COLLATE utf8mb4_unicode_ci ,\n" +
            "  `mimetype` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `size` int(12),\n" +
            "  `table_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `table_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationQueryDraggables = "CREATE TABLE IF NOT EXISTS draggables (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `template` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `style` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `image` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `vars` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `dynamics` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationQueryDroppables = "CREATE TABLE IF NOT EXISTS droppables (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `html` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `draggables` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `values` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `dynamics` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationQueryFieldDefinitions = "CREATE TABLE IF NOT EXISTS `fielddefinitions` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `label` varchar(255) COLLATE utf8mb4_unicode_ci  NOT NULL,\n" +
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
            "  `tab_groups` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `already_exist` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  `created` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationQuerySelectQueries = "CREATE TABLE IF NOT EXISTS `selectqueries` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `query_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `with_params` boolean DEFAULT false,\n" +
            "  `select_query` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationUsersQueries = "CREATE TABLE IF NOT EXISTS `users` (\n" +
            "  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `surname` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `active` boolean DEFAULT true,\n" +
            "  `creation_date` datetime ,\n" +
            "  `last_update_date` datetime ,\n" +
            "  PRIMARY KEY (username)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";


    public static String creationChangePasswordTokenQueries = "CREATE TABLE IF NOT EXISTS `changepasswordtokens` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `email` varchar(255) NOT NULL,\n" +
            "  `token` varchar(255) NOT NULL,\n" +
            "  `creation_date` datetime ,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationRolesQueries = "CREATE TABLE IF NOT EXISTS `roles` (\n" +
            "  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `object_type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `object_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `action` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  PRIMARY KEY (name)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationUserRolesQueries = "CREATE TABLE IF NOT EXISTS `userroles` (\n" +
            "  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `role` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  PRIMARY KEY (username, role)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public static String creationUrlMapRulesQueries = "CREATE TABLE IF NOT EXISTS `urlmaprules` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `pattern` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `access` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `http_methods` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
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



    public static String creationAdminUser = "INSERT IGNORE INTO `users` (`username`, `password`, `name`, `surname`, `email`, `active`) VALUES ('admin', 'jGl25bVBBBW96Qi9Te4V37Fnqchz/Eu4qB9vKrRIqRg=', 'admin', 'admin', 'admin@snello.io', '1');";

    public static String creationAdminRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('admin', 'all rules', 'metadatas', NULL, NULL);";

    public static String creationContentsViewRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('contents_view', 'contents view rule', 'metadatas', NULL, 'view');";
    public static String creationContentsEditRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('contents_edit', 'contents edit rule', 'metadatas', NULL, 'edit');";


    public static String creationConditionsViewRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('conditions_view', 'conditions view rule', 'metadatas', 'conditions', 'view');";
    public static String creationConditionsEditRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('conditions_edit', 'conditions edit rule', 'metadatas', 'conditions', 'edit');";

    public static String creationDocumentsViewRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('documents_view', 'documents view rule', 'metadatas', 'documents', 'view');";
    public static String creationDocumentsEditRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('documents_edit', 'documents edit rule', 'metadatas', 'documents', 'edit');";

    public static String creationFieldDefinitionsViewRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('fielddefinitions_view', 'fielddefinitions view rule', 'metadatas', 'fielddefinitions', 'view');";
    public static String creationFieldDefinitionsEditRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('fielddefinitions_edit', 'fielddefinitions edit rule', 'metadatas', 'fielddefinitions', 'edit');";


    public static String creationLinksViewRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('links_view', 'links view rule', 'metadatas', 'links', 'view');";
    public static String creationLinksEditRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('flinks_edit', 'links edit rule', 'metadatas', 'links', 'edit');";


    public static String creationMetadatasViewRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('metadatas_view', 'metadatas metadatas rule', 'metadatas', 'metadatas', 'view');";
    public static String creationMetadatasEditRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('metadatas_edit', 'metadatas metadatas rule', 'metadatas', 'metadatas', 'edit');";

    public static String creationPublicdataEditRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('publicdata_edit', 'publicdata edit rule', 'metadatas', 'publicdata', 'edit');";

    public static String creationRoleViewRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('roles_view', 'roles view rule', 'metadatas', 'roles', 'view');";
    public static String creationRoleEditRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('roles_edit', 'roles edit rule', 'metadatas', 'roles', 'edit');";


    public static String creationSelectQueryViewRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('selectqueries_view', 'selectqueries view rule', 'metadatas', 'selectqueries', 'view');";
    public static String creationSelectQueryEditRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('selectqueries_edit', 'selectqueries edit rule', 'metadatas', 'selectqueries', 'edit');";


    public static String creationUrlMapRuleViewRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('urlmaprules_view', 'urlmaprules view rule', 'metadatas', 'urlmaprules', 'view');";
    public static String creationUrlMapRuleEditRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('urlmaprules_edit', 'urlmaprules edit rule', 'metadatas', 'urlmaprules', 'edit');";


    public static String creationUserViewRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('users_view', 'users view rule', 'metadatas', 'users', 'view');";
    public static String creationUserEditRole = "INSERT IGNORE INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('users_edit', 'users edit rule', 'metadatas', 'users', 'edit');";

    public static String creationDroppableViewRole = "INSERT INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('droppables_view', 'droppables view rule', 'metadatas', 'droppables', 'view') ON CONFLICT (name) DO NOTHING;";
    public static String creationDroppableEditRole = "INSERT INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('droppables_edit', 'droppables edit rule', 'metadatas', 'droppables', 'edit') ON CONFLICT (name) DO NOTHING;";

    public static String creationDraggableViewRole = "INSERT INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('draggables_view', 'draggables view rule', 'metadatas', 'draggables', 'view') ON CONFLICT (name) DO NOTHING;";
    public static String creationDraggableEditRole = "INSERT INTO `roles` (`name`, `description`, `object_type`, `object_name`, `action`) VALUES ('draggables_edit', 'draggables edit rule', 'metadatas', 'draggables', 'edit') ON CONFLICT (name) DO NOTHING;";


    public static String creationAdminUserRole = "INSERT IGNORE INTO `userroles` (`username`, `role`) VALUES ('admin', 'admin');";

}
