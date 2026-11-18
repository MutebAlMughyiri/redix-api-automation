// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import org.jetbrains.annotations.NotNull;
import redix.model.projects.CreateProjectRequest;
import redix.model.projects.CreateProjectResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/** The projects a test works in: each its own, so no test sees another's rows. */
public final class Projects {
    private Projects() {
    }

    /** A project the admin made and approved, with one unit, its structure copied from the template (UC-006, UC-066); the admin manages it. */
    public static @NotNull String approved() {
        final @NotNull String id = pending();
        given(Api.asAdmin()).post("/projects/" + id + "/approve").then().statusCode(200);
        return id;
    }

    /** A project the admin made, still waiting for approval. */
    public static @NotNull String pending() {
        final @NotNull CreateProjectRequest request = CreateProjectRequest.builder().name("Project " + UUID.randomUUID().toString().substring(0, 8))
                .startDate(LocalDate.now().toString()).units(List.of("A1")).description("").partners(List.of()).build();
        return given(Api.asAdmin()).body(request).post("/projects").then().statusCode(201).extract().as(CreateProjectResponse.class).id();
    }
}
