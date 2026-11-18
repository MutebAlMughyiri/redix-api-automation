// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

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
