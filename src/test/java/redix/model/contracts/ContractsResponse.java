package redix.model.contracts;

import java.util.List;

public record ContractsResponse(
        String id,

        String itemId,

        String supplierName,

        String itemName,

        Double value,

        Boolean isOpenValue,

        Double unitPrice,

        Double quantity,

        String notes,

        Double paid,

        Double pending,

        Double remaining,

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
