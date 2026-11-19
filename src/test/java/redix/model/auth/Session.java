// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.auth;

import java.util.List;

/** What signing in, refreshing the session and changing the password answer: the API's auth.session, one record for the three (findings row 272). */
public record Session(
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
