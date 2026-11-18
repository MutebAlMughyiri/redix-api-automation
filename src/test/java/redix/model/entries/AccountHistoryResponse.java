package redix.model.entries;

public record AccountHistoryResponse(
        Integer id,

        String at,

        String by,

        String kind,

        String account,

        String otherAccount,

        Double amount,

        String entryKind,

        String nameWas,

        String custodian) {
}
