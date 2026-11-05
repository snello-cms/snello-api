package io.snello.test.webp;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class WebpTest {

    @Disabled("sample image fixtures removed from docs/; keep as manual media smoke test")
    @Test
    public void testHelloEndpoint() throws Exception {
        BufferedImage svg = ImageIO.read(new File("docs/images/snello-logo.svg"));
        ImageIO.write(svg, "webp", new File("target/snello-logo.webp"));
    }


    @Test
    public void purgeTest() {
        String table = "kayaks&iss";
        if (table.endsWith("&iss")) {
            System.out.println(table.replace("&iss", ""));
        }

    }


}
