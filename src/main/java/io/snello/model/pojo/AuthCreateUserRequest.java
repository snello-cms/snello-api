package io.snello.model.pojo;

import java.util.List;

public class AuthCreateUserRequest {
    public String username;
    public String email;
    public String name;
    public String surname;
    public List<String> groupIds;
    public List<String> groupNames;
}
