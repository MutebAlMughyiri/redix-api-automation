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
