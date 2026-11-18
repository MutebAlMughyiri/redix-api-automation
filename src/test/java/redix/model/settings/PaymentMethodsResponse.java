package redix.model.settings;

import java.util.List;

public record PaymentMethodsResponse(
        String id,

        String name,

        Boolean isActive,

        List<String> accountIds) {
}
