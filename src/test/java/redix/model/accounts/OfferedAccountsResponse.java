// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

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
