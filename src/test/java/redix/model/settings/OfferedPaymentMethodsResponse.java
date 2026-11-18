package redix.model.settings;

import java.util.List;

public record OfferedPaymentMethodsResponse(
        String id,

        String name,

        Boolean isActive,

        List<String> accountIds) {
}
