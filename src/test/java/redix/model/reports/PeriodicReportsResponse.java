package redix.model.reports;

import java.math.BigDecimal;

public record PeriodicReportsResponse(
        Integer number,

        String issuedAt,

        BigDecimal expenses) {
}
