package redix.model.partners;

import java.math.BigDecimal;

public record PartnerMovementsResponse(
        String id,

        String movementName,

        String date,

        BigDecimal amount,

        String description,

        String accountName,

        String attachmentId,

        String contentType,

        String fileName) {
}
