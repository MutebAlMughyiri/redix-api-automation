package redix.model.accounts;

public record AccountsResponse(
        String id,

        String type,

        String name,

        Double balance,

        String custodianId,

        String custodianName,

        Boolean isMine,

        Boolean isActive,

        Boolean isUsed) {
}
