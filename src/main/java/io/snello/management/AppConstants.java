package io.snello.management;

public class AppConstants {

    public static final String BASE_PATH = "/";
    public static final String API_PATH = "/api";

    public static final String CONDITIONS = "conditions";
    public static final String DATALISTS = "datalist";
    public static final String DOCUMENTS = "documents";
    public static final String FIELD_DEFINITIONS = "fielddefinitions";
    public static final String LINKS = "links";
    public static final String METADATAS = "metadatas";
    public static final String PUBLIC_DATA = "publicdata";
    public static final String SELECT_QUERY = "selectqueries";
    public static final String USERS = "users";
    public static final String CHANGE_PASSWORD = "changepassword";
    public static final String ROLES = "roles";
    public static final String URL_MAP_RULES = "urlmaprules";
    public static final String USER_ROLES = "userroles";
    public static final String CHANGE_PASSWORD_TOKENS = "changepasswordtokens";
    public static final String IDENTITY = "identity";


    public static final String SWAGGER_PATH = "/swagger";

    public static final String CONDITIONS_PATH = API_PATH + BASE_PATH + CONDITIONS;
    public static final String DATALIST_PATH = API_PATH + BASE_PATH + DATALISTS;
    public static final String DOCUMENTS_PATH = API_PATH + BASE_PATH + DOCUMENTS;
    public static final String FIELD_DEFINITIONS_PATH = API_PATH + BASE_PATH + FIELD_DEFINITIONS;
    public static final String LINKS_PATH = API_PATH + BASE_PATH + LINKS;
    public static final String METADATA_PATH = API_PATH + BASE_PATH + METADATAS;
    public static final String PUBLIC_DATA_PATH = API_PATH + BASE_PATH + PUBLIC_DATA;
    public static final String SELECT_QUERY_PATH = API_PATH + BASE_PATH + SELECT_QUERY;
    public static final String URL_MAP_RULES_PATH = API_PATH + BASE_PATH + URL_MAP_RULES;
    public static final String IDENTITY_PATH = API_PATH + BASE_PATH + IDENTITY;

    public static final String USERS_PATH = API_PATH + BASE_PATH + USERS;
    public static final String CHANGE_PASSORD_PATH = API_PATH + BASE_PATH + CHANGE_PASSWORD;
    public static final String ROLES_PATH = API_PATH + BASE_PATH + ROLES;
    public static final String USER_ROLES_PATH = API_PATH + BASE_PATH + USER_ROLES;

    public static final String DATA_LIST_NAMES = "/names";
    public static final String DATA_LIST_METADATA_NAMES = "/metadata/{name}";
    public static final String DATA_LIST_FIELD_DEFINITIONS = "/metadata/{name}/fielddefinitions";
    public static final String DATA_LIST_CONDITIONS = "/metadata/{name}/conditions";

    public static final String _0 = "0";
    public static final String _10 = "10";
    public static final String TABLE_PATH_PARAM = "/{table}";
    public static final String EXTRA_PATH_PARAM = "/{+path}";
    public static final String UUID = "uuid";
    public static final String UUID_PATH_PARAM = "/{" + UUID + "}";
    public static final String UUID_PATH_PARAM_CREATE = UUID_PATH_PARAM + "/create";
    public static final String UUID_PATH_PARAM_DELETE = UUID_PATH_PARAM + "/delete";
    public static final String UUID_PATH_PARAM_TRUNCATE = UUID_PATH_PARAM + "/truncate";
    public static final String UUID_PATH_PARAM_VERIFY = UUID_PATH_PARAM + "/verify";
    public static final String UUID_PATH_PARAM_CHANGE = UUID_PATH_PARAM + "/change";
    public static final String DOWNLOAD_PATH = "/download";
    public static final String SORT_PARAM = "_sort";
    public static final String DELETE_PARAM = "delete";
    public static final String LIMIT_PARAM = "_limit";
    public static final String START_PARAM = "_start";
    public static final String SIZE_HEADER_PARAM = "size";
    public static final String TOTAL_COUNT_HEADER_PARAM = "x-total-count";

    public static final String DB_TYPE = "micronaut.db.type";
    public static final String EMAIL_TYPE = "micronaut.email.type";
    public static final String STORAGE_TYPE = "micronaut.storage.type";
    public static final String SYSTEM_DOCUMENTS_BASE_PATH = "micronaut.router.static-resources.files.paths";
    public static final String EMAIL_SMTP_PORT = "micronaut.email.smtp-port";
    public static final String EMAIL_SMTP_AUTH = "micronaut.email.smtp-auth";
    public static final String EMAIL_SMTP_STARTSSL_ENABLE = "micronaut.email.starttls-enable";
    public static final String EMAIL_SMTP_HOST = "micronaut.email.smtp-host";
    public static final String EMAIL_SMTP_USERNAME = "micronaut.email.smtp-username";
    public static final String EMAIL_SMTP_PASSWORD = "micronaut.email.smtp-password";
    public static final String S3_ENDPOINT = "micronaut.s3.endpoint";
    public static final String S3_ACCESS_KEY = "micronaut.s3.accesskey";
    public static final String S3_SECRET_KEY = "micronaut.s3.secretkey";
    public static final String S3_REGION = "micronaut.s3.region";
    public static final String S3_BUCKET_NAME = "micronaut.s3.bucketname";


    public static final String DB_NAME = "micronaut.router.static-resources.default.paths[0]";


    public static final String DOCUMENT_NAME = "name";
    public static final String DOCUMENT_ORIGINAL_NAME = "original_name";
    public static final String DOCUMENT_PATH = "path";
    public static final String DOCUMENT_MIME_TYPE = "mimetype";

    public static final String SIZE = "size";
    public static final String TABLE_NAME = "table_name";
    public static final String TABLE_KEY = "table_key";
    public static final String CREATED = "created";

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String CONFIRM_PASSWORD = "confirm_password";
    public static final String EMAIL = "email";
    public static final String TOKEN = "token";
    public static final String CREATION_DATE = "creation_date";
    public static final String LAST_UPDATE_DATE = "last_update_date";
    public static final String ROLE = "role";
    public static final String NAME = "name";
    public static final String JOIN = "join";
    public static final String STRING = "string";
    public static final String TRUE = "true";
    public static final String ZIP = "zip";
    public static final String DOT_ZIP = ".zip";
    public static final String FILES = "files";
    public static final String EMPTY = "";
    public static final String COMMA = ",";
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



}
