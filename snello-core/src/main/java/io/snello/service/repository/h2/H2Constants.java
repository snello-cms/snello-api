package io.snello.service.repository.h2;

public class H2Constants {

        public static final String COUNT_QUERY = " SELECT COUNT(*) AS SIZE_OF FROM ";
        public static final String DELETE_FROM = "DELETE FROM ";
        public static final String SHOW_TABLES = "SHOW TABLES";
        public static final String SELECT_FROM = " SELECT * FROM ";
        public static final String INSERT_INTO = "INSERT INTO ";
        public static final String TRUNCATE_TABLE = "TRUNCATE TABLE ";
        public static final String DROP_TABLE = "DROP TABLE ";

        public static final String ESCAPE = "";

        public static String creationQueryActionExtensions = "CREATE TABLE IF NOT EXISTS actions (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  name varchar(255) NOT NULL,\n" +
                        "  description varchar(255) NOT NULL,\n" +
                        "  metadata_name varchar(255) NOT NULL,\n" +
                        "  condition varchar(255) ,\n" +
                        "  phase varchar(10) DEFAULT 'POST',\n" +
                        "  body varchar(1000) ,\n" +
                        "  PRIMARY KEY (uuid)\n" +
                        " ) ;";

        public static String creationQueryConditions = "CREATE TABLE IF NOT EXISTS conditions (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  metadata_uuid varchar(255) NOT NULL,\n" +
                        "  metadata_name varchar(255) NOT NULL,\n" +
                        "  separator varchar(255) DEFAULT NULL,\n" +
                        "  condition varchar(255) NOT NULL,\n" +
                        "  query_params varchar(255) DEFAULT NULL,\n" +
                        "  sub_query varchar(255) NOT NULL,\n" +
                        "  metadata_multijoin_uuid varchar(255) NOT NULL,\n" +
                        "  PRIMARY KEY (uuid)\n" +
                        ") ;";

        public static String creationQueryDocuments = "CREATE TABLE IF NOT EXISTS documents (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  name varchar(255) NOT NULL,\n" +
                        "  original_name varchar(255) NOT NULL,\n" +
                        "  path varchar(255) ,\n" +
                        "  formats varchar(2055) ,\n" +
                        "  mimetype varchar(255) NOT NULL,\n" +
                        "  size int(12),\n" +
                        "  creation_date timestamp DEFAULT CURRENT_TIMESTAMP,\n" +
                        "  table_name varchar(255) NOT NULL,\n" +
                        "  table_key varchar(255) NOT NULL,\n" +
                        "  PRIMARY KEY (uuid)\n" +
                        ") ;";

        // Long text columns use VARCHAR(1000000) (not CLOB) so JDBC returns String for fromMap.
        public static String creationQueryFieldDefinitions = "CREATE TABLE IF NOT EXISTS fielddefinitions (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  metadata_uuid varchar(255) NOT NULL,\n" +
                        "  metadata_name varchar(255) NOT NULL,\n" +
                        "  name varchar(255) NOT NULL,\n" +
                        "  label varchar(1000000)  NOT NULL,\n" +
                        "  description varchar(1000000) DEFAULT NULL,\n" +
                        "  type varchar(255) NOT NULL,\n" +
                        "  input_type varchar(255) DEFAULT NULL,\n" +
                        "  options varchar(1000000) DEFAULT NULL,\n" +
                        "  group_name varchar(255) DEFAULT NULL,\n" +
                        "  tab_name varchar(255) DEFAULT NULL,\n" +
                        "  validations varchar(1000000) DEFAULT NULL,\n" +
                        "  table_key BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                        "  input_disabled BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                        "  function_def varchar(1000000) DEFAULT NULL ,\n" +
                        "  sql_type varchar(1000000) DEFAULT NULL,\n" +
                        "  sql_definition varchar(1000000) DEFAULT NULL,\n" +
                        "  default_value varchar(1000000) DEFAULT NULL,\n" +
                        "  pattern varchar(1000000) DEFAULT NULL,\n" +
                        "  join_table_name varchar(100) DEFAULT NULL,\n" +
                        "  join_table_key varchar(100) DEFAULT NULL,\n" +
                        "  join_table_select_fields varchar(1000000) DEFAULT NULL,\n" +
                        "  searchable BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                        "  search_condition varchar(1000000) DEFAULT NULL,\n" +
                        "  search_field_name varchar(1000000) DEFAULT NULL,\n" +
                        "  show_in_list BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                        "  view_index int(12),\n" +
                        "  mandatory BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                        "  order_num int(12),\n" +
                        "  PRIMARY KEY (uuid)\n" +
                        ") ;";

        public static String creationQueryMetadatas = "CREATE TABLE IF NOT EXISTS metadatas (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  table_name varchar(255) NOT NULL,\n" +
                        "  icon varchar(100) DEFAULT NULL,\n" +
                        "  select_fields varchar(1000000) DEFAULT NULL,\n" +
                        "  search_fields varchar(1000000) DEFAULT NULL,\n" +
                        "  description varchar(1000000) DEFAULT NULL,\n" +
                        "  alias_table varchar(255) DEFAULT NULL,\n" +
                        "  alias_condition varchar(1000000) DEFAULT NULL,\n" +
                        "  table_key varchar(255) NOT NULL,\n" +
                        "  table_key_type varchar(255) DEFAULT NULL,\n" +
                        "  table_key_addition varchar(255) DEFAULT NULL,\n" +
                        "  creation_query varchar(1000000) DEFAULT NULL,\n" +
                        "  order_by varchar(1000000) DEFAULT NULL,\n" +
                        "  metadata_group varchar(150) DEFAULT NULL,\n" +
                        "  tab_groups varchar(1000000) DEFAULT NULL,\n" +
                        "  already_exist BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                        "  calendar_enabled BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                        "  calendar_field varchar(255) DEFAULT NULL,\n" +
                        "  calendar_label varchar(255) DEFAULT NULL,\n" +
                        "  created BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                        "  PRIMARY KEY (uuid)\n" +
                        ") ;";

        public static String creationQuerySelectQueries = "CREATE TABLE IF NOT EXISTS selectqueries (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  query_name varchar(255) NOT NULL,\n" +
                        "  with_params boolean DEFAULT false,\n" +
                        "  select_query varchar(255) DEFAULT NULL,\n" +
                        "  select_query_count varchar(255) DEFAULT NULL,\n" +
                        "  api_protected boolean DEFAULT false NOT NULL,\n" +
                        "  username_field varchar(255) DEFAULT NULL,\n" +
                        "  PRIMARY KEY (uuid)\n" +
                        ") ;";

        public static String creationLinksQueries = "CREATE TABLE IF NOT EXISTS links (\n" +
                        "  name varchar(255) NOT NULL,\n" +
                        "  labels varchar(255) NOT NULL,\n" +
                        "  metadata_name varchar(100) DEFAULT NULL,\n" +
                        "  metadata_key varchar(255) DEFAULT NULL,\n" +
                        "  metadata_searchable_field varchar(255) DEFAULT NULL,\n" +
                        "  metadata_lock_field varchar(255) DEFAULT NULL,\n" +
                        "  metadata_generated_uuid varchar(255) DEFAULT NULL,\n" +
                        "  created BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                        "  PRIMARY KEY (name)\n" +
                        ") ;";

        public static String creationChatInteractions = "CREATE TABLE IF NOT EXISTS chat_interactions (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  conversation_uuid varchar(255) NOT NULL,\n" +
                        "  user_id varchar(255) DEFAULT NULL,\n" +
                        "  user_message CLOB NOT NULL,\n" +
                        "  ai_response CLOB NOT NULL,\n" +
                        "  creation_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                        "  PRIMARY KEY (uuid)\n" +
                        ") ;";

        // Upgrade legacy schemas with varchar(4000) limits on chat messages (H2 syntax).
        public static String upgradeChatInteractionsUserMessage = "ALTER TABLE chat_interactions ALTER COLUMN user_message SET DATA TYPE CLOB";
        public static String upgradeChatInteractionsAiResponse = "ALTER TABLE chat_interactions ALTER COLUMN ai_response SET DATA TYPE CLOB";

        public static String creationAiTools = "CREATE TABLE IF NOT EXISTS ai_tools (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  name varchar(255) NOT NULL,\n" +
                        "  method_name varchar(255) DEFAULT NULL,\n" +
                        "  description CLOB DEFAULT NULL,\n" +
                        "  sql_query CLOB DEFAULT NULL,\n" +
                        "  parameters_schema CLOB DEFAULT NULL,\n" +
                        "  active BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                        "  PRIMARY KEY (uuid)\n" +
                        ") ;";

        public static String creationHistorySession = "CREATE TABLE IF NOT EXISTS history_session (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  session_id varchar(255) NOT NULL,\n" +
                        "  user_id varchar(255) DEFAULT NULL,\n" +
                        "  message_count BIGINT NOT NULL DEFAULT 0,\n" +
                        "  started_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                        "  PRIMARY KEY (uuid),\n" +
                        "  UNIQUE (session_id)\n" +
                        ") ;";

        public static String creationHistoryEvent = "CREATE TABLE IF NOT EXISTS history_event (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  occurred_at timestamp NOT NULL,\n" +
                        "  channel varchar(32) NOT NULL,\n" +
                        "  user_id varchar(255) DEFAULT NULL,\n" +
                        "  username varchar(255) DEFAULT NULL,\n" +
                        "  type varchar(32) NOT NULL,\n" +
                        "  body CLOB DEFAULT NULL,\n" +
                        "  session_id varchar(255) DEFAULT NULL,\n" +
                        "  model varchar(16) DEFAULT NULL,\n" +
                        "  recorded_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                        "  PRIMARY KEY (uuid)\n" +
                        ") ;";

        public static String creationAiProject = "CREATE TABLE IF NOT EXISTS ai_project (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  name varchar(128) NOT NULL,\n" +
                        "  description CLOB DEFAULT NULL,\n" +
                        "  global_message CLOB NOT NULL,\n" +
                        "  sso_group varchar(256) NOT NULL DEFAULT '',\n" +
                        "  roles CLOB NOT NULL,\n" +
                        "  PRIMARY KEY (uuid),\n" +
                        "  UNIQUE (name)\n" +
                        ") ;";

        public static String creationAiSkill = "CREATE TABLE IF NOT EXISTS ai_skill (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  project_uuid varchar(36) NOT NULL,\n" +
                        "  name varchar(128) NOT NULL,\n" +
                        "  description varchar(1024) NOT NULL,\n" +
                        "  instructions CLOB NOT NULL,\n" +
                        "  allowed_roles CLOB DEFAULT NULL,\n" +
                        "  enabled BOOLEAN NOT NULL DEFAULT TRUE,\n" +
                        "  PRIMARY KEY (uuid)\n" +
                        ") ;";

        public static String creationAiMcpTool = "CREATE TABLE IF NOT EXISTS ai_mcp_tool (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  project_uuid varchar(36) NOT NULL,\n" +
                        "  name varchar(128) NOT NULL,\n" +
                        "  description varchar(1024) NOT NULL,\n" +
                        "  http_method varchar(16) NOT NULL DEFAULT 'GET',\n" +
                        "  url varchar(2048) NOT NULL,\n" +
                        "  parameters_json CLOB NOT NULL,\n" +
                        "  arg_binding varchar(16) NOT NULL DEFAULT 'QUERY',\n" +
                        "  arg_names_json CLOB DEFAULT NULL,\n" +
                        "  fixed_query_json CLOB DEFAULT NULL,\n" +
                        "  catalog_page_path_template varchar(1024) DEFAULT NULL,\n" +
                        "  result_type varchar(128) DEFAULT NULL,\n" +
                        "  result_path varchar(256) DEFAULT NULL,\n" +
                        "  authenticated BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                        "  service_account_uuid varchar(255) DEFAULT NULL,\n" +
                        "  allowed_roles CLOB DEFAULT NULL,\n" +
                        "  enabled BOOLEAN NOT NULL DEFAULT TRUE,\n" +
                        "  PRIMARY KEY (uuid)\n" +
                        ") ;";

        public static String creationAiUiCard = "CREATE TABLE IF NOT EXISTS ai_ui_card (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  project_uuid varchar(36) NOT NULL,\n" +
                        "  name varchar(128) NOT NULL,\n" +
                        "  result_type varchar(128) DEFAULT NULL,\n" +
                        "  template CLOB DEFAULT NULL,\n" +
                        "  enabled BOOLEAN NOT NULL DEFAULT TRUE,\n" +
                        "  PRIMARY KEY (uuid)\n" +
                        ") ;";

        /** Print templates: a query + a Qute/CSV body or a JXLS xlsx (template_file, base64), rendered to pdf/html/csv/xlsx. */
        public static String creationPrintTemplates = "CREATE TABLE IF NOT EXISTS printtemplates (\n" +
                "  uuid varchar(255) NOT NULL,\n" +
                "  name varchar(255) NOT NULL,\n" +
                "  description varchar(255) DEFAULT NULL,\n" +
                "  export_type varchar(10) NOT NULL,\n" +
                "  sql_query VARCHAR(1000000) DEFAULT NULL,\n" +
                "  html_body VARCHAR(1000000) DEFAULT NULL,\n" +
                "  template_file VARCHAR(1000000) DEFAULT NULL,\n" +
                "  template_file_name varchar(255) DEFAULT NULL,\n" +
                "  api_protected boolean DEFAULT false NOT NULL,\n" +
                "  username_field varchar(255) DEFAULT NULL,\n" +
                "  PRIMARY KEY (uuid)\n" +
                ") ;";

        /** Jobs run by ScheduledActionTimer: a cron (frequency) and a JS body with the same bindings as an Action. */
        public static String creationScheduledActions = "CREATE TABLE IF NOT EXISTS scheduledactions (\n" +
                "  uuid varchar(255) NOT NULL,\n" +
                "  name varchar(255) NOT NULL,\n" +
                "  description varchar(255) DEFAULT NULL,\n" +
                "  frequency varchar(255) NOT NULL,\n" +
                "  enabled boolean DEFAULT true NOT NULL,\n" +
                "  body VARCHAR(1000000) DEFAULT NULL,\n" +
                "  next_run varchar(64) DEFAULT NULL,\n" +
                "  last_run varchar(64) DEFAULT NULL,\n" +
                "  last_status varchar(20) DEFAULT NULL,\n" +
                "  last_message VARCHAR(1000000) DEFAULT NULL,\n" +
                "  last_duration_ms bigint DEFAULT NULL,\n" +
                "  PRIMARY KEY (uuid)\n" +
                ") ;";

        public static String creationAiServiceAccount = "CREATE TABLE IF NOT EXISTS ai_service_account (\n" +
                        "  uuid varchar(255) NOT NULL,\n" +
                        "  project_uuid varchar(36) NOT NULL,\n" +
                        "  name varchar(128) NOT NULL,\n" +
                        "  host varchar(512) NOT NULL,\n" +
                        "  realm varchar(128) NOT NULL,\n" +
                        "  client_id varchar(255) NOT NULL,\n" +
                        "  client_secret CLOB NOT NULL,\n" +
                        "  enabled BOOLEAN NOT NULL DEFAULT TRUE,\n" +
                        "  PRIMARY KEY (uuid)\n" +
                        ") ;";

        // H2 2.x rejects bare IDENTITY as a type; use GENERATED … AS IDENTITY (MySQL mode also accepts AUTO_INCREMENT).
        public static String joinTableQuery = "CREATE TABLE IF NOT EXISTS %1$s ( %2$s varchar_ignorecase(100), %3$s varchar_ignorecase(100), id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY )";
}
