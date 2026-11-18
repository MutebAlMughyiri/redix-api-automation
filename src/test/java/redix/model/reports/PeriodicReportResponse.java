package redix.model.reports;

import java.util.List;

public record PeriodicReportResponse(
        String projectName,

        Integer number,

        String issuedAt,

        Double previousExpenses,

        Double expenses,

        Double capital,

        Double vatRefunds,

        Double sales,

        Double otherRevenue,

        Double surplus,

        Double surplusWithoutSales,

        List<CategoriesItem> categories,

        List<AccountsItem> accounts,

        Double accountsTotal,

        Double reconciliationDifference,

        Integer pendingCount,

        Double pendingAmount,

        Double capitalTotal,

        List<PartnersItem> partners,

        List<ItemsItem> items,

        List<RevenueCategoriesItem> revenueCategories,

        Integer pendingRevenueCount,

        Double pendingRevenueAmount,

        Double expensesDifference,

        Double revenue,

        Double revenueWithoutSales) {

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

    public record AccountsItem(
            String name,

            String type,

            Double balance) {
    }

    public record PartnersItem(
            String name,

            String id,

            Double balance,

            Double sharePercent) {
    }

    public record ItemsItem(
            String itemId,

            String itemName,

            String categoryName,

            Double total,

            List<SubgroupsItem> subgroups,

            List<ContractsItem> contracts,

            Boolean showsTablePercentages,

            String type,

            Boolean showsEveryTable) {

        public record SubgroupsItem(
                String id,

                String name,

                Double total,

                List<RowsItem> rows,

                Integer sortOrder,

                List<ColumnsItem> columns,

                String amountName) {

            public record RowsItem(
                    String entryId,

                    String date,

                    Double amount,

                    Boolean isOutsideContract,

                    List<String> values,

                    Boolean isNew,

                    Integer number) {
            }

            public record ColumnsItem(
                    String key,

                    String name,

                    String dataType,

                    Boolean isRequired,

                    List<ChoicesItem> choices) {

                public record ChoicesItem(
                        String name,

                        String sign) {
                }
            }
        }

        public record ContractsItem(
                String id,

                String contractorName,

                Double value,

                Boolean isOpenValue,

                Double paid,

                Double remaining,

                Boolean isOverrun,

                Double paymentsTotal,

                List<PaymentsItem> payments,

                Integer sortOrder,

                List<ColumnsItem> columns,

                String amountName) {

            public record PaymentsItem(
                    String entryId,

                    String date,

                    Double amount,

                    Boolean isOutsideContract,

                    List<String> values,

                    Boolean isNew,

                    Integer number) {
            }

            public record ColumnsItem(
                    String key,

                    String name,

                    String dataType,

                    Boolean isRequired,

                    List<ChoicesItem> choices) {

                public record ChoicesItem(
                        String name,

                        String sign) {
                }
            }
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
