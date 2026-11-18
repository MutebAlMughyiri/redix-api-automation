// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.accounts;

import java.math.BigDecimal;
import java.util.List;

public record EnterReconciliationResponse(
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
