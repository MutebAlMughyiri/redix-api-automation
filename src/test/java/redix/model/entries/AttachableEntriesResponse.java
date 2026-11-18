package redix.model.entries;

import java.math.BigDecimal;

public record AttachableEntriesResponse(
        String id,

        Integer number,

        String kind,

        String status,

        String rejectionReason,

        String entryDate,

        BigDecimal amount,

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

        BigDecimal accountShortBy) {
}
