package redix.model.reports;

import java.math.BigDecimal;

public record IssuePeriodicReportResponse(
        Integer number,

        String issuedAt,

        BigDecimal expenses) {
}
