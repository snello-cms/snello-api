package io.snello.cms.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.snello.cms.management.AppConstants;
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

@Controller(AppConstants.DATALIST_PATH)
public class DataListController {

    Logger logger = LoggerFactory.getLogger(DataListController.class);


    @Inject
    MetadataService metadataService;

    @Get("/names")
    public HttpResponse<Set<String>> names() throws Exception {
        return ok(metadataService.names());
    }

    @Get("/metadata/{name}")
    public HttpResponse<Metadata> metadata(@NotNull String name) {
        return ok(metadataService.metadata(name));
    }

    @Get("/metadata/{name}/fielddefinitions")
    public HttpResponse<List<FieldDefinition>> fielddefinitions(@NotNull String name) {
        return ok(metadataService.fielddefinitions(name));
    }

    @Get("/metadata/{name}/conditions")
    public HttpResponse<List<Condition>> conditions(@NotNull String name) {
        return ok(metadataService.conditions(name));
    }

}