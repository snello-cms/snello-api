package io.snello.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Put;
import io.snello.management.AppConstants;
import io.snello.service.ApiService;
import io.snello.service.mail.EmailService;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.util.Map;

import static io.micronaut.http.HttpResponse.ok;
import static io.snello.management.AppConstants.*;

@Controller(CHANGE_PASSORD_PATH)
public class ChangePasswordController {

    @Inject
    ApiService apiService;

    @Inject
    EmailService emailService;

    String table = AppConstants.USERS;
    String UUID = AppConstants.USERNAME;

    @Put(UUID_PATH_PARAM)
    public HttpResponse<?> change(@NotNull String uuid) throws Exception {
        Map<String, Object> map = apiService.fetch(null, table, uuid, UUID);
        if (map != null && map.containsKey(EMAIL)) {
            //GENERO UN TOKEN E LO INVIO TRAMITE EMAIL

            //DEVO AVERE UNA PAGINA DI CAMBIO PASSWORD
        }

        return ok();
    }
}
