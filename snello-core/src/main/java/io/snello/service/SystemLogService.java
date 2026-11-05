package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.model.SystemEventLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SystemLogService {

    @Inject
    ApiService apiService;
    
    public SystemEventLog persistSystemLog(String newPath, String method, String jsonObject, String principal) {
        SystemEventLog log = new SystemEventLog(newPath, method, jsonObject, principal);
        try {
            Log.info("PERSIST: " + log);
//            apiService.create("systemeventlogs", log.toMap(), "uuid");
            return log;
        } catch (Exception e) {
            Log.error(e);
        }
        return null;
    }

}
