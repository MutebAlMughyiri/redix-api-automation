package redix.model.entries;

import java.util.List;

public record ContractReportResponse(
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
