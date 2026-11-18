package redix.model.entries;

import java.math.BigDecimal;

public record PartnerHistoryResponse(
        Integer id,

        String at,

        String by,

        String kind,

        String partner,

        String account,

        BigDecimal amount,

        String nameWas,

        String login) {
}
