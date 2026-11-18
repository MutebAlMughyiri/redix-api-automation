// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.partners;

import java.math.BigDecimal;

public record PartnerMovementsResponse(
        String id,

        String movementName,

        String date,

        BigDecimal amount,

        String description,

        String accountName,

        String attachmentId,

        String contentType,

        String fileName) {
}
