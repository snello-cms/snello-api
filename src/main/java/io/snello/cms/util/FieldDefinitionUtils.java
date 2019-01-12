package io.snello.cms.util;

import io.snello.cms.model.FieldDefinition;


public class FieldDefinitionUtils {

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
                        sb.append(fieldDefinition.name).append(" varchar(200) ");
                        if (fieldDefinition.default_value != null && fieldDefinition.default_value.trim().isEmpty()) {
                            sb.append(fieldDefinition.name).append(" NOT NULL DEFAULT '" + fieldDefinition.default_value + "' ");
                        }
                        return sb.toString();
                    case "number":
                        sb.append(fieldDefinition.name + " int(10) ");
                        if (fieldDefinition.default_value != null && fieldDefinition.default_value.trim().isEmpty()) {
                            sb.append(fieldDefinition.name).append(" NOT NULL DEFAULT " + fieldDefinition.default_value + " ");
                        }
                        return sb.toString();
                }
            }
            case "textarea":
                return fieldDefinition.name + " text default null";
            case "date":
                return fieldDefinition.name + " date default null";
            case "checkbox":
                sb.append(fieldDefinition.name + " boolean default false");
                if (fieldDefinition.default_value != null && fieldDefinition.default_value.trim().isEmpty()) {
                    sb.append(fieldDefinition.name).append(" DEFAULT " + fieldDefinition.default_value + " ");
                }
                return sb.toString();
            case "select":
            case "media":
                return fieldDefinition.name + " varchar(200) ";
        }
        return null;
    }
}
