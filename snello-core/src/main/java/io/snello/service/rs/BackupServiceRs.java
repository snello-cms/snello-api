package io.snello.service.rs;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.model.pojo.ZipFormData;
import io.snello.service.BackupService;


import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.zip.ZipFile;

import static io.snello.management.AppConstants.BACKUP_PATH;

@Path(BACKUP_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
@RunOnVirtualThread
public class BackupServiceRs {

    @Inject
    BackupService backupService;

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path("/data")
    
    public Response download() {
        ZipFile output = backupService.exportData();
        return Response.ok(output)
                .header("Content-Disposition", "attachment; filename=\"zipfile.zip\"")
                .build();
    }

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path("/files")
    
    public Response downloadFiles() {
        ZipFile output = backupService.exportData();
        return Response.ok(output)
                .header("Content-Disposition", "attachment; filename=\"zipfile.zip\"")
                .build();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/data")
    
    public Response upload(@BeanParam ZipFormData zipFormData) {
        backupService.importData(zipFormData.data);
        return Response.ok().build();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/files")
    
    public Response uploadFiles(@BeanParam ZipFormData zipFormData) {
        backupService.importData(zipFormData.data);
        return Response.ok().build();
    }
}
