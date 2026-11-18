// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static io.restassured.RestAssured.given;

/** Suppliers a test needs (UC-017). */
public final class Suppliers {
    private Suppliers() {
    }

    /** A supplier the admin added to the project, named name, with no VAT number. */
    public static @NotNull String add(final @NotNull String projectId, final @NotNull String name) {
        return given(Api.asAdmin()).body(Map.of("name", name)).post("/projects/" + projectId + "/suppliers").then().statusCode(201).extract().path("id");
    }
}
