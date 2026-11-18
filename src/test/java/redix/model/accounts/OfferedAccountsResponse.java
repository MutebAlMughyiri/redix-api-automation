package redix.model.accounts;

import java.math.BigDecimal;

public record OfferedAccountsResponse(
        String id,

        String type,

        String name,

        BigDecimal balance,

        String custodianId,

        String custodianName,

        Boolean isMine,

        Boolean isActive,

        Boolean isUsed) {
}
