package io.snello.cms.model;

import java.util.List;

public class User {
    public String username;
    public String password;
    public String name;
    public String surname;
    public String email;
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
                ", active=" + active +
                '}';
    }
}
