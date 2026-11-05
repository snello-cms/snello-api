package io.snello.util;


import io.quarkus.logging.Log;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;

public class ImageUtils {
    
    public static Integer getImageWidthProportional(Object imageData,
                                                    Integer maxWidth, Integer maxHeight) {
        if (imageData == null)
            return 0;
        return getImageSizeProportional((byte[]) imageData, maxWidth, maxHeight)[0];
    }

    public static Integer getImageWidthProportionalUrl(String baseFolder, String url,
                                                       Integer maxWidth, Integer maxHeight) {
        if (url == null)
            return 0;
        return getImageSizeProportional(baseFolder + url, maxWidth, maxHeight)[0];
    }

    public static Integer getImageHeightProportional(Object imageData,
                                                     Integer maxWidth, Integer maxHeight) {
        if (imageData == null)
            return 0;
        return getImageSizeProportional((byte[]) imageData, maxWidth, maxHeight)[1];
    }

    public static Integer getImageHeightProportionalUrl(String baseFolder, String url,
                                                        Integer maxWidth, Integer maxHeight) {
        if (url == null)
            return 0;
        return getImageSizeProportional(baseFolder + url, maxWidth, maxHeight)[1];
    }

    public static int[] getImageSizeProportional(byte[] imageData, int maxWidth, int maxHeight) {
        ImageIcon imageIcon = new ImageIcon(imageData);
        return getImageSizeProportional(imageIcon, maxWidth, maxHeight);
    }

    public static int[] getImageSizeProportional(String url, int maxWidth, int maxHeight) {
        ImageIcon imageIcon = new ImageIcon(url);
        return getImageSizeProportional(imageIcon, maxWidth, maxHeight);
    }

    private static int[] getImageSizeProportional(ImageIcon imageIcon,
                                                  int maxWidth, int maxHeight) {
        double ratioH = (double) maxHeight / imageIcon.getIconHeight();
        double ratioW = (double) maxWidth / imageIcon.getIconWidth();
        int targetWidth = imageIcon.getIconWidth();
        int targetHeight = imageIcon.getIconHeight();

        if (ratioW < ratioH) {
            if (ratioW < 1) {
                targetWidth = (int) (imageIcon.getIconWidth() * ratioW);
                targetHeight = (int) (imageIcon.getIconHeight() * ratioW);
            }
        } else /* if ratioH < ratioW */if (ratioH < 1) {
            targetWidth = (int) (imageIcon.getIconWidth() * ratioH);
            targetHeight = (int) (imageIcon.getIconHeight() * ratioH);
        }

        return new int[]{targetWidth, targetHeight};
    }

    public static byte[] resizeImage(byte[] imageData, int maxWidthOrHeight,
                                     String type) throws Exception {
        // Create an ImageIcon from the image data
        ImageIcon imageIcon = new ImageIcon(imageData);
        int width = imageIcon.getIconWidth();
        int height = imageIcon.getIconHeight();
        // log.info("imageIcon width: " + width + "  height: " + height);

        // landscape (W>H) or portrait image (W<=H)?
        boolean isPortraitImage;
        if (width <= height)
            // vertical image (portrait)
            isPortraitImage = true;
        else
            // horizontal image (landscape)
            isPortraitImage = false;

        // vertical image, i have to care about height
        if (isPortraitImage && maxWidthOrHeight > 0
                && height > maxWidthOrHeight) {
            // Determine the shrink ratio
            double ratio = (double) maxWidthOrHeight
                    / imageIcon.getIconHeight();
            Log.info("resize ratio: " + ratio);
            width = (int) (imageIcon.getIconWidth() * ratio);
            height = maxWidthOrHeight;
            Log.info("imageIcon post scale width: " + width + "  height: "
                    + height);
        }

        // horizontal image, i have to care about width
        if (!isPortraitImage && maxWidthOrHeight > 0
                && width > maxWidthOrHeight) {
            // Determine the shrink ratio
            double ratio = (double) maxWidthOrHeight / imageIcon.getIconWidth();
            Log.info("resize ratio: " + ratio);
            height = (int) (imageIcon.getIconHeight() * ratio);
            width = maxWidthOrHeight;
            Log.info("imageIcon post scale width: " + width + "  height: "
                    + height);
        }

        // Create a new empty image buffer to "draw" the resized image into
        return writeImage(imageIcon, width, height, type);
    }

    public static byte[] resizeImage(File image, int newWidth, int newHeight, String type) throws Exception {
        // Create an ImageIcon from the image data
        ImageIcon imageIcon = new ImageIcon(image.getAbsolutePath());
        return writeImage(imageIcon, newWidth, newHeight, type);
    }

    public static byte[] resizeImage(byte[] imageData, int newWidth, int newHeight,
                                     String type) throws Exception {
        // Create an ImageIcon from the image data
        ImageIcon imageIcon = new ImageIcon(imageData);
        return writeImage(imageIcon, newWidth, newHeight, type);
    }

    private static byte[] writeImage(ImageIcon imageIcon, int width, int height, String type) throws Exception {
        BufferedImage bufferedResizedImage = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_RGB);
        // Create a Graphics object to do the "drawing"
        Graphics2D g2d = bufferedResizedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        // Draw the resized image
        g2d.drawImage(imageIcon.getImage(), 0, 0, width, height, null);
        g2d.dispose();
        // Now our buffered image is ready
        // Encode it as a JPEG
        return imgWrite(bufferedResizedImage, type);
    }

    private static byte[] imgWrite(BufferedImage bufferedResizedImage, String type) throws Exception {
        ByteArrayOutputStream encoderOutputStream = new ByteArrayOutputStream();
        ImageIO.write(bufferedResizedImage, type.toUpperCase(),
                encoderOutputStream);
        byte[] resizedImageByteArray = encoderOutputStream.toByteArray();
        return resizedImageByteArray;
    }


    public static byte[] cropImage(File img, String type, int x, int y, int w, int h) throws Exception {
        BufferedImage originalImage = ImageIO.read(img);
        BufferedImage bufferedResizedImage = originalImage.getSubimage(
                Integer.valueOf(x).intValue(),
                Integer.valueOf(y).intValue(),
                Integer.valueOf(w).intValue(),
                Integer.valueOf(h).intValue());
        return imgWrite(bufferedResizedImage, type);
    }

}
