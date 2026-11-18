package redix.model.entries;

public record PartnerHistoryResponse(
        Integer id,

        String at,

        String by,

        String kind,

        String partner,

        String account,

        Double amount,

        String nameWas,

        String login) {
}
