// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.projects;

import java.util.List;

public record RegisteredProjectsResponse(
        String id,

        String name,

        String startDate,

        List<String> units,

        String managerName,

        String managerUsername,

        String managerMobile,

        String description) {
}
