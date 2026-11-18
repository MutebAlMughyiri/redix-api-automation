package redix.model.entries;

public record AttachInvoiceResponse(
        String id,

        Integer number,

        String status,

        String rejectionReason) {
}
