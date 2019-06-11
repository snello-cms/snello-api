package io.snello.controller;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.snello.model.Metadata;
import io.snello.model.SelectQuery;
import io.snello.service.MetadataService;

import javax.inject.Inject;
import java.util.Collection;

import static io.snello.management.AppConstants.SWAGGER_PATH;

//@Controller(SWAGGER_PATH)
public class SwaggerController {

    @Inject
    MetadataService metadataService;

    @Get("/snello.yml")
    public String yaml() {

        StringBuffer stringBuffer = new StringBuffer("openapi: 3.0.1\n" +
                "info:\n" +
                "  title: Snello CMS" +
                "  description: Snello  CMS API\n" +
                "  contact:\n" +
                "    name: snello.io\n" +
                "    url: https://snello.io\n" +
                "    email: me@snello.io\n" +
                "  license:\n" +
                "    name: Apache 2.0\n" +
                "    url: http://snello.io\n" +
                "  version: \"0.0.1\"\n" +
                "paths:\n" +
                "  /api/{name}:\n" +
                "    get:\n" +
                "      description: \"\"\n" +
                "      operationId: index\n" +
                "      parameters:\n" +
                "      - name: name\n" +
                "        in: path\n" +
                "        description: The person's name\n" +
                "        required: true\n" +
                "        schema:\n" +
                "          type: string\n" +
                "      responses:\n" +
                "        default:\n" +
                "          description: The greeting\n" +
                "          content:\n" +
                "            text/plain:\n" +
                "              schema:\n" +
                "                type: string");
        try {
            addMetadata(stringBuffer);
            addSelectquery(stringBuffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stringBuffer.toString();
    }

    public void addMetadata(StringBuffer stringBuffer) throws Exception {
        Collection<Metadata> metadataList = metadataService.metadataMap().values();
        if (metadataList.isEmpty()) {
            return;
        }
        for (Metadata metadata : metadataList) {
            stringBuffer.append("paths:\n" +
                    "  /api/" + metadata.table_name + ":\n" +
                    "    get:\n" +
                    "      description: \"" + metadata.description + "\"\n" +
                    "      operationId: list\n" +
                    "      parameters:\n" +
                    "      - name: name\n" +
                    "        in: path\n" +
                    "        description: The person's name\n" +
                    "        required: true\n" +
                    "        schema:\n" +
                    "          type: string\n" +
                    "      responses:\n" +
                    "        default:\n" +
                    "          description: The greeting\n" +
                    "          content:\n" +
                    "            text/plain:\n" +
                    "              schema:\n" +
                    "                type: string");
        }
    }

    public void addSelectquery(StringBuffer stringBuffer) throws Exception {
        Collection<SelectQuery> selectQueries = metadataService.selectqueryMap().values();
        if (selectQueries.isEmpty()) {
            return;
        }
        for (SelectQuery selectQuery : selectQueries) {
            stringBuffer.append("paths:\n" +
                    "  /api/" + selectQuery.query_name + ":\n" +
                    "    get:\n" +
                    "      description: \"" + selectQuery.query_name + "\"\n" +
                    "      operationId: list\n" +
                    "      parameters:\n" +
                    "      - name: name\n" +
                    "        in: path\n" +
                    "        description: The person's name\n" +
                    "        required: true\n" +
                    "        schema:\n" +
                    "          type: string\n" +
                    "      responses:\n" +
                    "        default:\n" +
                    "          description: The greeting\n" +
                    "          content:\n" +
                    "            text/plain:\n" +
                    "              schema:\n" +
                    "                type: string");
        }

    }
}
