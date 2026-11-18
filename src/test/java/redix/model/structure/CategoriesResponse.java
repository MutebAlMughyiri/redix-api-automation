package redix.model.structure;

import java.util.List;

public record CategoriesResponse(
        String id,

        String name,

        Boolean isActive,

        List<ItemsItem> items) {

    public record ItemsItem(
            String id,

            String name,

            String type,

            Boolean isActive,

            List<SubgroupsItem> subgroups,

            Boolean showsTablePercentages) {

        public record SubgroupsItem(
                String id,

                String name) {
        }
    }
}
