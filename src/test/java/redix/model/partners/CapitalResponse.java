package redix.model.partners;

import java.util.List;

public record CapitalResponse(
        Double total,

        List<PartnersItem> partners) {

    public record PartnersItem(
            String id,

            String name,

            Double balance,

            Double sharePercent) {
    }
}
