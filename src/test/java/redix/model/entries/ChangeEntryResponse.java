package redix.model.entries;

public record ChangeEntryResponse(
        String id,

        String status,

        Double amount,

        Double paid,

        Double pending,

        Double remainingAfter,

        Boolean isOverrun) {
}
