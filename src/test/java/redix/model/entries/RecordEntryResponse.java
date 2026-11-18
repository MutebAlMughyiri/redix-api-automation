package redix.model.entries;

public record RecordEntryResponse(
        String id,

        String status,

        Double amount,

        Double paid,

        Double pending,

        Double remainingAfter,

        Boolean isOverrun,

        Integer number,

        String rejectionReason) {
}
