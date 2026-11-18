package redix.model.entries;

import java.math.BigDecimal;

public record ChangeEntryResponse(
        String id,

        String status,

        BigDecimal amount,

        BigDecimal paid,

        BigDecimal pending,

        BigDecimal remainingAfter,

        Boolean isOverrun) {
}
