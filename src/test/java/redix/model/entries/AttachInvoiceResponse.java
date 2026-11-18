// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.entries;

public record AttachInvoiceResponse(
        String id,

        Integer number,

        String status,

        String rejectionReason) {
}
