package redix.model.entries;

public record PendingEntriesResponse(
        String id,

        Integer number,

        String kind,

        String entryDate,

        Double amount,

        String itemName,

        String supplierName,

        String invoiceNumber,

        String accountName,

        String description,

        String attachmentId,

        String contentType,

        String fileName,

        String authorName,

        String partnerName,

        Double accountBalance,

        Double accountWaiting,

        Double accountShortBy) {
}
