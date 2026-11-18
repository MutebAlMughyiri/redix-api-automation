package redix.model.structure;

import java.util.List;

public record AddItemResponse(
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
