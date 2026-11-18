package redix.model.sync;

import java.util.Map;

public record SyncResponse(
        String stamp,

        Map<String, Object> lists) {
}
