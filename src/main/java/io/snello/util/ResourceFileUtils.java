package io.snello.util;

import io.snello.model.ResourceFile;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ResourceFileUtils {


    public static List<ResourceFile> fromFiles(File[] allContents) {
        List<ResourceFile> resourceFiles = new ArrayList<>();
        for (File file : allContents) {
            resourceFiles.add(new ResourceFile(file));
        }
        return resourceFiles;
    }

    public static void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                if (!Files.isSymbolicLink(f.toPath())) {
                    deleteDir(f);
                }
            }
        }
        file.delete();
    }

    public static String getExtension(String filename) throws Exception {
        if (filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".") + 1);
        }
        return null;
    }


}
