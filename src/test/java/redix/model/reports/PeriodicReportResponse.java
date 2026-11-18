// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.reports;

import java.math.BigDecimal;
import java.util.List;

public record PeriodicReportResponse(
        String projectName,

        Integer number,

        String issuedAt,

        BigDecimal previousExpenses,

        BigDecimal expenses,

        BigDecimal capital,

        BigDecimal vatRefunds,

        BigDecimal sales,

        BigDecimal otherRevenue,

        BigDecimal surplus,

        BigDecimal surplusWithoutSales,

        List<CategoriesItem> categories,

        List<AccountsItem> accounts,

        BigDecimal accountsTotal,

        BigDecimal reconciliationDifference,

        Integer pendingCount,

        BigDecimal pendingAmount,

        BigDecimal capitalTotal,

        List<PartnersItem> partners,

        List<ItemsItem> items,

        List<RevenueCategoriesItem> revenueCategories,

        Integer pendingRevenueCount,

        BigDecimal pendingRevenueAmount,

        BigDecimal expensesDifference,

        BigDecimal revenue,

        BigDecimal revenueWithoutSales) {

    public record CategoriesItem(
            String id,

            String name,

            BigDecimal total,

            List<ItemsItem> items) {

        public record ItemsItem(
                String id,

                String name,

                BigDecimal total,

                String type) {
        }
    }

    public record AccountsItem(
            String name,

            String type,

            BigDecimal balance) {
    }

    public record PartnersItem(
            String name,

            String id,

            BigDecimal balance,

            BigDecimal sharePercent) {
    }

    public record ItemsItem(
            String itemId,

            String itemName,

            String categoryName,

            BigDecimal total,

            List<SubgroupsItem> subgroups,

            List<ContractsItem> contracts,

            Boolean showsTablePercentages,

            String type,

            Boolean showsEveryTable) {

        public record SubgroupsItem(
                String id,

                String name,

                BigDecimal total,

                List<RowsItem> rows,

                Integer sortOrder,

                List<ColumnsItem> columns,

                String amountName) {

            public record RowsItem(
                    String entryId,

                    String date,

                    BigDecimal amount,

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

                BigDecimal value,

                Boolean isOpenValue,

                BigDecimal paid,

                BigDecimal remaining,

                Boolean isOverrun,

                BigDecimal paymentsTotal,

                List<PaymentsItem> payments,

                Integer sortOrder,

                List<ColumnsItem> columns,

                String amountName) {

            public record PaymentsItem(
                    String entryId,

                    String date,

                    BigDecimal amount,

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

            BigDecimal total,

            List<ItemsItem> items) {

        public record ItemsItem(
                String id,

                String name,

                BigDecimal total,

                String type) {
        }
    }
}
