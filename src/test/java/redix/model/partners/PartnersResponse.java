package redix.model.partners;

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

            Double amount) {
    }
}
