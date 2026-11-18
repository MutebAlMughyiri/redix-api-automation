package redix.model.entries;

import java.math.BigDecimal;

public record AccountHistoryResponse(
        Integer id,

        String at,

        String by,

        String kind,

        String account,

        String otherAccount,

        BigDecimal amount,

        String entryKind,

        String nameWas,

        String custodian) {
}
