package io.snello.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

public class ChangePasswordToken {

    public String uuid;
    public String email;
    public String token;
    public Date creation_date;

    public ChangePasswordToken() {
    }

    public ChangePasswordToken(Map<String, Object> map) {
        super();
        fromMap(map, this);
    }

    public ChangePasswordToken fromMap(Map<String, Object> map, ChangePasswordToken changePasswordToken) {
        if (map.get("uuid") instanceof String) {
            changePasswordToken.uuid = (String) map.get("uuid");
        }
        if (map.get("email") instanceof String) {
            changePasswordToken.email = (String) map.get("email");
        }
        if (map.get("token") instanceof String) {
            changePasswordToken.token = (String) map.get("token");
        }

        if (map.get("creation_date") instanceof String) {
            Instant instant = LocalDate.parse((String) map.get("creation_date")).atTime(LocalTime.of(0, 0, 0, 0)).toInstant(ZoneOffset.UTC);
            int offset = TimeZone.getDefault().getOffset(instant.toEpochMilli());
            changePasswordToken.creation_date = new java.sql.Date(instant.toEpochMilli() - offset);
        }

        return changePasswordToken;
    }


    @Override
    public String toString() {
        return "ChangePasswordToken{" +
                "uuid='" + uuid + '\'' +
                ", email='" + email + '\'' +
                ", token='" + token + '\'' +
                ", creation_date=" + creation_date +
                '}';
    }
}
