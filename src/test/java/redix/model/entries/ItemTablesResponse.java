package redix.model.entries;

public record ItemTablesResponse(
        String id,

        String kind,

        String name,

        Integer count) {
}
