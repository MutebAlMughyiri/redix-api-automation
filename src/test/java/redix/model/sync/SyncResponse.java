// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.sync;

import java.util.Map;

public record SyncResponse(
        String stamp,

        Map<String, Object> lists) {
}
