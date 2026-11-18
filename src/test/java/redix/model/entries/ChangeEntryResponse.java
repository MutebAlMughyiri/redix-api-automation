// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.entries;

import java.math.BigDecimal;

public record ChangeEntryResponse(
        String id,

        String status,

        BigDecimal amount,

        BigDecimal paid,

        BigDecimal pending,

        BigDecimal remainingAfter,

        Boolean isOverrun) {
}
