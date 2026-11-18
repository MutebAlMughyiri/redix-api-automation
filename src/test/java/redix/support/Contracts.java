// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static io.restassured.RestAssured.given;

/** Contracts a test needs (UC-020). */
public final class Contracts {
    private Contracts() {
    }

    /** A contract of 10,000 the admin made on the item with a new supplier named contractor, answering its id. */
    public static @NotNull String add(final @NotNull String projectId, final @NotNull String itemId, final @NotNull String contractor) {
        final @NotNull String supplierId = Suppliers.add(projectId, contractor);
        given(Api.asAdmin()).body(Map.of("itemId", itemId, "supplierId", supplierId, "value", 10000, "isOpenValue", false)).post("/projects/" + projectId + "/contracts")
                .then().statusCode(200);
        return given(Api.asAdmin()).get("/projects/" + projectId + "/contracts").then().statusCode(200).extract().path("find { it.supplierId == '" + supplierId + "' }.id");
    }
}
