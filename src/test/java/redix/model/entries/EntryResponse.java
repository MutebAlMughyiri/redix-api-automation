// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.entries;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record EntryResponse(
        Entry entry,

        String tableId,

        Expense expense,

        Purchase purchase,

        ContractorPayment contractorPayment,

        Invoice invoice,

        List<AttachmentsItem> attachments) {

    public record Entry(
            String id,

            String projectId,

            Integer number,

            String kind,

            String status,

            String entryDate,

            BigDecimal amount,

            String accountId,

            String description,

            Map<String, String> columnValues,

            String createdByUserId,

            String createdAt,

            String approvedAt) {
    }

    public record Expense(
            String entryId,

            String itemId,

            String subgroupId,

            String paymentMethodId) {
    }

    public record Purchase(
            String entryId,

            String supplierId) {
    }

    public record ContractorPayment(
            String entryId,

            String contractId,

            Boolean isOutsideContract) {
    }

    public record Invoice(
            String entryId,

            String number) {
    }

    public record AttachmentsItem(
            String attachmentId,

            String role,

            String fileName,

            String contentType,

            String uploadedAt) {
    }
}
