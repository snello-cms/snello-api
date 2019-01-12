package io.snello.cms.management;

public class AppConstants {

    public static final String BASE_PATH = "/";
    public static final String API_PATH = "/api";

    public static final String CONDITIONS = "conditions";
    public static final String DATALISTS = "datalist";
    public static final String DOCUMENTS = "documents";
    public static final String FIELD_DEFINITIONS = "fielddefinitions";
    public static final String METADATAS = "metadatas";


    public static final String CONDITIONS_PATH = BASE_PATH + CONDITIONS;
    public static final String DATALIST_PATH = BASE_PATH + DATALISTS;
    public static final String DOCUMENTS_PATH = BASE_PATH + DOCUMENTS;
    public static final String FIELD_DEFINITIONS_PATH = BASE_PATH + FIELD_DEFINITIONS;
    public static final String METADATA_PATH = BASE_PATH + METADATAS;

    public static final String _0 = "0";
    public static final String _10 = "10";
    public static final String TABLE_PATH_PARAM = "/{table}";
    public static final String EXTRA_PATH_PARAM = "/{+path}";
    public static final String UUID = "uuid";
    public static final String UUID_PATH_PARAM = "/{" + UUID + "}";
    public static final String UUID_PATH_PARAM_CREATE = UUID_PATH_PARAM + "/create";
    public static final String DOWNLOAD_PATH = "/download";
    public static final String SORT_PARAM = "_sort";
    public static final String DELETE_PARAM = "delete";
    public static final String LIMIT_PARAM = "_limit";
    public static final String START_PARAM = "_start";
    public static final String SIZE_HEADER_PARAM = "size";


    public static final String SYSTEM_DOCUMENTS_BASE_PATH = "micronaut.router.static-resources.default.paths[0]";


    public static final String DOCUMENT_NAME = "name";
    public static final String DOCUMENT_ORIGINAL_NAME = "original_name";
    public static final String DOCUMENT_PATH = "path";
    public static final String DOCUMENT_MIME_TYPE = "mimetype";

    public static final String DOCUMENT_SIZE = "size";
    public static final String DOCUMENT_TABLE = "table_name";
    public static final String DOCUMENT_TABLE_KEY = "table_key";

}
