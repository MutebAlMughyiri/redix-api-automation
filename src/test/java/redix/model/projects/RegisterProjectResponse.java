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

        Integer utcOffsetMinutes) {

    public record UnitsItem(
            String id,

            String name) {
    }
}
