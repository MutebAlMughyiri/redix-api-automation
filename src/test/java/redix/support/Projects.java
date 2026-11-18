// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import redix.model.projects.CreateProjectRequest;
import redix.model.projects.CreateProjectResponse;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

    /** A project someone with no account registered and the admin approved; they manage it alone (UC-066). */
    public record Registered(@NotNull String id, @NotNull String username, @NotNull String token) {
        /** A request as the one who registered it. */
        public @NotNull RequestSpecification asked() {
            return Api.as(token);
        }
    }

    /** A registration of a project named name with units, by a new person with unique details whose password is People.PASSWORD. */
    public static @NotNull Map<String, Object> registration(final @NotNull String name, final @NotNull List<String> units) {
        final @NotNull String tag = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        return new HashMap<>(Map.of("name", name, "startDate", LocalDate.now().toString(), "units", units, "partners", List.of(),
                "displayName", "Manager " + tag, "username", "manager" + tag, "mobile", "05" + tag, "password", People.PASSWORD));
    }

    /** Sends registration, as anyone may. */
    public static @NotNull Response register(final @NotNull Map<String, Object> registration) {
        return given(Api.anonymous()).body(registration).post("/projects/register");
    }

    /** A project registered with one unit and approved, its registrant signed in. */
    public static @NotNull Registered registered() {
        final @NotNull Map<String, Object> body = registration("Project " + UUID.randomUUID().toString().substring(0, 8), List.of("A1"));
        final @NotNull String id = register(body).then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + id + "/approve").then().statusCode(200);
        return new Registered(id, (String) body.get("username"), People.signIn((String) body.get("username"), People.PASSWORD).accessToken());
    }

    /** A project the admin made, still waiting for approval. */
    public static @NotNull String pending() {
        final @NotNull CreateProjectRequest request = CreateProjectRequest.builder().name("Project " + UUID.randomUUID().toString().substring(0, 8))
                .startDate(LocalDate.now().toString()).units(List.of("A1")).description("").partners(List.of()).build();
        return given(Api.asAdmin()).body(request).post("/projects").then().statusCode(201).extract().as(CreateProjectResponse.class).id();
    }
}
