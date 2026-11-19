// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.users;

import java.util.List;

/** A person as the API answers them: its users.userView (findings row 272). */
public record UserView(
        String id,

        String username,

        String displayName,

        String mobile,

        String email,

        Boolean mustChangePassword,

        List<ProjectView> projects) {

    public record ProjectView(
            String projectId,

            List<String> permissions,

            String roleName,

            List<String> partnerIds,

            Boolean isInactive,

            List<String> unseenTables) {
    }
}
