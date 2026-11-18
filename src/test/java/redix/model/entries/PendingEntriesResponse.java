// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.entries;

import java.math.BigDecimal;

public record PendingEntriesResponse(
        String id,

        Integer number,

        String kind,

        String entryDate,

        BigDecimal amount,

        String itemName,

        String supplierName,

        String invoiceNumber,

        String accountName,

        String description,

        String attachmentId,

        String contentType,

        String fileName,

        String authorName,

        String partnerName,

        BigDecimal accountBalance,

        BigDecimal accountWaiting,

        BigDecimal accountShortBy) {
}
