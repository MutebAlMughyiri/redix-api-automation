package redix.model.suppliers;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class EditSupplierRequest {
    private String name;

    private String vatNumber;

    private String mobile;

    private String defaultItemId;
}
