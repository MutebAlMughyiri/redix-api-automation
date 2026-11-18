package redix.model.suppliers;

public record SearchSuppliersResponse(
        String id,

        String name,

        String vatNumber,

        String mobile,

        String defaultItemId,

        String defaultItemName,

        String status,

        Boolean isActive,

        Integer timesUsed) {
}
