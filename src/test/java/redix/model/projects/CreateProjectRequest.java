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
public class CreateProjectRequest {
    private String name;

    private String startDate;

    private List<String> units;

    private String structureSourceId;

    private String description;

    private List<String> partners;
}
