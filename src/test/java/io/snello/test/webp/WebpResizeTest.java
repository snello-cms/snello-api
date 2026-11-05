package io.snello.test.webp;

import net.coobird.thumbnailator.Thumbnails;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;

public class WebpResizeTest {

    static String targetImgFormat = "webp";
    static String origin = "docs/snello3.jpeg";
    static String target = "docs/webp/snello3.webp";
    static String resized_target = "docs/webp/snello3_500x500.webp";


    @Disabled("sample image fixtures removed from docs/; keep as manual media smoke test")
    @Test
    public void resize() throws Exception {
        BufferedImage png = ImageIO.read(new File(origin));
        ImageIO.write(png, "webp", new File(target));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Thumbnails.of(target)
                .size(500, 500)
                .outputFormat(targetImgFormat)
                .outputQuality(1)
                .toFile(new File(resized_target));

    }
}
