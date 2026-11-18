// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.config;

public record ConfigResponse(
        String fileTimeoutSeconds,

        String requestTimeoutSeconds,

        String vatRatePercent) {
}
