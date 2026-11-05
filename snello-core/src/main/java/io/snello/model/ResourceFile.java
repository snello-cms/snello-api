package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.util.MimeUtils;

import java.io.File;
import java.util.Base64;
import java.util.Map;

@RegisterForReflection
public class ResourceFile {

    public ResourceFile(File file) {
        this.name = file.getName();
        this.path = file.getAbsolutePath();
        this.mimetype = MimeUtils.getContentType(this.name);
        this.uuid = Base64.getEncoder().encodeToString(this.path.getBytes());
    }

    public ResourceFile(String uuid) {
        byte[] decodedBytes = Base64.getDecoder().decode(uuid);
        this.path = new String(decodedBytes);
        File file = new File(this.path);
        this.name = file.getName();
        this.mimetype = MimeUtils.getContentType(this.name);
        this.uuid = uuid;
    }

    public ResourceFile(Map<String, Object> map, String folder) {
        this.name = (String) map.get("name");
        this.path = folder + "/" + this.name;
        this.mimetype = MimeUtils.getContentType(this.name);
        this.uuid = Base64.getEncoder().encodeToString(this.path.getBytes());
    }

    public String uuid;
    public String name;
    public String path;
    public String mimetype;


}
