package redix.model.accounts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class TransferMoneyRequest {
    private String fromAccountId;

    private String toAccountId;

    private BigDecimal amount;

    private String date;

    private String notes;
}
