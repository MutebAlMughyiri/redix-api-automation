package redix.model.documents;

public record DocumentsResponse(
        String id,

        String name,

        String group,

        String attachmentId,

        String fileName,

        String contentType,

        String uploadedAt) {
}
