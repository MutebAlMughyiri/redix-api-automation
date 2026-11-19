// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import redix.model.auth.ChangePasswordRequest;
import redix.model.auth.Session;
import redix.model.users.CreateUserRequest;
import redix.model.users.UserView;

import java.util.List;
import java.util.Map;
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

    /** The temporary password the manager gives every person made here (UC-007), which they must change at their first sign-in (AC-02.1). */
    public static final @NotNull String TEMPORARY = "Temporary-passw0rd-1";

    /**
     * A new person as the manager types them, holding ReadProject and permissions: every field unique, a username of letters and digits, so the
     * password policy can only refuse one that is the username for being it, and the temporary password TEMPORARY.
     */
    public static @NotNull CreateUserRequest.CreateUserRequestBuilder request(final @NotNull String... permissions) {
        final @NotNull String tag = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        return CreateUserRequest.builder().displayName("Person " + tag).username("person" + tag).mobile("05" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999))
                .email("person" + tag + "@redix.test").temporaryPassword(TEMPORARY).permissions(List.of(permissions)).roleName("").partnerIds(List.of());
    }

    /** A person made in projectId as request says, signed in with their temporary password, which is all such a session may change. */
    public static @NotNull Person newcomer(final @NotNull String projectId, final @NotNull CreateUserRequest request) {
        final @NotNull String id = given(Api.asAdmin()).body(request).post("/projects/" + projectId + "/users").then().statusCode(201).extract()
                .as(UserView.class).id();
        return new Person(id, request.getUsername(), request.getEmail(), signIn(request.getUsername(), TEMPORARY).accessToken());
    }

    /** A person made in projectId holding ReadProject and permissions, signed in with their temporary password. */
    public static @NotNull Person newcomer(final @NotNull String projectId, final @NotNull String... permissions) {
        return newcomer(projectId, request(permissions).build());
    }

    /** A person in projectId holding ReadProject and permissions, their temporary password already changed to PASSWORD, signed in. */
    public static @NotNull Person in(final @NotNull String projectId, final @NotNull String... permissions) {
        final @NotNull Person newcomer = newcomer(projectId, permissions);
        given(newcomer.asked()).body(ChangePasswordRequest.builder().currentPassword(TEMPORARY).newPassword(PASSWORD).build()).post("/auth/change-password")
                .then().statusCode(200);
        return new Person(newcomer.id(), newcomer.username(), newcomer.email(), signIn(newcomer.username(), PASSWORD).accessToken());
    }

    /** The admin, signed in on a device of their own once the run's first sign-in has changed the password the API was started with. */
    public static @NotNull Session admin() {
        Admin.token();
        return signIn(System.getenv("REDIX_ADMIN_USERNAME"), System.getenv("REDIX_ADMIN_NEW_PASSWORD"));
    }

    /** Signs username in with password, wanting 200. */
    public static @NotNull Session signIn(final @NotNull String username, final @NotNull String password) {
        return given(Api.anonymous()).body(Map.of("username", username, "password", password)).post("/auth/sign-in").then().statusCode(200).extract()
                .as(Session.class);
    }
}
