package redix.model.entries;

import java.math.BigDecimal;

public record ApprovalHistoryResponse(
        Integer id,

        String at,

        String by,

        String kind,

        String item,

        String party,

        BigDecimal amount,

        String reason) {
}
