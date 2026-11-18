package redix.model.entries;

public record ApprovalHistoryResponse(
        Integer id,

        String at,

        String by,

        String kind,

        String item,

        String party,

        Double amount,

        String reason) {
}
