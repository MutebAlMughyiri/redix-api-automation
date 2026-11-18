// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.settings;

import java.util.List;

public record PaymentMethodsResponse(
        String id,

        String name,

        Boolean isActive,

        List<String> accountIds) {
}
