// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.suppliers;

public record SearchSuppliersResponse(
        String id,

        String name,

        String vatNumber,

        String mobile,

        String defaultItemId,

        String defaultItemName,

        String status,

        Boolean isActive,

        Integer timesUsed) {
}
