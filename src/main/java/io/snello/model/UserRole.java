package io.snello.model;

public class UserRole {
    public String username;
    public String role;

    public UserRole() {
    }


    @Override
    public String toString() {
        return "UserRole{" +
                "username='" + username + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}
