// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.reports;

import java.math.BigDecimal;
import java.util.List;

public record SummaryResponse(
        BigDecimal revenue,

        BigDecimal expenses,

        BigDecimal surplus,

        Boolean wasReconciled,

        BigDecimal reconciliationDifference,

        Waiting waiting,

        BigDecimal completionPercent,

        List<CategoriesItem> categories,

        List<RevenueCategoriesItem> revenueCategories) {

    public record Waiting(
            Integer expensesCount,

            BigDecimal expensesAmount,

            Integer revenueCount,

            BigDecimal revenueAmount) {
    }

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
