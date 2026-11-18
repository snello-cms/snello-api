package io.snello.service.repository.mssql;

public class MssqlConstants {

    public static final String COUNT_QUERY = " SELECT COUNT(*) AS SIZE_OF FROM ";
    public static final String DELETE_FROM = "DELETE FROM ";
    public static final String SELECT_FROM = " SELECT * FROM ";
    public static final String INSERT_INTO = "INSERT INTO ";
    public static final String TRUNCATE_TABLE = "TRUNCATE TABLE ";
    public static final String DROP_TABLE = "DROP TABLE ";
    public static final String VERIFY_TABLE =
            "SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?";

    public static final String joinTableQuery =
            "IF OBJECT_ID(N'%1$s', N'U') IS NULL CREATE TABLE [%1$s] ( " +
            "[%2$s] nvarchar(255) NOT NULL, " +
            "[%3$s] nvarchar(255) NOT NULL, " +
            "[id] int IDENTITY(1,1) NOT NULL, PRIMARY KEY ([id]) );";

    public static String creationActionExtensions =
            "IF OBJECT_ID(N'actions', N'U') IS NULL\n" +
            "CREATE TABLE [actions] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [name] nvarchar(255) NOT NULL,\n" +
            "  [description] nvarchar(255) NOT NULL,\n" +
            "  [metadata_name] nvarchar(255) NOT NULL,\n" +
            "  [condition] nvarchar(255) NULL,\n" +
            "  [phase] nvarchar(10) DEFAULT N'POST',\n" +
            "  [body] nvarchar(max) NULL,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationQueryConditions =
            "IF OBJECT_ID(N'conditions', N'U') IS NULL\n" +
            "CREATE TABLE [conditions] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [metadata_uuid] nvarchar(255) NOT NULL,\n" +
            "  [metadata_name] nvarchar(255) NOT NULL,\n" +
            "  [separator] nvarchar(255) NULL,\n" +
            "  [condition] nvarchar(255) NOT NULL,\n" +
            "  [query_params] nvarchar(255) NULL,\n" +
            "  [sub_query] nvarchar(255) NOT NULL,\n" +
            "  [metadata_multijoin_uuid] nvarchar(255) NOT NULL,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationQueryDocuments =
            "IF OBJECT_ID(N'documents', N'U') IS NULL\n" +
            "CREATE TABLE [documents] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [name] nvarchar(255) NOT NULL,\n" +
            "  [original_name] nvarchar(255) NOT NULL,\n" +
            "  [path] nvarchar(255) NULL,\n" +
            "  [formats] nvarchar(2055) NOT NULL,\n" +
            "  [mimetype] nvarchar(255) NOT NULL,\n" +
            "  [size] int NULL,\n" +
            "  [creation_date] datetime2 DEFAULT CURRENT_TIMESTAMP,\n" +
            "  [table_name] nvarchar(255) NOT NULL,\n" +
            "  [table_key] nvarchar(255) NOT NULL,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationQueryFieldDefinitions =
            "IF OBJECT_ID(N'fielddefinitions', N'U') IS NULL\n" +
            "CREATE TABLE [fielddefinitions] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [metadata_uuid] nvarchar(255) NOT NULL,\n" +
            "  [metadata_name] nvarchar(255) NOT NULL,\n" +
            "  [name] nvarchar(255) NOT NULL,\n" +
            "  [label] nvarchar(255) NOT NULL,\n" +
            "  [description] nvarchar(255) NULL,\n" +
            "  [type] nvarchar(255) NOT NULL,\n" +
            "  [input_type] nvarchar(255) NULL,\n" +
            "  [options] nvarchar(255) NULL,\n" +
            "  [group_name] nvarchar(255) NULL,\n" +
            "  [tab_name] nvarchar(255) NULL,\n" +
            "  [validations] nvarchar(255) NULL,\n" +
            "  [table_key] bit NOT NULL DEFAULT 0,\n" +
            "  [input_disabled] bit NOT NULL DEFAULT 0,\n" +
            "  [function_def] nvarchar(255) NULL,\n" +
            "  [sql_type] nvarchar(255) NULL,\n" +
            "  [sql_definition] nvarchar(255) NULL,\n" +
            "  [default_value] nvarchar(255) NULL,\n" +
            "  [pattern] nvarchar(255) NULL,\n" +
            "  [join_table_name] nvarchar(100) NULL,\n" +
            "  [join_table_key] nvarchar(100) NULL,\n" +
            "  [join_table_select_fields] nvarchar(100) NULL,\n" +
            "  [searchable] bit NOT NULL DEFAULT 0,\n" +
            "  [search_condition] nvarchar(100) NULL,\n" +
            "  [search_field_name] nvarchar(100) NULL,\n" +
            "  [show_in_list] bit NOT NULL DEFAULT 0,\n" +
            "  [view_index] int NULL,\n" +
            "  [mandatory] bit NOT NULL DEFAULT 0,\n" +
            "  [order_num] int NULL,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationQueryMetadatas =
            "IF OBJECT_ID(N'metadatas', N'U') IS NULL\n" +
            "CREATE TABLE [metadatas] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [table_name] nvarchar(255) NOT NULL,\n" +
            "  [icon] nvarchar(100) NULL,\n" +
            "  [select_fields] nvarchar(255) NULL,\n" +
            "  [search_fields] nvarchar(255) NULL,\n" +
            "  [description] nvarchar(255) NULL,\n" +
            "  [alias_table] nvarchar(255) NULL,\n" +
            "  [alias_condition] nvarchar(255) NULL,\n" +
            "  [table_key] nvarchar(255) NOT NULL,\n" +
            "  [table_key_type] nvarchar(255) NULL,\n" +
            "  [table_key_addition] nvarchar(255) NULL,\n" +
            "  [creation_query] nvarchar(255) NULL,\n" +
            "  [order_by] nvarchar(255) NULL,\n" +
            "  [metadata_group] nvarchar(150) NULL,\n" +
            "  [tab_groups] nvarchar(255) NULL,\n" +
            "  [already_exist] bit NOT NULL DEFAULT 0,\n" +
            "  [calendar_enabled] bit NOT NULL DEFAULT 0,\n" +
            "  [calendar_field] nvarchar(255) NULL,\n" +
            "  [calendar_label] nvarchar(255) NULL,\n" +
            "  [created] bit NOT NULL DEFAULT 0,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationQuerySelectQueries =
            "IF OBJECT_ID(N'selectqueries', N'U') IS NULL\n" +
            "CREATE TABLE [selectqueries] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [query_name] nvarchar(255) NOT NULL,\n" +
            "  [with_params] bit NOT NULL DEFAULT 0,\n" +
            "  [select_query] nvarchar(255) NULL,\n" +
            "  [select_query_count] nvarchar(255) NULL,\n" +
            "  [api_protected] bit NOT NULL DEFAULT 0,\n" +
            "  [username_field] nvarchar(255) NULL,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationLinksQueries =
            "IF OBJECT_ID(N'links', N'U') IS NULL\n" +
            "CREATE TABLE [links] (\n" +
            "  [name] nvarchar(255) NOT NULL,\n" +
            "  [labels] nvarchar(255) NOT NULL,\n" +
            "  [metadata_name] nvarchar(100) NULL,\n" +
            "  [metadata_key] nvarchar(255) NULL,\n" +
            "  [metadata_searchable_field] nvarchar(255) NULL,\n" +
            "  [metadata_lock_field] nvarchar(255) NULL,\n" +
            "  [metadata_generated_uuid] nvarchar(255) NULL,\n" +
            "  [created] bit NOT NULL DEFAULT 0,\n" +
            "  PRIMARY KEY ([name])\n" +
            ")";

    public static String creationChatInteractions =
            "IF OBJECT_ID(N'chat_interactions', N'U') IS NULL\n" +
            "CREATE TABLE [chat_interactions] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [conversation_uuid] nvarchar(255) NOT NULL,\n" +
            "  [user_id] nvarchar(255) NULL,\n" +
            "  [user_message] nvarchar(max) NOT NULL,\n" +
            "  [ai_response] nvarchar(max) NOT NULL,\n" +
            "  [creation_date] datetime2 NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationAiTools =
            "IF OBJECT_ID(N'ai_tools', N'U') IS NULL\n" +
            "CREATE TABLE [ai_tools] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [name] nvarchar(255) NOT NULL,\n" +
            "  [method_name] nvarchar(255) NULL,\n" +
            "  [description] nvarchar(max) NULL,\n" +
            "  [sql_query] nvarchar(max) NULL,\n" +
            "  [parameters_schema] nvarchar(max) NULL,\n" +
            "  [active] bit NOT NULL DEFAULT 0,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationHistorySession =
            "IF OBJECT_ID(N'history_session', N'U') IS NULL\n" +
            "CREATE TABLE [history_session] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [session_id] nvarchar(255) NOT NULL,\n" +
            "  [user_id] nvarchar(255) NULL,\n" +
            "  [message_count] bigint NOT NULL DEFAULT 0,\n" +
            "  [started_at] datetime2 NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
            "  PRIMARY KEY ([uuid]),\n" +
            "  CONSTRAINT [uk_history_session_session_id] UNIQUE ([session_id])\n" +
            ")";

    public static String creationHistoryEvent =
            "IF OBJECT_ID(N'history_event', N'U') IS NULL\n" +
            "CREATE TABLE [history_event] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [occurred_at] datetime2 NOT NULL,\n" +
            "  [channel] nvarchar(32) NOT NULL,\n" +
            "  [user_id] nvarchar(255) NULL,\n" +
            "  [username] nvarchar(255) NULL,\n" +
            "  [type] nvarchar(32) NOT NULL,\n" +
            "  [body] nvarchar(max) NULL,\n" +
            "  [session_id] nvarchar(255) NULL,\n" +
            "  [model] nvarchar(16) NULL,\n" +
            "  [recorded_at] datetime2 NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationAiProject =
            "IF OBJECT_ID(N'ai_project', N'U') IS NULL\n" +
            "CREATE TABLE [ai_project] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [name] nvarchar(128) NOT NULL,\n" +
            "  [description] nvarchar(max) NULL,\n" +
            "  [global_message] nvarchar(max) NOT NULL,\n" +
            "  [sso_group] nvarchar(256) NOT NULL DEFAULT N'',\n" +
            "  [roles] nvarchar(max) NOT NULL,\n" +
            "  PRIMARY KEY ([uuid]),\n" +
            "  CONSTRAINT [uk_ai_project_name] UNIQUE ([name])\n" +
            ")";

    public static String creationAiSkill =
            "IF OBJECT_ID(N'ai_skill', N'U') IS NULL\n" +
            "CREATE TABLE [ai_skill] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [project_uuid] nvarchar(36) NOT NULL,\n" +
            "  [name] nvarchar(128) NOT NULL,\n" +
            "  [description] nvarchar(1024) NOT NULL,\n" +
            "  [instructions] nvarchar(max) NOT NULL,\n" +
            "  [allowed_roles] nvarchar(max) NULL,\n" +
            "  [enabled] bit NOT NULL DEFAULT 1,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationAiMcpTool =
            "IF OBJECT_ID(N'ai_mcp_tool', N'U') IS NULL\n" +
            "CREATE TABLE [ai_mcp_tool] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [project_uuid] nvarchar(36) NOT NULL,\n" +
            "  [name] nvarchar(128) NOT NULL,\n" +
            "  [description] nvarchar(1024) NOT NULL,\n" +
            "  [http_method] nvarchar(16) NOT NULL DEFAULT N'GET',\n" +
            "  [url] nvarchar(2048) NOT NULL,\n" +
            "  [parameters_json] nvarchar(max) NOT NULL,\n" +
            "  [arg_binding] nvarchar(16) NOT NULL DEFAULT N'QUERY',\n" +
            "  [arg_names_json] nvarchar(max) NULL,\n" +
            "  [fixed_query_json] nvarchar(max) NULL,\n" +
            "  [catalog_page_path_template] nvarchar(1024) NULL,\n" +
            "  [result_type] nvarchar(128) NULL,\n" +
            "  [result_path] nvarchar(256) NULL,\n" +
            "  [authenticated] bit NOT NULL DEFAULT 0,\n" +
            "  [service_account_uuid] nvarchar(255) NULL,\n" +
            "  [allowed_roles] nvarchar(max) NULL,\n" +
            "  [enabled] bit NOT NULL DEFAULT 1,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationAiUiCard =
            "IF OBJECT_ID(N'ai_ui_card', N'U') IS NULL\n" +
            "CREATE TABLE [ai_ui_card] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [project_uuid] nvarchar(36) NOT NULL,\n" +
            "  [name] nvarchar(128) NOT NULL,\n" +
            "  [result_type] nvarchar(128) NULL,\n" +
            "  [template] nvarchar(max) NULL,\n" +
            "  [enabled] bit NOT NULL DEFAULT 1,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationPrintTemplates =
            "IF OBJECT_ID(N'printtemplates', N'U') IS NULL\n" +
            "CREATE TABLE [printtemplates] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [name] nvarchar(255) NOT NULL,\n" +
            "  [description] nvarchar(255) NULL,\n" +
            "  [export_type] nvarchar(10) NOT NULL,\n" +
            "  [sql_query] nvarchar(max) NULL,\n" +
            "  [html_body] nvarchar(max) NULL,\n" +
            "  [template_file] nvarchar(max) NULL,\n" +
            "  [template_file_name] nvarchar(255) NULL,\n" +
            "  [api_protected] bit NOT NULL DEFAULT 0,\n" +
            "  [username_field] nvarchar(255) NULL,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    /** Workflow pages: HTML/JS served at /p/{name} with the snello-page.js client (human task review pages). */
    public static String creationWorkflowPages =
            "IF OBJECT_ID(N'workflowpages', N'U') IS NULL\n" +
            "CREATE TABLE [workflowpages] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [name] nvarchar(255) NOT NULL,\n" +
            "  [title] nvarchar(255) NULL,\n" +
            "  [description] nvarchar(1000) NULL,\n" +
            "  [workflow_namespace] nvarchar(100) NULL,\n" +
            "  [workflow_name] nvarchar(100) NULL,\n" +
            "  [roles] nvarchar(1000) NULL,\n" +
            "  [body] nvarchar(max) NULL,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    /** Human tasks: a decision a workflow waits for (OPEN -> decided -> CLOSED by the workflow), see /api/humantasks. */
    public static String creationHumanTasks =
            "IF OBJECT_ID(N'humantasks', N'U') IS NULL\n" +
            "CREATE TABLE [humantasks] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [title] nvarchar(500) NULL,\n" +
            "  [description] nvarchar(max) NULL,\n" +
            "  [page_name] nvarchar(255) NULL,\n" +
            "  [workflow_namespace] nvarchar(100) NULL,\n" +
            "  [workflow_name] nvarchar(100) NULL,\n" +
            "  [workflow_version] nvarchar(50) NULL,\n" +
            "  [instance_id] nvarchar(64) NULL,\n" +
            "  [record_table] nvarchar(255) NULL,\n" +
            "  [record_key] nvarchar(255) NULL,\n" +
            "  [decisions] nvarchar(1000) NULL,\n" +
            "  [roles] nvarchar(1000) NULL,\n" +
            "  [status] nvarchar(20) NOT NULL,\n" +
            "  [decision] nvarchar(100) NULL,\n" +
            "  [decision_comment] nvarchar(max) NULL,\n" +
            "  [decided_by] nvarchar(255) NULL,\n" +
            "  [decided_at] datetime2 NULL,\n" +
            "  [created_at] datetime2 NULL,\n" +
            "  [closed_at] datetime2 NULL,\n" +
            "  [data] nvarchar(max) NULL,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationLayouts =
            "IF OBJECT_ID(N'layouts', N'U') IS NULL\n" +
            "CREATE TABLE [layouts] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [name] nvarchar(255) NOT NULL,\n" +
            "  [page_type] nvarchar(10) NOT NULL,\n" +
            "  [metadata_uuid] nvarchar(255) NOT NULL,\n" +
            "  [metadata_name] nvarchar(255) NOT NULL,\n" +
            "  [json_data] nvarchar(max) NULL,\n" +
            "  [updated_at] nvarchar(32) NOT NULL,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationLayoutsIndex =
            "IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_layouts_metadata_page' AND object_id = OBJECT_ID(N'layouts'))\n" +
            "CREATE INDEX [idx_layouts_metadata_page] ON [layouts] ([metadata_uuid], [page_type])";

    public static String creationScheduledActions =
            "IF OBJECT_ID(N'scheduledactions', N'U') IS NULL\n" +
            "CREATE TABLE [scheduledactions] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [name] nvarchar(255) NOT NULL,\n" +
            "  [description] nvarchar(255) NULL,\n" +
            "  [frequency] nvarchar(255) NOT NULL,\n" +
            "  [enabled] bit NOT NULL DEFAULT 1,\n" +
            "  [body] nvarchar(max) NULL,\n" +
            "  [next_run] nvarchar(64) NULL,\n" +
            "  [last_run] nvarchar(64) NULL,\n" +
            "  [last_status] nvarchar(20) NULL,\n" +
            "  [last_message] nvarchar(max) NULL,\n" +
            "  [last_duration_ms] bigint NULL,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationAiServiceAccount =
            "IF OBJECT_ID(N'ai_service_account', N'U') IS NULL\n" +
            "CREATE TABLE [ai_service_account] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [project_uuid] nvarchar(36) NOT NULL,\n" +
            "  [name] nvarchar(128) NOT NULL,\n" +
            "  [host] nvarchar(512) NOT NULL,\n" +
            "  [realm] nvarchar(128) NOT NULL,\n" +
            "  [client_id] nvarchar(255) NOT NULL,\n" +
            "  [client_secret] nvarchar(max) NOT NULL,\n" +
            "  [enabled] bit NOT NULL DEFAULT 1,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

    public static String creationQuerySystemEventLogs =
            "IF OBJECT_ID(N'systemeventlogs', N'U') IS NULL\n" +
            "CREATE TABLE [systemeventlogs] (\n" +
            "  [uuid] nvarchar(255) NOT NULL,\n" +
            "  [query_name] nvarchar(255) NOT NULL,\n" +
            "  [with_params] bit NOT NULL DEFAULT 0,\n" +
            "  [select_query] nvarchar(255) NULL,\n" +
            "  PRIMARY KEY ([uuid])\n" +
            ")";

}
