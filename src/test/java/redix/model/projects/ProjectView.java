// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.projects;

import java.util.List;

/** A project as the API answers it: its projects.projectView, whichever route answered (findings row 272). */
public record ProjectView(
        String id,

        String name,

        String startDate,

        String status,

        String rejectionReason,

        String description,

        List<UnitView> units,

        List<String> permissions,

        String roleName,

        Boolean isInactive,

        Boolean holdsEntries,

        String timeZone,

        Integer utcOffsetMinutes,

        List<String> unseenTables) {

    public record UnitView(
            String id,

            String name) {
    }
}
