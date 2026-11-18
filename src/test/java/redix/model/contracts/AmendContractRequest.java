// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.contracts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class AmendContractRequest {
    private BigDecimal value;

    private Boolean isOpenValue;

    private BigDecimal unitPrice;

    private BigDecimal quantity;

    private String notes;

    private List<String> attachmentIds;

    private String supplierId;

    private String itemId;
}
