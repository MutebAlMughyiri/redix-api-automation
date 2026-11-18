package redix.model.structure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class EditDefaultColumnRequest {
    private String name;

    private Boolean isRequired;

    private Boolean showInReports;

    private Boolean applyToAllItems;

    private List<NewChoicesItem> newChoices;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    public static class NewChoicesItem {
        private String name;

        private String sign;
    }
}
