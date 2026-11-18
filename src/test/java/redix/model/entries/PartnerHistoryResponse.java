// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

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
