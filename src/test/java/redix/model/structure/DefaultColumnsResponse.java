// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.structure;

import java.util.List;

public record DefaultColumnsResponse(
        String id,

        String key,

        String name,

        String dataType,

        Boolean isCore,

        Boolean isCustom,

        Boolean isRequired,

        Boolean showInReports,

        List<ChoicesItem> choices,

        Boolean isEntryField) {

    public record ChoicesItem(
            String id,

            String name,

            String sign) {
    }
}
