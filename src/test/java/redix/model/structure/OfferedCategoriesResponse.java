// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.structure;

import java.util.List;

public record OfferedCategoriesResponse(
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
