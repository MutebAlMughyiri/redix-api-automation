package redix.model.reports;

import java.util.List;

public record SummaryResponse(
        Double revenue,

        Double expenses,

        Double surplus,

        Boolean wasReconciled,

        Double reconciliationDifference,

        Waiting waiting,

        Double completionPercent,

        List<CategoriesItem> categories,

        List<RevenueCategoriesItem> revenueCategories) {

    public record Waiting(
            Integer expensesCount,

            Double expensesAmount,

            Integer revenueCount,

            Double revenueAmount) {
    }

    public record CategoriesItem(
            String id,

            String name,

            Double total,

            List<ItemsItem> items) {

        public record ItemsItem(
                String id,

                String name,

                Double total,

                String type) {
        }
    }

    public record RevenueCategoriesItem(
            String id,

            String name,

            Double total,

            List<ItemsItem> items) {

        public record ItemsItem(
                String id,

                String name,

                Double total,

                String type) {
        }
    }
}
