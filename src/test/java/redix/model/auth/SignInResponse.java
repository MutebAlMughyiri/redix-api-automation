// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.auth;

import java.util.List;

public record SignInResponse(
        String accessToken,

        String accessTokenExpiresAt,

        String refreshToken,

        Boolean mustChangePassword,

        List<String> permissions,

        String defaultProjectId,

        Boolean usesBiometrics,

        String userId,

        String username,

        String displayName,

        String email) {
}
