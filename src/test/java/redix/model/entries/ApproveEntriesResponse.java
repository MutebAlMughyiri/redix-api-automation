package redix.model.entries;

import java.util.List;

public record ApproveEntriesResponse(
        List<String> approved,

        List<FailedItem> failed) {

    public record FailedItem(
            String entryId,

            String code) {
    }
}
