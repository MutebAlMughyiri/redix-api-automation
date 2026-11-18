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
