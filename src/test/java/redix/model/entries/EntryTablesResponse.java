package redix.model.entries;

import java.util.List;

public record EntryTablesResponse(
        String id,

        String name,

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
