package redix.model.users;

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
public class CreateUserRequest {
    private String displayName;

    private String username;

    private String mobile;

    private String email;

    private String temporaryPassword;

    private List<String> permissions;

    private String roleName;

    private List<String> partnerIds;
}
