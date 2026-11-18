// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.notifications;

public record NotificationsResponse(
        String id,

        String kind,

        String projectId,

        String projectName,

        String entryId,

        String at,

        Boolean isRead) {
}
