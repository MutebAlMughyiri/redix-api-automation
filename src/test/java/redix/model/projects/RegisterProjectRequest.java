package redix.model.projects;

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
public class RegisterProjectRequest {
    private String name;

    private String startDate;

    private List<String> units;

    private String displayName;

    private String username;

    private String mobile;

    private String password;

    private String description;

    private List<String> partners;
}
