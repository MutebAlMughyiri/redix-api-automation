// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.model.entries;

import java.util.List;

public record ApproveEntriesResponse(
        List<String> approved,

        List<FailedItem> failed) {

    public record FailedItem(
            String entryId,

            String code) {
    }
}
