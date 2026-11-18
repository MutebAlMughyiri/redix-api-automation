package redix.model.accounts;

import java.util.List;

public record EnterReconciliationResponse(
        String at,

        Double surplus,

        Double actualTotal,

        Double difference,

        List<BalancesItem> balances) {

    public record BalancesItem(
            String accountId,

            String accountName,

            String type,

            Double booksBalance,

            Double actualBalance,

            Boolean wasChecked) {
    }
}
