package redix.support;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The admin the API was started with: REDIX_ADMIN_USERNAME and REDIX_ADMIN_PASSWORD, the server's REDIX_Admin__Username and
 * REDIX_Admin__Password. The API makes that password temporary (BR-29), so the first sign-in changes it to REDIX_ADMIN_NEW_PASSWORD; on a
 * server whose admin already did, the new password is the one that signs in. Signed in once, at the first test that asks.
 */
final class Admin {
    private static @NotNull String token = "";

    private Admin() {
    }

    /** The admin's access token, from one sign-in for the whole run. */
    static synchronized @NotNull String token() {
        if (token.isEmpty()) {
            final @NotNull String username = required("REDIX_ADMIN_USERNAME");
            final @NotNull String newPassword = required("REDIX_ADMIN_NEW_PASSWORD");
            final @NotNull Response first = signIn(username, required("REDIX_ADMIN_PASSWORD"));
            if (first.statusCode() == 200 && Boolean.TRUE.equals(first.path("mustChangePassword"))) {
                RestAssured.given(Api.anonymous()).header("Authorization", "Bearer " + first.path("accessToken"))
                        .body(Map.of("currentPassword", required("REDIX_ADMIN_PASSWORD"), "newPassword", newPassword))
                        .post("/auth/change-password").then().statusCode(200);
            }
            final @NotNull Response signedIn = first.statusCode() == 200 && !Boolean.TRUE.equals(first.path("mustChangePassword"))
                    ? first : signIn(username, newPassword);
            token = signedIn.then().statusCode(200).extract().path("accessToken");
        }
        return token;
    }

    private static @NotNull Response signIn(final @NotNull String username, final @NotNull String password) {
        return RestAssured.given(Api.anonymous()).body(Map.of("username", username, "password", password)).post("/auth/sign-in");
    }

    private static @NotNull String required(final @NotNull String name) {
        final String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set: the admin the API was started with signs every admin request in");
        }
        return value;
    }
}
