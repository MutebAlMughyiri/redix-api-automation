// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

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
public class AddDefaultColumnRequest {
    private Boolean applyToAllItems;

    private String name;

    private String dataType;

    private Boolean isRequired;

    private Boolean showInReports;

    private List<ChoicesItem> choices;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    public static class ChoicesItem {
        private String name;

        private String sign;
    }
}
