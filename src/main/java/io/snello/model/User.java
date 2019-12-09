package io.snello.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class User {
    public String username;
    public String password;
    public String name;
    public String surname;
    public String email;
    public Date creation_date;
    public Date last_update_date;
    public boolean active = true;

    public List<UserRole> user_roles;

    public User() {
    }

    public User(Map<String, Object> map) {
        super();
        fromMap(map, this);
    }

    public User fromMap(Map<String, Object> map, User user) {
        if (map.get("username") instanceof String) {
            user.username = (String) map.get("username");
        }
        if (map.get("password") instanceof String) {
            user.password = (String) map.get("password");
        }
        if (map.get("name") instanceof String) {
            user.name = (String) map.get("name");
        }
        if (map.get("surname") instanceof String) {
            user.surname = (String) map.get("surname");
        }
        if (map.get("email") instanceof String) {
            user.email = (String) map.get("email");
        }

        if (map.get("creation_date") instanceof String) {
            Instant instant = LocalDate.parse((String) map.get("creation_date")).atTime(LocalTime.of(0, 0, 0, 0)).toInstant(ZoneOffset.UTC);
            int offset = TimeZone.getDefault().getOffset(instant.toEpochMilli());
            user.creation_date = new java.sql.Date(instant.toEpochMilli() - offset);
        }
        if (map.get("last_update_date") instanceof String) {
            Instant instant = LocalDate.parse((String) map.get("last_update_date")).atTime(LocalTime.of(0, 0, 0, 0)).toInstant(ZoneOffset.UTC);
            int offset = TimeZone.getDefault().getOffset(instant.toEpochMilli());
            user.last_update_date = new java.sql.Date(instant.toEpochMilli() - offset);
        }

        if (map.get("active") instanceof String) {
            user.active = Boolean.valueOf((String) map.get("active"));
        }

        return user;
    }


    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", name='" + name + '\'' +
                ", surname='" + surname + '\'' +
                ", email='" + email + '\'' +
                ", creation_date='" + creation_date + '\'' +
                ", last_update_date='" + last_update_date + '\'' +
                ", active=" + active +
                '}';
    }
}
