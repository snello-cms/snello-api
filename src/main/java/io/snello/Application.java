package io.snello;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;


@OpenAPIDefinition(
        info = @Info(
                title = "Snello Api",
                version = "0.0.1",
                description = "My API",
                license = @License(name = "Apache 2.0", url = "http://snello.io"),
                contact = @Contact(url = "http://snello.io", name = "Snello", email = "me@fiorenzo.pizza")
        )
)
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class);
    }
}
