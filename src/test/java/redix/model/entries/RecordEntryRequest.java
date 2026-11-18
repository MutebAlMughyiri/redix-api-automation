package redix.model.entries;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class RecordEntryRequest {
    private String id;

    private String itemId;

    private String tableId;

    private String date;

    private Double amount;

    private String accountId;

    private String paymentMethodId;

    private String description;

    private Map<String, String> columnValues;

    private String attachmentId;

    private Boolean submit;

    private String supplierId;

    private Boolean isOutsideContract;
}
