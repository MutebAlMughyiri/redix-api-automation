package redix.model.entries;

import java.math.BigDecimal;
import java.util.List;

public record ItemReportResponse(
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
