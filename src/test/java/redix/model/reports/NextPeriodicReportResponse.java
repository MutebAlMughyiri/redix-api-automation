package redix.model.reports;

import java.math.BigDecimal;

public record NextPeriodicReportResponse(
        Integer number,

        Waiting waiting) {

    public record Waiting(
            Integer expensesCount,

            BigDecimal expensesAmount,

            Integer revenueCount,

            BigDecimal revenueAmount) {
    }
}
