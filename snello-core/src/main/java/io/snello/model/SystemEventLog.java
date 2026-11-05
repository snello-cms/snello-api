package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RegisterForReflection
public class SystemEventLog {

    public String uuid;
    public LocalDateTime received_date;
    public String obj;
    public String operation_type;
    public String data;
    public String principal;

    public SystemEventLog() {
    }

    public SystemEventLog(String obj, String operation_type, String data, String principal) {
        this.received_date = LocalDateTime.now();
        this.obj = obj;
        this.operation_type = operation_type;
        this.data = data;
        this.principal = principal;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (this.uuid != null) {
            map.put("uuid", this.uuid);
        }
        if (this.received_date != null) {
            map.put("received_date", this.received_date);
        }
        if (this.obj != null) {
            map.put("obj", this.obj);
        }
        if (this.operation_type != null) {
            map.put("operation_type", this.operation_type);
        }
        if (this.data != null) {
            map.put("data", this.data);
        }
        if (this.principal != null) {
            map.put("principal", this.principal);
        }
        return map;
    }

    @Override
    public String toString() {
        return "SystemEventLog{" +
               "uuid='" + uuid + '\'' +
               ", received_date=" + received_date +
               ", obj='" + obj + '\'' +
               ", operation_type='" + operation_type + '\'' +
               ", data='" + data + '\'' +
               ", principal='" + principal + '\'' +
               '}';
    }
}
