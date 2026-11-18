package redix.model.reports;

public record NextPeriodicReportResponse(
        Integer number,

        Waiting waiting) {

    public record Waiting(
            Integer expensesCount,

            Double expensesAmount,

            Integer revenueCount,

            Double revenueAmount) {
    }
}
