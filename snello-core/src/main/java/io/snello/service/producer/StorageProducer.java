package io.snello.service.producer;

import io.minio.MinioClient;
import io.quarkus.logging.Log;
import io.snello.api.service.StorageService;
import io.snello.service.storage.FsStorageService;
import io.snello.service.storage.S3StorageService;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Singleton
public class StorageProducer {
    
    @ConfigProperty(name = "snello.storagetype", defaultValue = "")
    String storagetype;

    @ConfigProperty(name = "snello.s3.bucketname", defaultValue = "")
    String bucketName;

    @ConfigProperty(name = "snello.s3.folder", defaultValue = "")
    String base_folder;

    @ConfigProperty(name = "snello.fs.basePath", defaultValue = "")
    String basePath;

    @Inject
    MinioClient minioClient;

    public StorageProducer() {
        Log.info("StorageProducer");
    }

    @Produces
    public StorageService storage() throws Exception {
        Log.info("storagetype: " + storagetype);
        switch (storagetype) {
            case "s3":
                return new S3StorageService(minioClient, bucketName, base_folder);
            case "f2":
                return new FsStorageService(basePath);
            case "blobstorage":
                return null;
            default:
                throw new Exception("no storagetype");
        }
    }
}
