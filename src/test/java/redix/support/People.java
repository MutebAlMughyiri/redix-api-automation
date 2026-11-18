// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import redix.model.auth.SignInResponse;
import redix.model.users.CreateUserRequest;
import redix.model.users.CreateUserResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;

/** The people a test signs in as: each made fresh in a project by its manager, the admin, so no test leans on another's login (UC-007). */
public final class People {
    /** The password every person made here signs in with once their temporary one is changed. */
    public static final @NotNull String PASSWORD = "Chosen-passw0rd-1";

    private People() {
    }

    /** A person made in a project, signed in. */
    public record Person(@NotNull String id, @NotNull String username, @NotNull String email, @NotNull String token) {
        /** A request as this person. */
        public @NotNull RequestSpecification asked() {
            return Api.as(token);
        }
    }

    /** A person in projectId holding ReadProject and permissions, their temporary password already changed to PASSWORD, signed in. */
    public static @NotNull Person in(final @NotNull String projectId, final @NotNull String... permissions) {
        final @NotNull String tag = UUID.randomUUID().toString().substring(0, 8);
        final @NotNull String temporary = "Temporary-passw0rd-1";
        final @NotNull CreateUserRequest request = CreateUserRequest.builder().displayName("Person " + tag).username("person" + tag)
                .mobile("05" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999)).email("person" + tag + "@redix.test")
                .temporaryPassword(temporary).permissions(List.of(permissions)).roleName("").partnerIds(List.of()).build();
        final @NotNull String id = given(Api.asAdmin()).body(request).post("/projects/" + projectId + "/users").then().statusCode(201).extract()
                .as(CreateUserResponse.class).id();
        final @NotNull String first = signIn(request.getUsername(), temporary).accessToken();
        given(Api.as(first)).body(Map.of("currentPassword", temporary, "newPassword", PASSWORD)).post("/auth/change-password").then().statusCode(200);
        return new Person(id, request.getUsername(), request.getEmail(), signIn(request.getUsername(), PASSWORD).accessToken());
    }

    /** Signs username in with password, wanting 200. */
    public static @NotNull SignInResponse signIn(final @NotNull String username, final @NotNull String password) {
        return given(Api.anonymous()).body(Map.of("username", username, "password", password)).post("/auth/sign-in").then().statusCode(200).extract()
                .as(SignInResponse.class);
    }
}
