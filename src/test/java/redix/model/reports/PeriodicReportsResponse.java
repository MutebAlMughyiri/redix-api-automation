package redix.model.reports;

public record PeriodicReportsResponse(
        Integer number,

        String issuedAt,

        Double expenses) {
}
