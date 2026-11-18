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
public class AddContractRequest {
    private String supplierId;

    private String itemId;

    private BigDecimal value;

    private Boolean isOpenValue;

    private BigDecimal unitPrice;

    private BigDecimal quantity;

    private String notes;

    private List<String> attachmentIds;
}
