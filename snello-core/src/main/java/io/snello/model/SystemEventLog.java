package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;

import java.time.LocalDateTime;
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

    public SystemEventLog(Map<String, Object> map) {
        fromMap(map, this);
    }

    public SystemEventLog fromMap(Map<String, Object> map, SystemEventLog log) {
        if (map == null || log == null) {
            return log;
        }
        log.uuid = MapReads.str(map, "uuid");
        log.received_date = MapReads.localDateTime(map, "received_date");
        log.obj = MapReads.str(map, "obj");
        log.operation_type = MapReads.str(map, "operation_type");
        log.data = MapReads.str(map, "data");
        log.principal = MapReads.str(map, "principal");
        return log;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "uuid", uuid);
        MapWrites.put(map, "received_date", received_date);
        MapWrites.put(map, "obj", obj);
        MapWrites.put(map, "operation_type", operation_type);
        MapWrites.put(map, "data", data);
        MapWrites.put(map, "principal", principal);
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
