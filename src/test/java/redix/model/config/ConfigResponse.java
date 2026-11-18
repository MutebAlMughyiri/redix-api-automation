package redix.model.config;

public record ConfigResponse(
        String fileTimeoutSeconds,

        String requestTimeoutSeconds,

        String vatRatePercent) {
}
