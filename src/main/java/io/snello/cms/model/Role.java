package io.snello.cms.model;

public class Role {

    public String name;
    public String description;
    //metadata or select query
    public String object_type;
    // matadata_name or selectquery_name => PATH
    public String object_name;
    // action => GET, POST, PUT, DELETE
    public String action;

    public Role() {
    }

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }


    @Override
    public String toString() {
        return "Role{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", object_type='" + object_type + '\'' +
                ", object_name='" + object_name + '\'' +
                ", action='" + action + '\'' +
                '}';
    }
}
