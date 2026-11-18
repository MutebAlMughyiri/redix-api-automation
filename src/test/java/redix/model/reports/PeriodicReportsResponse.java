// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.reports;

import java.math.BigDecimal;

public record PeriodicReportsResponse(
        Integer number,

        String issuedAt,

        BigDecimal expenses) {
}
