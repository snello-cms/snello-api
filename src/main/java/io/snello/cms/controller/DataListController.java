package io.snello.cms.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.snello.cms.model.Condition;
import io.snello.cms.model.FieldDefinition;
import io.snello.cms.model.Metadata;
import io.snello.cms.service.MetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

import static io.micronaut.http.HttpResponse.ok;
import static io.snello.cms.management.AppConstants.*;

@Controller(DATALIST_PATH)
public class DataListController {

    Logger logger = LoggerFactory.getLogger(DataListController.class);


    @Inject
    MetadataService metadataService;

    @Get(DATA_LIST_NAMES)
    public HttpResponse<Set<String>> names() throws Exception {
        return ok(metadataService.names());
    }

    @Get(DATA_LIST_METADATA_NAMES)
    public HttpResponse<Metadata> metadata(@NotNull String name) throws Exception {
        return ok(metadataService.metadata(name));
    }

    @Get(DATA_LIST_FIELD_DEFINITIONS)
    public HttpResponse<List<FieldDefinition>> fielddefinitions(@NotNull String name) throws Exception {
        return ok(metadataService.fielddefinitions(name));
    }

    @Get(DATA_LIST_CONDITIONS)
    public HttpResponse<List<Condition>> conditions(@NotNull String name) throws Exception {
        return ok(metadataService.conditions(name));
    }

}
