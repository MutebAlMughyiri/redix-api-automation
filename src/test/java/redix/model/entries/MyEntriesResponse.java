package redix.model.entries;

public record MyEntriesResponse(
        String id,

        Integer number,

        String kind,

        String status,

        String rejectionReason,

        String entryDate,

        Double amount,

        String itemName,

        String supplierName,

        String supplierVatNumber,

        String invoiceNumber,

        String authorName,

        Boolean isMine,

        String partnerName,

        String description,

        String accountName,

        String paymentMethodName,

        Double accountShortBy) {
}
