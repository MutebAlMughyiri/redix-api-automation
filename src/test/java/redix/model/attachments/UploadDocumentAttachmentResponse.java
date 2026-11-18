package redix.model.attachments;

public record UploadDocumentAttachmentResponse(
        String id,

        String fileName,

        String contentType,

        Integer sizeBytes) {
}
