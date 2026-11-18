package redix.model.contracts;

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
public class AddContractRequest {
    private String supplierId;

    private String itemId;

    private Double value;

    private Boolean isOpenValue;

    private Double unitPrice;

    private Double quantity;

    private String notes;

    private List<String> attachmentIds;
}
