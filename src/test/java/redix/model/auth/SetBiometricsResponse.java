package redix.model.auth;

import java.util.List;

public record SetBiometricsResponse(
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
