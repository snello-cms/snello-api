# Document Management

This section explains how documents are modeled, stored, and exposed through REST APIs.

## Core Model: Document

The document domain model is defined in [snello-core/src/main/java/io/snello/model/Document.java](../snello-core/src/main/java/io/snello/model/Document.java).

Main fields:

- `uuid`: document identifier
- `name`: stored file name
- `original_name`: original uploaded file name
- `path`: storage path/object key
- `mimetype`: media type
- `size`: file size
- `formats`: generated derivative formats (for example `webp`, `400x400`)
- `table_name`: business table connected to the file
- `table_key`: key of the business record connected to the file

## One API, Multiple Storage Backends

The application uses the same REST endpoints independently from storage backend implementation.

Storage contract:

- `StorageService` interface: [snello-core/src/main/java/io/snello/api/service/StorageService.java](../snello-core/src/main/java/io/snello/api/service/StorageService.java)

Available implementations:

- S3/MinIO: [snello-core/src/main/java/io/snello/service/storage/S3StorageService.java](../snello-core/src/main/java/io/snello/service/storage/S3StorageService.java)
- Filesystem: [snello-core/src/main/java/io/snello/service/storage/FsStorageService.java](../snello-core/src/main/java/io/snello/service/storage/FsStorageService.java)
- Blob storage placeholder: [snello-core/src/main/java/io/snello/service/storage/BlobStorageService.java](../snello-core/src/main/java/io/snello/service/storage/BlobStorageService.java)

Backend selection is done by producer/config:

- Producer: [snello-core/src/main/java/io/snello/service/producer/StorageProducer.java](../snello-core/src/main/java/io/snello/service/producer/StorageProducer.java)
- Config key: `snello.storagetype`
- Supported runtime values currently wired:
  - `s3` -> `S3StorageService`
  - `f2` -> `FsStorageService`
  - `blobstorage` -> placeholder branch (currently not implemented)

In practice, this gives one REST API surface for multiple storage strategies.

## REST Layer: DocumentServiceRs

Document endpoints are exposed by [snello-core/src/main/java/io/snello/service/rs/DocumentServiceRs.java](../snello-core/src/main/java/io/snello/service/rs/DocumentServiceRs.java).

### CRUD and metadata

- `GET /api/documents` list documents
- `GET /api/documents/{id}` fetch metadata for one document
- `POST /api/documents` upload a new file (`multipart/form-data`)
- `PUT /api/documents/{uuid}` replace file content (`multipart/form-data`)
- `PUT /api/documents/{id}/data` update metadata only (`application/json`)
- `DELETE /api/documents/{uuid}` delete metadata; optional physical delete with query param `?delete=true`

### Download endpoints

- `GET /api/documents/{uuid}/download`
- `GET /api/documents/{uuid}/download/{name}`
- `GET /api/documents/{uuid}/webp`

## Upload Workflow

Upload payload is represented by [snello-core/src/main/java/io/snello/model/pojo/DocumentFormData.java](../snello-core/src/main/java/io/snello/model/pojo/DocumentFormData.java):

- binary part: `file`
- text fields: `filename`, `mimeType`, `table_name`, `table_key`

The REST layer delegates to the selected `StorageService` implementation, then stores/updates document metadata through `ApiService`.

## Two Nice Features

### 1. On-demand `format` generation during download

When calling:

- `GET /api/documents/{uuid}/download?format=<value>`

Behavior:

1. If derivative format already exists (tracked in `formats`), the system streams that generated file.
2. If missing, the system triggers asynchronous generation using events (`imageEvent.fireAsync(new ImageEvent(uuid, format))`) and immediately returns the original file stream.

This means requested formats are created lazily and cached in storage for future downloads.

### 2. On-demand `/webp` generation

When calling:

- `GET /api/documents/{uuid}/webp`

Behavior:

1. If a webp derivative exists, it is returned directly.
2. If missing and source file is convertible (`png`/`jpg`/`jpeg`), an asynchronous `ImageEvent` for `webp` is fired.
3. The current request returns available content, while webp is produced in background for next requests.

## Async Conversion Pipeline

Event model:

- [snello-core/src/main/java/io/snello/model/events/ImageEvent.java](../snello-core/src/main/java/io/snello/model/events/ImageEvent.java)

Async processor:

- [snello-core/src/main/java/io/snello/service/ImageService.java](../snello-core/src/main/java/io/snello/service/ImageService.java)

`ImageService` listens with `@ObservesAsync`, generates derivative files, uploads them through `StorageService`, and updates the document `formats` field.

## Notes

- S3 and filesystem backends are implemented and used by the same REST contract.
- Blob storage classes are currently placeholders and need implementation before production usage.
- Derivative generation is asynchronous by design: first request can return original content while conversion is being produced.

## All-in-one storage

The all-in-one image defaults to `snello.storagetype=fs` with `SNELLO_FS_BASE_PATH=/data/files` (persist via the `/data` volume). See [All-in-one](all-in-one.md).

