package io.snello.service.repository.postgresql;

public class PostgresqlConstants {
    public static final String COUNT_QUERY = " SELECT COUNT(*) AS SIZE_OF FROM ";
    public static final String DELETE_FROM = "DELETE FROM ";
    public static final String SHOW_TABLES = "SELECT to_regclass";
    public static final String SELECT_FROM = " SELECT * FROM ";
    public static final String INSERT_INTO = "INSERT INTO ";
    public static final String TRUNCATE_TABLE = "TRUNCATE TABLE ";
    public static final String DROP_TABLE = "DROP TABLE ";

    public static final String ESCAPE = "";


    public static String creationActionExtensions = "CREATE TABLE IF NOT EXISTS actions (\n" +
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
                                                   "  query_params varchar(255) NOT NULL,\n" +
                                                   "  sub_query varchar(255) NOT NULL,\n" +
                                                   "  metadata_multijoin_uuid varchar(255) NOT NULL,\n" +
                                                   "  PRIMARY KEY (uuid)\n" +
                                                   ") ";

    public static String creationQueryDocuments = "CREATE TABLE IF NOT EXISTS documents (\n" +
                                                  "  uuid varchar(255) NOT NULL,\n" +
                                                  "  name varchar(255) NOT NULL,\n" +
                                                  "  original_name varchar(255) NOT NULL,\n" +
                                                  "  path varchar(255) ,\n" +
                                                  "  formats varchar(2055) ,\n" +
                                                  "  mimetype varchar(255) NOT NULL,\n" +
                                                  "  size NUMERIC(12),\n" +
                                                  "  creation_date timestamp DEFAULT CURRENT_TIMESTAMP,\n" +
                                                  "  table_name varchar(255) NOT NULL,\n" +
                                                  "  table_key varchar(255) NOT NULL,\n" +
                                                  "  PRIMARY KEY (uuid)\n" +
                                                  ") ";


    public static String creationQueryFieldDefinitions = "CREATE TABLE IF NOT EXISTS fielddefinitions (\n" +
                                                         "  uuid varchar(255) NOT NULL,\n" +
                                                         "  metadata_uuid varchar(255) NOT NULL,\n" +
                                                         "  metadata_name varchar(255) NOT NULL,\n" +
                                                         "  name varchar(255) NOT NULL,\n" +
                                                         "  label varchar(255)  NOT NULL,\n" +
                                                         "  description varchar(255) DEFAULT NULL,\n" +
                                                         "  type varchar(255) NOT NULL,\n" +
                                                         "  input_type varchar(255) DEFAULT NULL,\n" +
                                                         "  options varchar(255) DEFAULT NULL,\n" +
                                                         "  group_name varchar(255) DEFAULT NULL,\n" +
                                                         "  tab_name varchar(255) DEFAULT NULL,\n" +
                                                         "  validations varchar(255) DEFAULT NULL,\n" +
                                                         "  table_key BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                                                         "  input_disabled BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                                                         "  function_def varchar(255) DEFAULT NULL ,\n" +
                                                         "  sql_type varchar(255) DEFAULT NULL,\n" +
                                                         "  sql_definition varchar(255) DEFAULT NULL,\n" +
                                                         "  default_value varchar(255) DEFAULT NULL,\n" +
                                                         "  pattern varchar(255) DEFAULT NULL,\n" +
                                                         "  join_table_name varchar(100) DEFAULT NULL,\n" +
                                                         "  join_table_key varchar(100) DEFAULT NULL,\n" +
                                                         "  join_table_select_fields varchar(100) DEFAULT NULL,\n" +
                                                         "  searchable BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                                                         "  search_condition varchar(100) DEFAULT NULL,\n" +
                                                         "  search_field_name varchar(100) DEFAULT NULL,\n" +
                                                         "  show_in_list BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                                                         "  view_index NUMERIC(12),\n" +
                                                         "  mandatory BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                                                         "  order_num NUMERIC(12),\n" +
                                                         "  PRIMARY KEY (uuid)\n" +
                                                         ") ";

    // Compatibility fix for legacy schemas where order_num was created with too-small precision.
    public static String upgradeFieldDefinitionsOrderNum = "ALTER TABLE IF EXISTS fielddefinitions ALTER COLUMN order_num TYPE NUMERIC(12)";

        // Compatibility fix for legacy schemas with varchar limits too small for metadata import payloads.
        public static String upgradeMetadataAndFieldDefinitionsTextColumns =
            "ALTER TABLE IF EXISTS metadatas " +
            "ALTER COLUMN select_fields TYPE TEXT, " +
            "ALTER COLUMN search_fields TYPE TEXT, " +
            "ALTER COLUMN description TYPE TEXT, " +
            "ALTER COLUMN alias_condition TYPE TEXT, " +
            "ALTER COLUMN creation_query TYPE TEXT, " +
            "ALTER COLUMN order_by TYPE TEXT, " +
            "ALTER COLUMN tab_groups TYPE TEXT; " +
            "ALTER TABLE IF EXISTS fielddefinitions " +
            "ALTER COLUMN label TYPE TEXT, " +
            "ALTER COLUMN description TYPE TEXT, " +
            "ALTER COLUMN options TYPE TEXT, " +
            "ALTER COLUMN validations TYPE TEXT, " +
            "ALTER COLUMN function_def TYPE TEXT, " +
            "ALTER COLUMN sql_type TYPE TEXT, " +
            "ALTER COLUMN sql_definition TYPE TEXT, " +
            "ALTER COLUMN default_value TYPE TEXT, " +
            "ALTER COLUMN pattern TYPE TEXT, " +
            "ALTER COLUMN join_table_select_fields TYPE TEXT, " +
            "ALTER COLUMN search_condition TYPE TEXT, " +
            "ALTER COLUMN search_field_name TYPE TEXT";

    public static String creationQueryMetadatas = "CREATE TABLE IF NOT EXISTS metadatas (\n" +
                                                  "  uuid varchar(255) NOT NULL,\n" +
                                                  "  table_name varchar(255) NOT NULL,\n" +
                                                  "  icon varchar(100) DEFAULT NULL,\n" +
                                                  "  select_fields varchar(255) DEFAULT NULL,\n" +
                                                  "  search_fields varchar(255) DEFAULT NULL,\n" +
                                                  "  description varchar(255) DEFAULT NULL,\n" +
                                                  "  alias_table varchar(255) DEFAULT NULL,\n" +
                                                  "  alias_condition varchar(255) DEFAULT NULL,\n" +
                                                  "  table_key varchar(255) NOT NULL,\n" +
                                                  "  table_key_type varchar(255) DEFAULT NULL,\n" +
                                                  "  table_key_addition varchar(255) DEFAULT NULL,\n" +
                                                  "  creation_query varchar(255) DEFAULT NULL,\n" +
                                                  "  order_by varchar(255) DEFAULT NULL,\n" +
                                                  "  metadata_group varchar(150) DEFAULT NULL,\n" +
                                                  "  tab_groups varchar(255) DEFAULT NULL,\n" +
                                                  "  already_exist BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                                                  "  api_protected BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                                                  "  username_field varchar(255) DEFAULT NULL,\n" +
                                                  "  calendar_enabled BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                                                  "  calendar_field varchar(255) DEFAULT NULL,\n" +
                                                  "  calendar_label varchar(255) DEFAULT NULL,\n" +
                                                  "  created BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                                                  "  PRIMARY KEY (uuid)\n" +
                                                  ") ";

    public static String creationQuerySelectQueries = "CREATE TABLE IF NOT EXISTS selectqueries (\n" +
                                                      "  uuid varchar(255) NOT NULL,\n" +
                                                      "  query_name varchar(255) NOT NULL,\n" +
                                                      "  with_params boolean DEFAULT false,\n" +
                                                      "  select_query varchar(255) DEFAULT NULL,\n" +
                                                      "  select_query_count varchar(255) DEFAULT NULL,\n" +
                                                      "  PRIMARY KEY (uuid)\n" +
                                                      ") ";

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
                                                ") ";

    public static String creationChatInteractions = "CREATE TABLE IF NOT EXISTS chat_interactions (\n" +
                                                    "  uuid varchar(255) NOT NULL,\n" +
                                                    "  conversation_uuid varchar(255) NOT NULL,\n" +
                                                    "  user_id varchar(255) DEFAULT NULL,\n" +
                                                    "  user_message text NOT NULL,\n" +
                                                    "  ai_response text NOT NULL,\n" +
                                                    "  creation_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                                                    "  PRIMARY KEY (uuid)\n" +
                                                    ") ";

    // Upgrade legacy schemas with varchar(4000) limits on chat messages.
    public static String upgradeChatInteractionsTextColumns = "ALTER TABLE IF EXISTS chat_interactions " +
                                                               "ALTER COLUMN user_message TYPE TEXT, " +
                                                               "ALTER COLUMN ai_response TYPE TEXT";

    public static String creationAiTools = "CREATE TABLE IF NOT EXISTS ai_tools (\n" +
                                              "  uuid varchar(255) NOT NULL,\n" +
                                              "  name varchar(255) NOT NULL,\n" +
                                              "  method_name varchar(255) DEFAULT NULL,\n" +
                                              "  description text DEFAULT NULL,\n" +
                                              "  sql_query text DEFAULT NULL,\n" +
                                              "  parameters_schema text DEFAULT NULL,\n" +
                                              "  active BOOLEAN NOT NULL DEFAULT FALSE,\n" +
                                              "  PRIMARY KEY (uuid)\n" +
                                              ") ";

    public static String joinTableQuery = "CREATE TABLE IF NOT EXISTS %1$s ( " +
                                          "%2$s varchar(255) NOT NULL, " +
                                          "%3$s varchar(255) NOT NULL, " +
                                          "id SERIAL PRIMARY KEY )";

}
