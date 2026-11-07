package io.snello.util;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtils {

    public static void unzip(final String zipFilePath, final String unzipLocation) throws IOException {
        unzip(new FileInputStream(zipFilePath), Paths.get(unzipLocation));
    }

    public static void unzip(final InputStream zipStream, final Path unzipLocation) throws IOException {
        if (!(Files.exists(unzipLocation))) {
            Files.createDirectories(unzipLocation);
        }
        try (ZipInputStream zipInputStream = new ZipInputStream(zipStream)) {
            ZipEntry entry = zipInputStream.getNextEntry();
            while (entry != null) {
                Path filePath = unzipLocation.resolve(entry.getName()).normalize();
                if (!filePath.startsWith(unzipLocation)) {
                    throw new IOException("zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Path parent = filePath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    unzipFiles(zipInputStream, filePath);
                }

                zipInputStream.closeEntry();
                entry = zipInputStream.getNextEntry();
            }
        }
    }

    public static void unzipFiles(final ZipInputStream zipInputStream, final Path unzipFilePath) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(unzipFilePath.toAbsolutePath().toString()))) {
            byte[] bytesIn = new byte[8192];
            int read;
            while ((read = zipInputStream.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        }
    }

    public static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup of temp backup dirs
                }
            });
        }
    }
}
