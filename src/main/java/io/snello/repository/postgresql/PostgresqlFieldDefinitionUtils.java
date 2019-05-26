package io.snello.repository.postgresql;

import io.snello.model.FieldDefinition;

import static io.snello.repository.postgresql.PostgresqlSqlUtils.escape;


public class PostgresqlFieldDefinitionUtils {


    //inputType: text,
//                    text => type: textarea (todo), inputType: null,
//                    number => type: input, inputType: number,
//                boolean => type: checkbox, inputType: null,
//                    date => type: date, inputType: null,
//                    email=> type: input, inputType: email,
//                    password => type: input, inputType: password,
//                    enum => type: select, inputType: null,
//                    media => type: media(todo), inputType: null
    public static String sql(FieldDefinition fieldDefinition) {
        StringBuffer sb = new StringBuffer();
        switch (fieldDefinition.type) {
            case "input": {
                switch (fieldDefinition.inputType) {
                    case "text":
                    case "password":
                    case "email":
                        sb.append(escape(fieldDefinition.name)).append(" varchar(200)  NOT NULL ");
                        if (fieldDefinition.default_value != null && fieldDefinition.default_value.trim().isEmpty()) {
                            sb.append(" DEFAULT '" + fieldDefinition.default_value + "' ");
                        }
                        return sb.toString();
                    case "number":
                        sb.append(fieldDefinition.name + " NUMERIC(10) ");
                        if (fieldDefinition.default_value != null && fieldDefinition.default_value.trim().isEmpty()) {
                            sb.append(fieldDefinition.default_value + " ");
                        }
                        return sb.toString();
                    case "decimal":
                        sb.append(fieldDefinition.name + " DOUBLE PRECISION ");
                        if (fieldDefinition.default_value != null && fieldDefinition.default_value.trim().isEmpty()) {
                            sb.append(fieldDefinition.default_value + " ");
                        }
                        return sb.toString();
                }
            }
            case "textarea":
                return escape(fieldDefinition.name) + " text default null";
            case "date":
                return escape(fieldDefinition.name) + " date default null";
            case "datetime":
                return escape(fieldDefinition.name) + " datetime default null";
            case "time":
                return escape(fieldDefinition.name) + " time default null";
            case "checkbox":
                sb.append(escape(fieldDefinition.name) + " boolean");
                if (fieldDefinition.default_value != null && fieldDefinition.default_value.trim().isEmpty()) {
                    sb.append(" DEFAULT " + fieldDefinition.default_value + " ");
                } else {
                    sb.append("  default false ");
                }
                return sb.toString();
            case "select":
            case "media":
            case "tags":
            case "multijoin":
            case "join":
                return escape(fieldDefinition.name) + " varchar(2048) default null  ";
        }
        return null;
    }


}
