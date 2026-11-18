package redix.model.contracts;

import java.math.BigDecimal;
import java.util.List;

public record ContractsResponse(
        String id,

        String itemId,

        String supplierName,

        String itemName,

        BigDecimal value,

        Boolean isOpenValue,

        BigDecimal unitPrice,

        BigDecimal quantity,

        String notes,

        BigDecimal paid,

        BigDecimal pending,

        BigDecimal remaining,

        Boolean isOverrun,

        Boolean isActive,

        String supplierId,

        Boolean hasPayments,

        List<DocumentsItem> documents) {

    public record DocumentsItem(
            String id,

            String fileName,

            String contentType,

            Integer sizeBytes) {
    }
}
