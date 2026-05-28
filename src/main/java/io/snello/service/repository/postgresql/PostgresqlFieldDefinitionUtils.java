package io.snello.service.repository.postgresql;

import io.snello.model.FieldDefinition;

import static io.snello.service.repository.postgresql.PostgresqlSqlUtils.escape;


public class PostgresqlFieldDefinitionUtils {
//                    input_type: text,
//                    text => type: textarea (todo), input_type: null,
//                    number => type: input, input_type: number,
//                    boolean => type: checkbox, input_type: null,
//                    date => type: date, input_type: null,
//                    email=> type: input, input_type: email,
//                    password => type: input, input_type: password,
//                    enum => type: select, input_type: null,
//                    media => type: media(todo), input_type: null
    public static String sql(FieldDefinition fieldDefinition) {
        if (fieldDefinition == null || fieldDefinition.type == null) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        switch (fieldDefinition.type) {
            case "input": {
                if (fieldDefinition.input_type == null) {
                    return escape(fieldDefinition.name) + " varchar(200) default null";
                }
                switch (fieldDefinition.input_type) {
                    case "text":
                    case "password":
                    case "email":
                        sb.append(escape(fieldDefinition.name)).append(" varchar(200) default null ");
                        // if (w != null && !fieldDefinition.default_value.trim().isEmpty()) {
                        //     sb.append(" DEFAULT '" + fieldDefinition.default_value + "' ");
                        // }
                        return sb.toString();
                    case "number":
                        sb.append(fieldDefinition.name + " NUMERIC(10) ");
                        // if (fieldDefinition.default_value != null && !fieldDefinition.default_value.trim().isEmpty()) {
                        //     sb.append(fieldDefinition.default_value + " ");
                        // }
                        return sb.toString();
                    case "decimal":
                        sb.append(fieldDefinition.name + " DOUBLE PRECISION ");
                        // if (fieldDefinition.default_value != null && !fieldDefinition.default_value.trim().isEmpty()) {
                        //     sb.append(fieldDefinition.default_value + " ");
                        // }
                        return sb.toString();
                }
            }
            case "gmappath":
            case "gmaplocation":
            case "monaco":
            case "textarea":
            case "tinymce":
                return escape(fieldDefinition.name) + " text default null";
            case "date":
                return escape(fieldDefinition.name) + " date default null";
            case "datetime":
                return escape(fieldDefinition.name) + " TIMESTAMPTZ default null";
            case "time":
                return escape(fieldDefinition.name) + " time default null";
            case "checkbox":
            case "passivation":
                sb.append(escape(fieldDefinition.name) + " boolean");
                // if (fieldDefinition.default_value != null && !fieldDefinition.default_value.trim().isEmpty()) {
                //     sb.append(" DEFAULT " + fieldDefinition.default_value + " ");
                // } else {
                    sb.append("  default false ");
                // }
                return sb.toString();
            case "select":
            case "media":
            case "image":
            case "tags":
            case "multijoin":
            case "multiselect":
            case "multilookup":
            case "realtionship":
            case "realtionships":
            case "relationship":
            case "relationships":
            case "join":
            case "lookup":
                return escape(fieldDefinition.name) + " varchar(2048) default null  ";
        }
        return null;
    }


}
