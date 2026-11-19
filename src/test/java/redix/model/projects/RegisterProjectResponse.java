// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.projects;

import java.util.List;

public record RegisterProjectResponse(
        String id,

        String name,

        String startDate,

        String status,

        String rejectionReason,

        String description,

        List<UnitsItem> units,

        List<String> permissions,

        String roleName,

        Boolean isInactive,

        Boolean holdsEntries,

        String timeZone,

        Integer utcOffsetMinutes,

        List<String> unseenTables) {

    public record UnitsItem(
            String id,

            String name) {
    }
}
