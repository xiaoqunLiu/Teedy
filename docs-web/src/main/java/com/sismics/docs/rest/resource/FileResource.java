package com.sismics.docs.rest.resource;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.sismics.docs.core.constant.PermType;
import com.sismics.docs.core.dao.AclDao;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.dao.FileDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.dao.dto.DocumentDto;
import com.sismics.docs.core.event.DocumentUpdatedAsyncEvent;
import com.sismics.docs.core.event.FileDeletedAsyncEvent;
import com.sismics.docs.core.event.FileUpdatedAsyncEvent;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.docs.core.model.jpa.File;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.service.TranslationService;
import com.sismics.docs.core.util.DirectoryUtil;
import com.sismics.docs.core.util.EncryptionUtil;
import com.sismics.docs.core.util.FileUtil;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.rest.util.RestUtil;
import com.sismics.rest.util.ValidationUtil;
import com.sismics.util.HttpUtil;
import com.sismics.util.JsonUtil;
import com.sismics.util.context.ThreadLocalContext;
import com.sismics.util.mime.MimeType;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * File REST resources.
 * 
 * @author bgamard
 */
@Path("/file")
public class FileResource extends BaseResource {
    // 语言代码映射
    private static String mapLangCode(String code) {
        if (code == null || code.trim().isEmpty()) return "auto";
        if (code.equalsIgnoreCase("eng")) return "en";
        if (code.equalsIgnoreCase("en")) return "en";
        if (code.equalsIgnoreCase("zh") || code.equalsIgnoreCase("zh-CHS") || code.equalsIgnoreCase("zh_CN")) return "zh-CHS";
        if (code.equalsIgnoreCase("spa")) return "es";
        if (code.equalsIgnoreCase("fra")) return "fr";
        // 可根据需要补充更多映射
        return code;
    }

    /**
     * Add a file (with or without a document).
     *
     * @api {put} /file Add a file
     * @apiDescription A file can be added without associated document, and will go
     *                 in a temporary storage waiting for one.
     *                 This resource accepts only multipart/form-data.
     * @apiName PutFile
     * @apiGroup File
     * @apiParam {String} [id] Document ID
     * @apiParam {String} [previousFileId] ID of the file to replace by this new
     *           version
     * @apiParam {String} file File data
     * @apiSuccess {String} status Status OK
     * @apiSuccess {String} id File ID
     * @apiSuccess {Number} size File size (in bytes)
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiError (client) NotFound Document not found
     * @apiError (server) StreamError Error reading the input file
     * @apiError (server) ErrorGuessMime Error guessing mime type
     * @apiError (client) QuotaReached Quota limit reached
     * @apiError (server) FileError Error adding a file
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param documentId   Document ID
     * @param fileBodyPart File to add
     * @return Response
     */
    @PUT
    @Consumes("multipart/form-data")
    public Response add(
            @FormDataParam("id") String documentId,
            @FormDataParam("previousFileId") String previousFileId,
            @FormDataParam("file") FormDataBodyPart fileBodyPart) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Validate input data
        ValidationUtil.validateRequired(fileBodyPart, "file");

        // Get the document
        DocumentDto documentDto = null;
        if (Strings.isNullOrEmpty(documentId)) {
            documentId = null;
        } else {
            DocumentDao documentDao = new DocumentDao();
            documentDto = documentDao.getDocument(documentId, PermType.WRITE, getTargetIdList(null));
            if (documentDto == null) {
                throw new NotFoundException();
            }
        }

        // Keep unencrypted data temporary on disk
        String name = fileBodyPart.getContentDisposition() != null
                ? URLDecoder.decode(fileBodyPart.getContentDisposition().getFileName(), StandardCharsets.UTF_8)
                : null;
        java.nio.file.Path unencryptedFile;
        long fileSize;
        try {
            unencryptedFile = AppContext.getInstance().getFileService().createTemporaryFile(name);
            Files.copy(fileBodyPart.getValueAs(InputStream.class), unencryptedFile,
                    StandardCopyOption.REPLACE_EXISTING);
            fileSize = Files.size(unencryptedFile);
        } catch (IOException e) {
            throw new ServerException("StreamError", "Error reading the input file", e);
        }

        try {
            String fileId = FileUtil.createFile(name, previousFileId, unencryptedFile, fileSize,
                    documentDto == null ? null : documentDto.getLanguage(), principal.getId(), documentId);

            // Always return OK
            JsonObjectBuilder response = Json.createObjectBuilder()
                    .add("status", "ok")
                    .add("id", fileId)
                    .add("size", fileSize);
            return Response.ok().entity(response.build()).build();
        } catch (IOException e) {
            throw new ClientException(e.getMessage(), e.getMessage(), e);
        } catch (Exception e) {
            throw new ServerException("FileError", "Error adding a file", e);
        }
    }

    /**
     * Attach a file to a document.
     *
     * @api {post} /file/:fileId/attach Attach a file to a document
     * @apiName PostFileAttach
     * @apiGroup File
     * @apiParam {String} fileId File ID
     * @apiParam {String} id Document ID
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiError (client) IllegalFile File not orphan
     * @apiError (server) AttachError Error attaching file to document
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param id File ID
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}/attach")
    public Response attach(
            @PathParam("id") String id,
            @FormParam("id") String documentId) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Validate input data
        ValidationUtil.validateRequired(documentId, "documentId");

        // Get the current user
        UserDao userDao = new UserDao();
        User user = userDao.getById(principal.getId());

        // Get the document and the file
        DocumentDao documentDao = new DocumentDao();
        FileDao fileDao = new FileDao();
        File file = fileDao.getFile(id, principal.getId());
        DocumentDto documentDto = documentDao.getDocument(documentId, PermType.WRITE, getTargetIdList(null));
        if (file == null || documentDto == null) {
            throw new NotFoundException();
        }

        // Check that the file is orphan
        if (file.getDocumentId() != null) {
            throw new ClientException("IllegalFile", MessageFormat.format("File not orphan: {0}", id));
        }

        // Update the file
        file.setDocumentId(documentId);
        file.setOrder(fileDao.getByDocumentId(principal.getId(), documentId).size());
        fileDao.update(file);

        // Raise a new file updated event and document updated event (it wasn't sent
        // during file creation)
        try {
            java.nio.file.Path storedFile = DirectoryUtil.getStorageDirectory().resolve(id);
            java.nio.file.Path unencryptedFile = EncryptionUtil.decryptFile(storedFile, user.getPrivateKey());
            FileUtil.startProcessingFile(id);
            FileUpdatedAsyncEvent fileUpdatedAsyncEvent = new FileUpdatedAsyncEvent();
            fileUpdatedAsyncEvent.setUserId(principal.getId());
            fileUpdatedAsyncEvent.setLanguage(documentDto.getLanguage());
            fileUpdatedAsyncEvent.setFileId(file.getId());
            fileUpdatedAsyncEvent.setUnencryptedFile(unencryptedFile);
            ThreadLocalContext.get().addAsyncEvent(fileUpdatedAsyncEvent);

            DocumentUpdatedAsyncEvent documentUpdatedAsyncEvent = new DocumentUpdatedAsyncEvent();
            documentUpdatedAsyncEvent.setUserId(principal.getId());
            documentUpdatedAsyncEvent.setDocumentId(documentId);
            ThreadLocalContext.get().addAsyncEvent(documentUpdatedAsyncEvent);
        } catch (Exception e) {
            throw new ServerException("AttachError", "Error attaching file to document", e);
        }

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Update a file.
     *
     * @api {post} /file/:id Update a file
     * @apiName PostFile
     * @apiGroup File
     * @apiParam {String} id File ID
     * @apiParam {String} name Name
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiPermission user
     * @apiVersion 1.6.0
     *
     * @param id File ID
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}")
    public Response update(@PathParam("id") String id,
            @FormParam("name") String name) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Get the file
        File file = findFile(id, null);

        // Validate input data
        name = ValidationUtil.validateLength(name, "name", 1, 200, false);

        // Update the file
        FileDao fileDao = new FileDao();
        file.setName(name);
        fileDao.update(file);

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Process a file manually.
     *
     * @api {post} /file/:id/process Process a file manually
     * @apiName PostFileProcess
     * @apiGroup File
     * @apiParam {String} id File ID
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiError (server) ProcessingError Processing error
     * @apiPermission user
     * @apiVersion 1.6.0
     *
     * @param id File ID
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}/process")
    public Response process(@PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Get the document and the file
        DocumentDao documentDao = new DocumentDao();
        FileDao fileDao = new FileDao();
        File file = fileDao.getFile(id);
        if (file == null || file.getDocumentId() == null) {
            throw new NotFoundException();
        }
        DocumentDto documentDto = documentDao.getDocument(file.getDocumentId(), PermType.WRITE, getTargetIdList(null));
        if (documentDto == null) {
            throw new NotFoundException();
        }

        // Get the creating user
        UserDao userDao = new UserDao();
        User user = userDao.getById(file.getUserId());

        // Start the processing asynchronously
        try {
            java.nio.file.Path storedFile = DirectoryUtil.getStorageDirectory().resolve(id);
            java.nio.file.Path unencryptedFile = EncryptionUtil.decryptFile(storedFile, user.getPrivateKey());
            FileUtil.startProcessingFile(id);
            FileUpdatedAsyncEvent event = new FileUpdatedAsyncEvent();
            event.setUserId(principal.getId());
            event.setLanguage(documentDto.getLanguage());
            event.setFileId(file.getId());
            event.setUnencryptedFile(unencryptedFile);
            ThreadLocalContext.get().addAsyncEvent(event);
        } catch (Exception e) {
            throw new ServerException("ProcessingError", "Error processing this file", e);
        }

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Reorder files.
     *
     * @api {post} /file/:reorder Reorder files
     * @apiName PostFileReorder
     * @apiGroup File
     * @apiParam {String} id Document ID
     * @apiParam {String[]} order List of files ID
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) ValidationError Validation error
     * @apiError (client) NotFound Document not found
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param documentId Document ID
     * @param idList     List of files ID in the new order
     * @return Response
     */
    @POST
    @Path("reorder")
    public Response reorder(
            @FormParam("id") String documentId,
            @FormParam("order") List<String> idList) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Validate input data
        ValidationUtil.validateRequired(documentId, "id");
        ValidationUtil.validateRequired(idList, "order");

        // Get the document
        AclDao aclDao = new AclDao();
        if (!aclDao.checkPermission(documentId, PermType.WRITE, getTargetIdList(null))) {
            throw new NotFoundException();
        }

        // Reorder files
        FileDao fileDao = new FileDao();
        for (File file : fileDao.getByDocumentId(principal.getId(), documentId)) {
            int order = idList.lastIndexOf(file.getId());
            if (order != -1) {
                file.setOrder(order);
            }
        }

        // Raise a document updated event
        DocumentUpdatedAsyncEvent event = new DocumentUpdatedAsyncEvent();
        event.setUserId(principal.getId());
        event.setDocumentId(documentId);
        ThreadLocalContext.get().addAsyncEvent(event);

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Returns files linked to a document or not linked to any document.
     *
     * @api {get} /file/list Get files
     * @apiName GetFileList
     * @apiGroup File
     * @apiParam {String} [id] Document ID
     * @apiParam {String} [share] Share ID
     * @apiSuccess {Object[]} files List of files
     * @apiSuccess {String} files.id ID
     * @apiSuccess {String} files.processing True if the file is currently
     *             processing
     * @apiSuccess {String} files.name File name
     * @apiSuccess {String} files.version Zero-based version number
     * @apiSuccess {String} files.mimetype MIME type
     * @apiSuccess {String} files.document_id Document ID
     * @apiSuccess {String} files.create_date Create date (timestamp)
     * @apiSuccess {String} files.size File size (in bytes)
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound Document not found
     * @apiError (server) FileError Unable to get the size of a file
     * @apiPermission none
     * @apiVersion 1.5.0
     *
     * @param documentId Document ID
     * @param shareId    Sharing ID
     * @return Response
     */
    @GET
    @Path("list")
    public Response list(
            @QueryParam("id") String documentId,
            @QueryParam("share") String shareId) {
        boolean authenticated = authenticate();

        // Check document visibility
        if (documentId != null) {
            AclDao aclDao = new AclDao();
            if (!aclDao.checkPermission(documentId, PermType.READ, getTargetIdList(shareId))) {
                throw new NotFoundException();
            }
        } else if (!authenticated) {
            throw new ForbiddenClientException();
        }

        FileDao fileDao = new FileDao();
        JsonArrayBuilder files = Json.createArrayBuilder();
        for (File fileDb : fileDao.getByDocumentId(principal.getId(), documentId)) {
            files.add(RestUtil.fileToJsonObjectBuilder(fileDb));
        }
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("files", files);

        return Response.ok().entity(response.build()).build();
    }

    /**
     * List all versions of a file.
     *
     * @api {get} /file/:id/versions Get versions of a file
     * @apiName GetFileVersions
     * @apiGroup File
     * @apiParam {String} id File ID
     * @apiSuccess {Object[]} files List of files
     * @apiSuccess {String} files.id ID
     * @apiSuccess {String} files.name File name
     * @apiSuccess {String} files.version Zero-based version number
     * @apiSuccess {String} files.mimetype MIME type
     * @apiSuccess {String} files.create_date Create date (timestamp)
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound File not found
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param id File ID
     * @return Response
     */
    @GET
    @Path("{id: [a-z0-9\\-]+}/versions")
    public Response versions(@PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Get versions
        File file = findFile(id, null);
        FileDao fileDao = new FileDao();
        List<File> fileList = Lists.newArrayList(file);
        if (file.getVersionId() != null) {
            fileList = fileDao.getByVersionId(file.getVersionId());
        }

        JsonArrayBuilder files = Json.createArrayBuilder();
        for (File fileDb : fileList) {
            files.add(Json.createObjectBuilder()
                    .add("id", fileDb.getId())
                    .add("name", JsonUtil.nullable(fileDb.getName()))
                    .add("version", fileDb.getVersion())
                    .add("mimetype", fileDb.getMimeType())
                    .add("create_date", fileDb.getCreateDate().getTime()));
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("files", files);
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Deletes a file.
     *
     * @api {delete} /file/:id Delete a file
     * @apiName DeleteFile
     * @apiGroup File
     * @apiParam {String} id File ID
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound File or document not found
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param id File ID
     * @return Response
     */
    @DELETE
    @Path("{id: [a-z0-9\\-]+}")
    public Response delete(
            @PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Get the file
        File file = findFile(id, null);

        // Delete the file
        FileDao fileDao = new FileDao();
        fileDao.delete(file.getId(), principal.getId());

        // Raise a new file deleted event
        FileDeletedAsyncEvent fileDeletedAsyncEvent = new FileDeletedAsyncEvent();
        fileDeletedAsyncEvent.setUserId(principal.getId());
        fileDeletedAsyncEvent.setFileId(file.getId());
        fileDeletedAsyncEvent.setFileSize(file.getSize());
        ThreadLocalContext.get().addAsyncEvent(fileDeletedAsyncEvent);

        if (file.getDocumentId() != null) {
            // Raise a new document updated
            DocumentUpdatedAsyncEvent documentUpdatedAsyncEvent = new DocumentUpdatedAsyncEvent();
            documentUpdatedAsyncEvent.setUserId(principal.getId());
            documentUpdatedAsyncEvent.setDocumentId(file.getDocumentId());
            ThreadLocalContext.get().addAsyncEvent(documentUpdatedAsyncEvent);
        }

        // Always return OK
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Returns a file.
     *
     * @api {get} /file/:id/data Get a file data
     * @apiName GetFile
     * @apiGroup File
     * @apiParam {String} id File ID
     * @apiParam {String} share Share ID
     * @apiParam {String="web","thumb","content"} [size] Size variation
     * @apiSuccess {Object} file The file data is the whole response
     * @apiError (client) SizeError Size must be web or thumb
     * @apiError (client) ForbiddenError Access denied or document not visible
     * @apiError (client) NotFound File not found
     * @apiError (server) ServiceUnavailable Error reading the file
     * @apiPermission none
     * @apiVersion 1.5.0
     *
     * @param fileId File ID
     * @return Response
     */
    @GET
    @Path("{id: [a-z0-9\\-]+}/data")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response data(
            @PathParam("id") final String fileId,
            @QueryParam("share") String shareId,
            @QueryParam("size") String size) {
        authenticate();

        if (size != null && !Lists.newArrayList("web", "thumb", "content").contains(size)) {
            throw new ClientException("SizeError", "Size must be web, thumb or content");
        }

        // Get the file
        File file = findFile(fileId, shareId);

        // Get the stored file
        UserDao userDao = new UserDao();
        java.nio.file.Path storedFile;
        String mimeType;
        boolean decrypt;
        if (size != null) {
            if (size.equals("content")) {
                return Response.ok(Strings.nullToEmpty(file.getContent()))
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8")
                        .build();
            }

            storedFile = DirectoryUtil.getStorageDirectory().resolve(fileId + "_" + size);
            mimeType = MimeType.IMAGE_JPEG; // Thumbnails are JPEG
            decrypt = true; // Thumbnails are encrypted
            if (!Files.exists(storedFile)) {
                try {
                    storedFile = Paths.get(getClass().getResource("/image/file-" + size + ".png").toURI());
                } catch (URISyntaxException e) {
                    // Ignore
                }
                mimeType = MimeType.IMAGE_PNG;
                decrypt = false;
            }
        } else {
            storedFile = DirectoryUtil.getStorageDirectory().resolve(fileId);
            mimeType = file.getMimeType();
            decrypt = true; // Original files are encrypted
        }

        // Stream the output and decrypt it if necessary
        StreamingOutput stream;

        // A file is always encrypted by the creator of it
        User user = userDao.getById(file.getUserId());

        // Write the decrypted file to the output
        try {
            InputStream fileInputStream = Files.newInputStream(storedFile);
            final InputStream responseInputStream = decrypt
                    ? EncryptionUtil.decryptInputStream(fileInputStream, user.getPrivateKey())
                    : fileInputStream;

            stream = outputStream -> {
                try {
                    ByteStreams.copy(responseInputStream, outputStream);
                } finally {
                    try {
                        responseInputStream.close();
                        outputStream.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            };
        } catch (Exception e) {
            return Response.status(Status.SERVICE_UNAVAILABLE).build();
        }

        Response.ResponseBuilder builder = Response.ok(stream)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFullName("data") + "\"")
                .header(HttpHeaders.CONTENT_TYPE, mimeType);
        if (decrypt) {
            // Cache real files
            builder.header(HttpHeaders.CACHE_CONTROL, "private")
                    .header(HttpHeaders.EXPIRES, HttpUtil.buildExpiresHeader(3_600_000L * 24L * 365L));
        } else {
            // Do not cache the temporary thumbnail
            builder.header(HttpHeaders.CACHE_CONTROL, "no-store, must-revalidate")
                    .header(HttpHeaders.EXPIRES, "0");
        }
        return builder.build();
    }

    /**
     * Returns all files from a document, zipped.
     *
     * @api {get} /file/zip Returns all files from a document, zipped.
     * @apiName GetFileZip
     * @apiGroup File
     * @apiParam {String} id Document ID
     * @apiParam {String} share Share ID
     * @apiSuccess {Object} file The ZIP file is the whole response
     * @apiError (client) NotFoundException Document not found
     * @apiError (server) InternalServerError Error creating the ZIP file
     * @apiPermission none
     * @apiVersion 1.5.0
     *
     * @param documentId Document ID
     * @param shareId    Share ID
     * @return Response
     */
    @GET
    @Path("zip")
    @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN })
    public Response zip(
            @QueryParam("id") String documentId,
            @QueryParam("share") String shareId) {
        authenticate();

        // Get the document
        DocumentDao documentDao = new DocumentDao();
        DocumentDto documentDto = documentDao.getDocument(documentId, PermType.READ, getTargetIdList(shareId));
        if (documentDto == null) {
            throw new NotFoundException();
        }

        // Get files associated with this document
        FileDao fileDao = new FileDao();
        final List<File> fileList = fileDao.getByDocumentId(principal.getId(), documentId);
        String zipFileName = documentDto.getTitle().replaceAll("\\W+", "_");
        return sendZippedFiles(zipFileName, fileList);
    }

    /**
     * Returns a list of files, zipped
     *
     * @api {post} /file/zip Returns a list of files, zipped
     * @apiName GetFilesZip
     * @apiGroup File
     * @apiParam {String[]} files IDs
     * @apiSuccess {Object} file The ZIP file is the whole response
     * @apiError (client) NotFoundException Files not found
     * @apiError (server) InternalServerError Error creating the ZIP file
     * @apiPermission none
     * @apiVersion 1.11.0
     *
     * @param filesIdsList Files IDs
     * @return Response
     */
    @POST
    @Path("zip")
    @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN })
    public Response zip(
            @FormParam("files") List<String> filesIdsList) {
        authenticate();
        List<File> fileList = findFiles(filesIdsList);
        return sendZippedFiles("files", fileList);
    }

    /**
     * Sent the content of a list of files.
     */
    private Response sendZippedFiles(String zipFileName, List<File> fileList) {
        final UserDao userDao = new UserDao();

        // Create the ZIP stream
        StreamingOutput stream = outputStream -> {
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
                // Add each file to the ZIP stream
                int index = 0;
                for (File file : fileList) {
                    java.nio.file.Path storedfile = DirectoryUtil.getStorageDirectory().resolve(file.getId());
                    InputStream fileInputStream = Files.newInputStream(storedfile);

                    // Add the decrypted file to the ZIP stream
                    // Files are encrypted by the creator of them
                    User user = userDao.getById(file.getUserId());
                    try (InputStream decryptedStream = EncryptionUtil.decryptInputStream(fileInputStream,
                            user.getPrivateKey())) {
                        ZipEntry zipEntry = new ZipEntry(index + "-" + file.getFullName(Integer.toString(index)));
                        zipOutputStream.putNextEntry(zipEntry);
                        ByteStreams.copy(decryptedStream, zipOutputStream);
                        zipOutputStream.closeEntry();
                    } catch (Exception e) {
                        throw new WebApplicationException(e);
                    }
                    index++;
                }
            }
            outputStream.close();
        };

        // Write to the output
        return Response.ok(stream)
                .header("Content-Type", "application/zip")
                .header("Content-Disposition", "attachment; filename=\"" + zipFileName + ".zip\"")
                .build();
    }

    /**
     * Find a file with access rights checking.
     *
     * @param fileId  File ID
     * @param shareId Share ID
     * @return File
     */
    private File findFile(String fileId, String shareId) {
        FileDao fileDao = new FileDao();
        File file = fileDao.getFile(fileId);
        if (file == null) {
            throw new NotFoundException();
        }
        checkFileAccessible(shareId, file);
        return file;
    }

    /**
     * Find a list of files with access rights checking.
     *
     * @param filesIds Files IDs
     * @return List<File>
     */
    private List<File> findFiles(List<String> filesIds) {
        FileDao fileDao = new FileDao();
        List<File> files = fileDao.getFiles(filesIds);
        for (File file : files) {
            checkFileAccessible(null, file);
        }
        return files;
    }

    /**
     * Check if a file is accessible to the current user
     * 
     * @param shareId Share ID
     * @param file
     */
    private void checkFileAccessible(String shareId, File file) {
        if (file.getDocumentId() == null) {
            // It's an orphan file
            if (!file.getUserId().equals(principal.getId())) {
                // But not ours
                throw new ForbiddenClientException();
            }
        } else {
            // Check document accessibility
            AclDao aclDao = new AclDao();
            if (!aclDao.checkPermission(file.getDocumentId(), PermType.READ, getTargetIdList(shareId))) {
                throw new ForbiddenClientException();
            }
        }
    }

    /**
     * Translate a file.
     *
     * @api {post} /file/:id/translate Translate a file
     * @apiName PostFileTranslate
     * @apiGroup File
     * @apiParam {String} id File ID
     * @apiParam {String} to Target language code
     * @apiSuccess {String} status Status OK
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound File not found
     * @apiError (server) TranslationError Error translating the file
     * @apiPermission user
     * @apiVersion 1.12.0
     *
     * @param id   File ID
     * @param body JSON body
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}/translate")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED})
    @Produces(MediaType.APPLICATION_JSON)
    public Response translate(
            @PathParam("id") String id,
            String body,
            @HeaderParam("Content-Type") String contentType) {
        System.out.println("[Translate] Step 0: Content-Type=" + contentType + ", body=" + body);
        if (!authenticate()) {
            System.out.println("[Translate] Step 0.1: Authentication failed");
            throw new ForbiddenClientException();
        }

        // 解析 JSON body 获取 'to' 字段
        String to = null;
        if (contentType != null && contentType.contains("application/json")) {
            try {
                jakarta.json.JsonObject json = jakarta.json.Json.createReader(new java.io.StringReader(body)).readObject();
                to = json.getString("to");
                System.out.println("[Translate] Step 1: Target language = " + to);
            } catch (Exception e) {
                System.out.println("[Translate] Step 1: JSON parse error: " + e.getMessage());
                throw new ClientException("InvalidRequest", "Invalid JSON body", e);
            }
        } else if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
            // 简单解析表单内容
            if (body != null && body.startsWith("to=")) {
                to = java.net.URLDecoder.decode(body.substring(3), java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("[Translate] Step 1: Target language (form) = " + to);
            }
        }

        ValidationUtil.validateRequired(to, "to");

        // Get the file
        File file = null;
        try {
            file = findFile(id, null);
            System.out.println("[Translate] Step 2: File found, name = " + file.getName());
        } catch (Exception e) {
            System.out.println("[Translate] Step 2: File not found: " + e.getMessage());
            throw e;
        }
        if (file == null) {
            System.out.println("[Translate] Step 2: File is null");
            throw new NotFoundException();
        }

        // 调试输出 documentId
        System.out.println("[Translate] Step 2.1: file.getDocumentId() = " + file.getDocumentId());
        if (file.getDocumentId() == null) {
            System.out.println("[Translate] Step 3: File is orphan (no documentId), cannot translate");
            throw new NotFoundException("File is not attached to any document.");
        }
        DocumentDao documentDao = new DocumentDao();
        DocumentDto documentDto = null;
        try {
            documentDto = documentDao.getDocument(file.getDocumentId(), PermType.READ, getTargetIdList(null));
            if (documentDto == null) {
                System.out.println("[Translate] Step 3: DocumentDto is null for id=" + file.getDocumentId());
                throw new NotFoundException("Document not found or no permission for documentId=" + file.getDocumentId());
            }
            System.out.println("[Translate] Step 3: Document found, language = " + documentDto.getLanguage());
        } catch (Exception e) {
            System.out.println("[Translate] Step 3: Document not found: " + e.getMessage());
            throw e;
        }

        String fromLang = mapLangCode(documentDto.getLanguage());
        String toLang = mapLangCode(to);
        // System.out.println("[DEBUG] documentDto.getLanguage() = " + documentDto.getLanguage());
        // System.out.println("[DEBUG] to = " + to);
        // System.out.println("[DEBUG] fromLang = " + fromLang + ", toLang = " + toLang);

        try {
            // Get the stored file
            java.nio.file.Path storedFile = DirectoryUtil.getStorageDirectory().resolve(id);
            UserDao userDao = new UserDao();
            User user = userDao.getById(file.getUserId());
            java.nio.file.Path unencryptedFile = EncryptionUtil.decryptFile(storedFile, user.getPrivateKey());
            System.out.println("[Translate] Step 4: File decrypted, path = " + unencryptedFile);

            // Read file content (only extract PDF text)
            String content;
            try (org.apache.pdfbox.pdmodel.PDDocument pdfDoc = org.apache.pdfbox.pdmodel.PDDocument.load(unencryptedFile.toFile())) {
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                content = stripper.getText(pdfDoc);
            }
            System.out.println("[Translate] Step 5: File content read, length = " + content.length());

            // Translate content
            TranslationService translationService = new TranslationService();
            String translatedContent = translationService.translate(content, fromLang, toLang);
            System.out.println("[Translate] Step 6: Content translated, length = " + translatedContent.length());

            // Create new PDF file with translated content
            java.nio.file.Path newPdfFile = Files.createTempFile("translated_", ".pdf");
            // 请将支持中英文的字体文件（如 NotoSansCJKsc-Regular.ttf）放到 src/main/resources/fonts/ 目录下
            try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
                doc.addPage(page);
                // 加载字体
                try (java.io.InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSansCJKsc-Regular.ttf")) {
                    if (fontStream == null) {
                        throw new RuntimeException("字体文件 /fonts/NotoSansCJKsc-Regular.ttf 未找到，请将支持中英文的 TTF 字体放到 src/main/resources/fonts/");
                    }
                    org.apache.pdfbox.pdmodel.font.PDType0Font font = org.apache.pdfbox.pdmodel.font.PDType0Font.load(doc, fontStream);
                    try (org.apache.pdfbox.pdmodel.PDPageContentStream contentStream = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                        contentStream.beginText();
                        contentStream.setFont(font, 12);
                        contentStream.newLineAtOffset(50, 700);
                        int lineCount = 0;
                        for (String line : translatedContent.split("\\r?\\n")) {
                            contentStream.showText(line);
                            contentStream.newLineAtOffset(0, -15);
                            lineCount++;
                            if (lineCount % 40 == 0) { // 每40行换一页
                                contentStream.endText();
                                contentStream.close();
                                page = new org.apache.pdfbox.pdmodel.PDPage();
                                doc.addPage(page);
                                try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                                    cs.beginText();
                                    cs.setFont(font, 12);
                                    cs.newLineAtOffset(50, 700);
                                }
                            }
                        }
                        contentStream.endText();
                    }
                    doc.save(newPdfFile.toFile());
                }
            }
            System.out.println("[Translate] Step 7: New PDF file created, path = " + newPdfFile);

            // Create new file in Teedy
            String newFileName = file.getName() + "_" + to + ".pdf";
            String newFileId = FileUtil.createFile(newFileName, null, newPdfFile, Files.size(newPdfFile), to, principal.getId(), file.getDocumentId());
            System.out.println("[Translate] Step 8: New file uploaded, id = " + newFileId);

            // Always return OK
            JsonObjectBuilder response = Json.createObjectBuilder()
                    .add("status", "ok")
                    .add("id", newFileId);
            return Response.ok().entity(response.build()).build();
        } catch (Exception e) {
            System.out.println("[Translate] Step ERROR: " + e.getMessage());
            throw new ServerException("TranslationError", "Error translating the file", e);
        }
    }
}
