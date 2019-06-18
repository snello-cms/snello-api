package io.snello.dsl;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.DefaultHttpClient;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken;
import io.reactivex.Flowable;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Optional;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static io.snello.management.AppConstants.FIELD_DEFINITIONS_PATH;
import static io.snello.management.AppConstants.METADATA_PATH;

public class SnelloBuilder {

    Logger logger = LoggerFactory.getLogger(getClass());

    static String HOST = "http://localhost:8080";
    static String login_uri = "/login";
    static String metadata_uri = METADATA_PATH;
    static String fielddefinition_uri = FIELD_DEFINITIONS_PATH;

    private String token;
    private String username;
    private String password;

    private MetadataDsl metadataDsl;


    public SnelloBuilder(String username, String password) {
        this.username = username;
        this.password = password;

    }

    public SnelloBuilder(String token) {
        this.token = token;
    }

    public SnelloBuilder login() throws Exception {
        isLoggable();
        logger.info("login: " + login_uri);
        try (RxHttpClient client = new DefaultHttpClient(new URL(HOST))) {
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(this.username, this.password);
            HttpRequest request = HttpRequest.POST("/login", credentials);
            BearerAccessRefreshToken bearerAccessRefreshToken = client.toBlocking().retrieve(request, BearerAccessRefreshToken.class);
            if (bearerAccessRefreshToken != null) {
                this.token = bearerAccessRefreshToken.getAccessToken();
                logger.info("token: " + this.token);
            }
        } catch (Exception e) {
            logger.error("login error:", e);
        }
        return this;
    }

    private void isLoggable() throws Exception {
        if (this.username == null && this.password == null) {
            throw new Exception("username or password is invalid");
        }
    }

    //METADATA
    public SnelloBuilder metadata(String table_name, String table_key) throws Exception {
        if (this.metadataDsl != null) {
            if (this.metadataDsl.uuid != null) {
                logger.info("we need to create metadata");
                create();
            } else {
                throw new Exception("metadata already exist!");
            }
        }
        logger.info("new metadata");
        this.metadataDsl = new MetadataDsl();
        this.metadataDsl.metadata.table_name = table_name;
        this.metadataDsl.metadata.table_key = table_key;
        this.metadataDsl.metadata.table_key_type = "uuid";
        return this;
    }

    public SnelloBuilder autoincrement() throws Exception {
        isMetadatable();
        this.metadataDsl.metadata.table_key_type = "autoincrement";
        return this;
    }

    public SnelloBuilder icon(String icon) throws Exception {
        isMetadatable();
        this.metadataDsl.metadata.icon = icon;
        return this;
    }

    public SnelloBuilder slug(String table_key_addition) throws Exception {
        isMetadatable();
        this.metadataDsl.metadata.table_key_type = "slug";
        this.metadataDsl.metadata.table_key_addition = table_key_addition;
        return this;
    }


    private void isMetadatable() throws Exception {
        if (this.metadataDsl.metadata == null) {
            throw new Exception("matadata is null");
        }
    }


    private SnelloBuilder createMetadata() throws Exception {
        isMetadatable();
        logger.info("metadata: " + metadata_uri);
        try (RxHttpClient client = new DefaultHttpClient(new URL(HOST))) {
            Flowable<HttpResponse<Metadata>> call = client.exchange(
                    POST(metadata_uri, this.metadataDsl.metadata)
                            .header("Authorization", "Bearer " + this.token), Metadata.class
            );
            HttpResponse<Metadata> response = call.blockingFirst();
            Optional<Metadata> metadataResponse = response.getBody(Metadata.class);
            if (response.getStatus().equals(HttpStatus.OK)) {
                if (metadataResponse.isPresent()) {
                    Metadata metadata = metadataResponse.get();
                    if (metadata != null && metadata.uuid != null) {
                        this.metadataDsl.uuid(metadata.uuid);
                        logger.info(metadata.toString());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("create metadata error: ", e);
        }
        return this;
    }


    private SnelloBuilder createMetadataTable() throws Exception {
        isMetadatable();
        logger.info("metadata: " + metadata_uri);
        try (RxHttpClient client = new DefaultHttpClient(new URL(HOST))) {
            Flowable<HttpResponse<Metadata>> call = client.exchange(
                    GET(metadata_uri + "/" + this.metadataDsl.uuid + "/create")
                            .header("Authorization", "Bearer " + this.token), Metadata.class
            );
            HttpResponse<Metadata> response = call.blockingFirst();
            Optional<Metadata> metadataResponse = response.getBody(Metadata.class);
            if (response.getStatus().equals(HttpStatus.OK)) {
                if (metadataResponse.isPresent()) {
                    Metadata metadata = metadataResponse.get();
                    if (metadata != null && metadata.uuid != null) {
                        this.metadataDsl.uuid(metadata.uuid);
                        logger.info(metadata.toString());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("create metadata error: ", e);
        }
        return this;
    }

    public SnelloBuilder fieldDefinition(String name, String label) {
        if (this.metadataDsl.fieldDefinition != null) {
            logger.info("rotate field definition");
            this.metadataDsl.rotate();
        }
        logger.info("new fieldDefinition");
        this.metadataDsl.fieldDefinition = new FieldDefinition();
        this.metadataDsl.fieldDefinition.name = name;
        this.metadataDsl.fieldDefinition.label = label;
        this.metadataDsl.fieldDefinition.type = "input";
        this.metadataDsl.fieldDefinition.input_type = "text";
        return this;
    }

    public SnelloBuilder fieldDefinition(String name, String label, String type) throws Exception {
        fieldDefinition(name, label);
        switch (type) {
            case "text":
                return text();
            case "number":
                return number();
            case "checkbox":
                return checkbox();
            case "textarea":
                return textarea();
            case "media":
                return media();
            case "tags":
                return tags();
            case "password":
                return password();
            case "email":
                return email();
            case "date":
                return date();
            case "datetime":
                return datetime();
            case "time":
                return time();
            default:
                throw new Exception("field definition type is not valid!");
        }
    }

    public SnelloBuilder select(String options) {
        this.metadataDsl.fieldDefinition.type = "select";
        this.metadataDsl.fieldDefinition.options = options;
        return this;
    }

    public SnelloBuilder join(String join_table_name,
                              String join_table_key,
                              String join_table_select_fields) {
        this.metadataDsl.fieldDefinition.type = "join";
        this.metadataDsl.fieldDefinition.join_table_name = join_table_name;
        this.metadataDsl.fieldDefinition.join_table_key = join_table_key;
        this.metadataDsl.fieldDefinition.join_table_select_fields = join_table_select_fields;
        return this;
    }

    public SnelloBuilder multijoin(String join_table_name,
                                   String join_table_key,
                                   String join_table_select_fields) {
        this.metadataDsl.fieldDefinition.type = "multijoin";
        this.metadataDsl.fieldDefinition.join_table_name = join_table_name;
        this.metadataDsl.fieldDefinition.join_table_key = join_table_key;
        this.metadataDsl.fieldDefinition.join_table_select_fields = join_table_select_fields;
        return this;
    }

    public SnelloBuilder text() {
        this.metadataDsl.fieldDefinition.type = "input";
        this.metadataDsl.fieldDefinition.input_type = "text";
        return this;
    }

    public SnelloBuilder number() {
        this.metadataDsl.fieldDefinition.type = "input";
        this.metadataDsl.fieldDefinition.input_type = "number";
        return this;
    }

    public SnelloBuilder checkbox() {
        this.metadataDsl.fieldDefinition.type = "checkbox";
        return this;
    }

    public SnelloBuilder textarea() {
        this.metadataDsl.fieldDefinition.type = "textarea";
        return this;
    }

    public SnelloBuilder media() {
        this.metadataDsl.fieldDefinition.type = "media";
        return this;
    }

    public SnelloBuilder tags() {
        this.metadataDsl.fieldDefinition.type = "tags";
        return this;
    }

    public SnelloBuilder password() {
        this.metadataDsl.fieldDefinition.type = "input";
        this.metadataDsl.fieldDefinition.input_type = "password";
        return this;
    }

    public SnelloBuilder email() {
        this.metadataDsl.fieldDefinition.type = "input";
        this.metadataDsl.fieldDefinition.input_type = "email";
        return this;
    }

    public SnelloBuilder date() {
        this.metadataDsl.fieldDefinition.type = "date";
        return this;
    }


    public SnelloBuilder datetime() {
        this.metadataDsl.fieldDefinition.type = "datetime";
        return this;
    }

    public SnelloBuilder time() {
        this.metadataDsl.fieldDefinition.type = "time";
        return this;
    }


    public SnelloBuilder show_in_list() {
        this.metadataDsl.fieldDefinition.show_in_list = true;
        return this;
    }

    public SnelloBuilder isSearchable(String search_Condition, String search_field_name) {
        this.metadataDsl.fieldDefinition.searchable = true;
        this.metadataDsl.fieldDefinition.search_condition = search_Condition;
        this.metadataDsl.fieldDefinition.search_field_name = search_field_name;
        return this;
    }

    private SnelloBuilder createFieldDefinitions() throws Exception {
        isMetadatable();
        logger.info("field definitions: " + fielddefinition_uri);
        try (RxHttpClient client = new DefaultHttpClient(new URL(HOST))) {
            for (FieldDefinition fieldDefinition : this.metadataDsl.fieldDefinitions) {

                fieldDefinition.metadata_uuid = this.metadataDsl.metadata.uuid;
                fieldDefinition.metadata_name = this.metadataDsl.metadata.table_name;

                Flowable<HttpResponse<FieldDefinition>> call = client.exchange(
                        POST(fielddefinition_uri, fieldDefinition)
                                .header("Authorization", "Bearer " + this.token),
                        FieldDefinition.class
                );
                HttpResponse<FieldDefinition> response = call.blockingFirst();
                Optional<FieldDefinition> metadataResponse = response.getBody(FieldDefinition.class);
                if (response.getStatus().equals(HttpStatus.OK)) {
                    if (metadataResponse.isPresent()) {
                        FieldDefinition fieldDefinition_ = metadataResponse.get();
                        if (fieldDefinition_ != null && fieldDefinition_.uuid != null) {
                            logger.info("fieldDefinition: " + fieldDefinition_.uuid);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("create field definition error: ", e);
        }
        return this;
    }


    public SnelloBuilder create() throws Exception {
        if (this.metadataDsl != null && this.metadataDsl.uuid == null) {
            metadataDsl.rotate();
            createMetadata();
            createFieldDefinitions();
        } else {
            logger.info("we can't create metadata");
        }
        return this;
    }

    public SnelloBuilder createTable() throws Exception {
        if (this.metadataDsl != null) {
            if (this.metadataDsl.uuid == null) {
                logger.info(" we must create before!");
                create();
            }
            createMetadataTable();
        } else {
            logger.info("we can't create metadata table - we must create before!");
        }
        return this;
    }

}
