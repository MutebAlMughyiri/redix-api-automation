package redix.model.entries;

import java.math.BigDecimal;

public record RecordEntryResponse(
        String id,

        String status,

        BigDecimal amount,

        BigDecimal paid,

        BigDecimal pending,

        BigDecimal remainingAfter,

        Boolean isOverrun,

        Integer number,

        String rejectionReason) {
}
