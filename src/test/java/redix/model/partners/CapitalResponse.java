package redix.model.partners;

import java.math.BigDecimal;
import java.util.List;

public record CapitalResponse(
        BigDecimal total,

        List<PartnersItem> partners) {

    public record PartnersItem(
            String id,

            String name,

            BigDecimal balance,

            BigDecimal sharePercent) {
    }
}
