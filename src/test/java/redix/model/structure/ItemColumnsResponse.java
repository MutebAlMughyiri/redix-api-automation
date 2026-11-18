package redix.model.structure;

import java.util.List;

public record ItemColumnsResponse(
        String id,

        String key,

        String name,

        String dataType,

        Boolean isCore,

        Boolean isCustom,

        Boolean isRequired,

        Boolean showInReports,

        List<ChoicesItem> choices,

        Boolean isEntryField) {

    public record ChoicesItem(
            String id,

            String name,

            String sign) {
    }
}
