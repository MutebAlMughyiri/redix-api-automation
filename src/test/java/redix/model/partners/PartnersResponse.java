package redix.model.partners;

import java.math.BigDecimal;
import java.util.List;

public record PartnersResponse(
        String id,

        String name,

        String userId,

        String userName,

        Boolean mayDelete,

        List<String> othersDrafts,

        List<MyDraftsItem> myDrafts) {

    public record MyDraftsItem(
            String entryDate,

            BigDecimal amount) {
    }
}
