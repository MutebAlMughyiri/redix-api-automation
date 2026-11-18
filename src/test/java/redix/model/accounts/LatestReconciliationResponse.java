package redix.model.accounts;

import java.math.BigDecimal;
import java.util.List;

public record LatestReconciliationResponse(
        String at,

        BigDecimal surplus,

        BigDecimal actualTotal,

        BigDecimal difference,

        List<BalancesItem> balances) {

    public record BalancesItem(
            String accountId,

            String accountName,

            String type,

            BigDecimal booksBalance,

            BigDecimal actualBalance,

            Boolean wasChecked) {
    }
}
