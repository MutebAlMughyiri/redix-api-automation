package redix.model.projects;

public record TimeZonesResponse(
        String name,

        String wordKey,

        Integer utcOffsetMinutes) {
}
