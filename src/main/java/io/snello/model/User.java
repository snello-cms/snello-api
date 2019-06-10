package io.snello.model;

import java.util.Date;
import java.util.List;

public class User {
    public String username;
    public String password;
    public String name;
    public String surname;
    public String email;
    public Date creation_date;
    public Date last_update_date;
    public boolean active = true;

    public List<UserRole> userRoles;
    
    public User() {
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
