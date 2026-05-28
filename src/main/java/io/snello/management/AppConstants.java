package io.snello.management;

public class AppConstants {

    public static final String BASE_PATH = "/";
    public static final String API_PATH = "/api";
    public static final String PAGES_PATH = API_PATH + "/p";

    public static final String ACTIONS = "actions";
    public static final String BACKUP = "backup";
    public static final String CONDITIONS = "conditions";
    public static final String DATALISTS = "datalist";
    public static final String DOCUMENTS = "documents";
    public static final String FIELD_DEFINITIONS = "fielddefinitions";
    public static final String LINKS = "links";
    public static final String METADATAS = "metadatas";
    public static final String CHAT_INTERACTIONS = "chat_interactions";
    public static final String PUBLIC_DATA = "publicdata";
    public static final String SELECT_QUERY = "selectqueries";
    public static final String AI_TOOLS = "ai_tools";


    public static final String ACTIONS_PATH = API_PATH + BASE_PATH + ACTIONS;
    public static final String AI_TOOLS_PATH = API_PATH + BASE_PATH + AI_TOOLS;
    public static final String BACKUP_PATH = API_PATH + BASE_PATH + BACKUP;
    public static final String CACHE_DOCUMENTS_PATH = API_PATH + BASE_PATH + DOCUMENTS + "/cache";
    public static final String CHAT_INTERACTIONS_PATH = API_PATH + BASE_PATH + CHAT_INTERACTIONS;
    public static final String CHAT_PATH = API_PATH + BASE_PATH + "chat";
    public static final String CONDITIONS_PATH = API_PATH + BASE_PATH + CONDITIONS;
    public static final String DATALIST_PATH = API_PATH + BASE_PATH + DATALISTS;
    public static final String DOCUMENTS_PATH = API_PATH + BASE_PATH + DOCUMENTS;
    public static final String FIELD_DEFINITIONS_PATH = API_PATH + BASE_PATH + FIELD_DEFINITIONS;
    public static final String LINKS_PATH = API_PATH + BASE_PATH + LINKS;
    public static final String METADATA_PATH = API_PATH + BASE_PATH + METADATAS;
    public static final String SELECT_QUERY_PATH = API_PATH + BASE_PATH + SELECT_QUERY;

    public static final String _0 = "0";
    public static final String _10 = "10";
    public static final String TABLE_PATH_PARAM = "/{table}";
    public static final String EXTRA_PATH_PARAM = "/{path:.*}";
    public static final String UUID = "uuid";
    public static final String UUID_PATH_PARAM = "/{" + UUID + "}";
    public static final String UUID_PATH_PARAM_CREATE = UUID_PATH_PARAM + "/create";
    public static final String UUID_PATH_PARAM_DELETE = UUID_PATH_PARAM + "/delete";
    public static final String UUID_PATH_PARAM_TRUNCATE = UUID_PATH_PARAM + "/truncate";
    public static final String UUID_PATH_PARAM_VERIFY = UUID_PATH_PARAM + "/verify";
    public static final String UUID_PATH_PARAM_CHANGE = "/change" + UUID_PATH_PARAM;
    public static final String UUID_PATH_PARAM_RESET = "/reset" + UUID_PATH_PARAM;
    public static final String UUID_PATH_PARAM_CROP = UUID_PATH_PARAM + "/crop";
    public static final String UUID_PATH_PARAM_MOGRIFY = UUID_PATH_PARAM + "/mogrify";
    public static final String UUID_PATH_PARAM_RESIZE = UUID_PATH_PARAM + "/resize";
    public static final String DOWNLOAD_PATH = "/download";
    public static final String WEBP_PATH = "/webp";
    public static final String SORT_PARAM = "_sort";
    public static final String DELETE_PARAM = "delete";
    public static final String LIMIT_PARAM = "_limit";
    public static final String START_PARAM = "_start";
    public static final String SIZE_HEADER_PARAM = "size";
    public static final String TOTAL_COUNT_HEADER_PARAM = "x-total-count";

    public static final String SYSTEM_DOCUMENTS_BASE_PATH = "files.paths";
    public static final String JDBC_DB = "datasources.default.database";


    public static final String DOCUMENT_NAME = "name";
    public static final String FORMATS = "formats";
    public static final String DOCUMENT_ORIGINAL_NAME = "original_name";
    public static final String DOCUMENT_PATH = "path";
    public static final String DOCUMENT_MIME_TYPE = "mimetype";

    public static final String SIZE = "size";
    public static final String TABLE_NAME = "table_name";
    public static final String TABLE_KEY = "table_key";
    public static final String CREATED = "created";

    public static final String CREATION_DATE = "creation_date";
    public static final String LAST_UPDATE_DATE = "last_update_date";
    public static final String ROLE = "role";
    public static final String NAME = "name";
    public static final String JOIN = "join";
    public static final String LOOKUP = "lookup";
    public static final String STRING = "string";
    public static final String TRUE = "true";
    public static final String ZIP = "zip";
    public static final String DOT_ZIP = ".zip";
    public static final String FILES = "files";
    public static final String EMPTY = "";
    public static final String COMMA = ",";
    public static final String COMMA_MIXED = ",!|;";
    public static final String QUERY_NAME = "query_name";
    public static final String FILE_DOT_DOT = "file:";
    public static final String SORT_DOT_DOT = "sort: ";
    public static final String LIMIT_DOT_DOT = "limit: ";
    public static final String START_DOT_DOT = "start: ";

    public static final String MSG_NAME_PARAM_IS_EMPTY = "name param is empty";
    public static final String MSG_NAME_PARAM_IS_RESERVED = "name param is a reserved word";
    public static final String MSG_PATH_IS_EMPTY = "path is empty";
    public static final String MSG_TABLE_NAME_IS_EMPTY = "table name is empty";
    public static final String MSG_TABLE_NAME_IS_RESERVED = "table name  is a reserved word";
    public static final String MSG_QUERY_NAME_IS_EMPTY = "query name is empty";
    public static final String MSG_QUERY_NAME_IS_RESERVED = "query name is a reserved word";
    public static final String MSG_USERNAME_IS_EMPTY = "username is empty";
    public static final String MSG_NOT_IMPLEMENTED = "not implemented! (please delete and after create)";

    public static final String DELETE_FROM_FD = "DELETE FROM fielddefinitions WHERE metadata_uuid = ? ";

}
