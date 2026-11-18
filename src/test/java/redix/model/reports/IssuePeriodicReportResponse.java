package redix.model.reports;

public record IssuePeriodicReportResponse(
        Integer number,

        String issuedAt,

        Double expenses) {
}
