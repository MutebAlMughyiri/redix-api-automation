package redix.model.attachments;

public record UploadAttachmentResponse(
        String id,

        String fileName,

        String contentType,

        Integer sizeBytes) {
}
