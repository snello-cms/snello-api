package io.snello;

import io.snello.dsl.SnelloApiBuilder;
import io.snello.dsl.SnelloBuilder;
import org.junit.Test;

public class SnelloDSLTest {


    @Test
    public void dsl() {
        try {
            SnelloBuilder snelloBuilder = new SnelloBuilder("admin", "admin")
                    .login()
                    .metadata("televisioni", "uuid")
                    .icon("fa users")
                    .slug("name")
                    .fieldDefinition("name", "name")
                    .fieldDefinition("channels", "channels", "number")
                    .create()
                    .createTable();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void apiListDsl() {
        try {
            SnelloApiBuilder snelloBuilder = new SnelloApiBuilder("admin", "admin")
                    .login()
                    .list("drinks");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void apiSingleDsl() {
        try {
            SnelloApiBuilder snelloBuilder = new SnelloApiBuilder("admin", "admin")
                    .login()
                    .table("drinks")
                    .uuid("spritz")
                    .single();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
