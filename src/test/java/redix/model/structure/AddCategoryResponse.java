package redix.model.structure;

import java.util.List;

public record AddCategoryResponse(
        String id,

        String name,

        Boolean isActive,

        List<ItemsItem> items) {

    public record ItemsItem(
            String id,

            String name,

            List<TablesItem> tables,

            Boolean isRevenue) {

        public record TablesItem(
                String id,

                String kind,

                String name,

                List<String> columnKeys) {
        }
    }
}
