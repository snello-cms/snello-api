package io.snello.service.rs;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.snello.management.SnelloPlane;
import io.snello.model.pojo.BackupDataImportResult;
import io.snello.model.pojo.ZipFormData;
import io.snello.service.BackupService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import static io.snello.management.AppConstants.BACKUP_PATH;
import static jakarta.ws.rs.core.Response.ok;

@Path(BACKUP_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class BackupServiceRs {

    @Inject
    BackupService backupService;

    @GET
    @Produces("application/zip")
    @Path("/data")
    public Response download() throws Exception {
        java.nio.file.Path zip = backupService.exportAllDataZip();
        return zipDownload(zip, "snello-backup-data.zip");
    }

    @GET
    @Produces("application/zip")
    @Path("/data/{metadata_name}")
    public Response downloadTable(@PathParam("metadata_name") @NotNull String metadataName) throws Exception {
        java.nio.file.Path zip = backupService.exportTableDataZip(metadataName);
        return zipDownload(zip, "snello-backup-data-" + metadataName + ".zip");
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/data")
    public Response upload(@BeanParam ZipFormData zipFormData) throws Exception {
        if (zipFormData == null || zipFormData.data == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("missing multipart form field 'file'").build();
        }
        BackupDataImportResult result = backupService.importData(zipFormData.data);
        return ok(result).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path("/files")
    public Response downloadFiles() {
        // Stub — files backup not implemented yet
        return Response.status(Response.Status.NOT_IMPLEMENTED)
                .entity("backup files export not implemented").build();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/files")
    public Response uploadFiles(@BeanParam ZipFormData zipFormData) {
        return Response.status(Response.Status.NOT_IMPLEMENTED)
                .entity("backup files import not implemented").build();
    }

    private static Response zipDownload(java.nio.file.Path zip, String filename) {
        StreamingOutput stream = (OutputStream out) -> {
            try (InputStream in = Files.newInputStream(zip)) {
                in.transferTo(out);
            } finally {
                try {
                    Files.deleteIfExists(zip);
                } catch (Exception e) {
                    Log.warnf(e, "failed to delete temp backup zip %s", zip);
                }
            }
        };
        return Response.ok(stream)
                .type("application/zip")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
    }
}
