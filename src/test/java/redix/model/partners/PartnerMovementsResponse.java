package redix.model.partners;

public record PartnerMovementsResponse(
        String id,

        String movementName,

        String date,

        Double amount,

        String description,

        String accountName,

        String attachmentId,

        String contentType,

        String fileName) {
}
