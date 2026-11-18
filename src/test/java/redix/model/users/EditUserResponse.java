package redix.model.users;

import java.util.List;

public record EditUserResponse(
        String id,

        String username,

        String displayName,

        String mobile,

        String email,

        Boolean mustChangePassword,

        List<ProjectsItem> projects) {

    public record ProjectsItem(
            String projectId,

            List<String> permissions,

            String roleName,

            List<String> partnerIds,

            Boolean isInactive) {
    }
}
