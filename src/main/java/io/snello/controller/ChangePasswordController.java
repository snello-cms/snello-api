package io.snello.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.snello.management.AppConstants;
import io.snello.service.ApiService;
import io.snello.service.mail.Email;
import io.snello.service.mail.EmailService;
import io.snello.util.JsonUtils;
import io.snello.util.PasswordUtils;
import io.snello.util.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static io.micronaut.http.HttpResponse.ok;
import static io.micronaut.http.HttpResponse.serverError;
import static io.snello.management.AppConstants.*;

@Controller(PASSWORD_PATH)
public class ChangePasswordController {

    Logger logger = LoggerFactory.getLogger(ChangePasswordController.class);


    @Inject
    ApiService apiService;

    @Inject
    EmailService emailService;

    String table = AppConstants.USERS;
    String UUID = AppConstants.USERNAME;

    @Post(UUID_PATH_PARAM_RESET)
    public HttpResponse<?> reset(@NotNull String uuid) throws Exception {
        Map<String, Object> map = apiService.fetch(null, table, uuid, UUID);
        if (map != null && map.containsKey(EMAIL)) {
            //GENERO UN TOKEN E LO INVIO TRAMITE EMAIL
            String token = RandomUtils.aphaNumericString(6);
            //DEVO AVERE UNA PAGINA DI CAMBIO PASSWORD
            String mail = (String) map.get(EMAIL);
            String subject = "reset password SNELLO CMS";
            String body = "The token to change your password is: " + token;
            Email emailObj = new Email(mail, subject, body);
            emailService.send(emailObj);

            Map<String, Object> changePasswordTokenMap = new HashMap<>();
            changePasswordTokenMap.put(AppConstants.UUID, java.util.UUID.randomUUID().toString());
            changePasswordTokenMap.put(EMAIL, uuid);
            changePasswordTokenMap.put(TOKEN, token);
            changePasswordTokenMap.put(CREATION_DATE, Instant.now().toString());
            changePasswordTokenMap = apiService.create(CHANGE_PASSWORD_TOKENS, changePasswordTokenMap, UUID);
            return ok(changePasswordTokenMap);
        }
        return serverError();
    }


    @Post(UUID_PATH_PARAM_CHANGE)
    public HttpResponse<?> change(@Body String body, @NotNull String uuid) throws Exception {
        Map<String, Object> mapVerify = JsonUtils.fromJson(body);
        Map<String, Object> tokenMap = apiService.fetch(null, CHANGE_PASSWORD_TOKENS,
                (String) mapVerify.get(TOKEN), TOKEN);
        boolean tokenValido = false;
        boolean pwdValida = false;
        if (tokenMap != null && tokenMap.containsKey(EMAIL)
                && tokenMap.get(EMAIL).equals(uuid)
        ) {
            tokenValido = true;
        } else {
            logger.info("no token Valido: " + mapVerify.get(TOKEN) + " - for uuid: " + uuid);
        }
        if (tokenMap != null && mapVerify.containsKey(PASSWORD)
                && mapVerify.containsKey(CONFIRM_PASSWORD)
                && mapVerify.get(PASSWORD).equals(mapVerify.get(CONFIRM_PASSWORD))
        ) {
            pwdValida = true;
        } else {
            logger.info("no pwd valida: " + mapVerify.get(TOKEN) + " - for uuid: " + uuid);
        }
        if (tokenValido && pwdValida) {
            Map<String, Object> map = new HashMap<>();
            String pwd = PasswordUtils.createPassword((String) mapVerify.get(AppConstants.PASSWORD));
            map.put(AppConstants.PASSWORD, pwd);
            map.put(AppConstants.LAST_UPDATE_DATE, Instant.now().toString());
            map = apiService.merge(table, map, uuid, UUID);
            return ok(map);
        } else {
            logger.info("!!PAY ATTENTION!! tokenValido: " + tokenValido + " or pwdValida: " + pwdValida);
        }
        return serverError();
    }
}
