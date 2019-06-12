package io.snello;

import org.junit.Test;

import java.util.Base64;

public class FileEncodeDecode {


    @Test
    public void en() {

        String originalInput = "/Users/fiorenzo/IdeaProjectsSnello/my-openapi-app/my-openapi-app.iml";
        String encodedString = Base64.getEncoder().encodeToString(originalInput.getBytes());
        System.out.println(encodedString);
        byte[] decodedBytes = Base64.getDecoder().decode(encodedString);
        String decodedString = new String(decodedBytes);
        System.out.println(decodedString);

    }
}
