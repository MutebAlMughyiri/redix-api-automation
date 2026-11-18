// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.model.auth.ChangePasswordRequest;
import redix.model.auth.ChangePasswordResponse;
import redix.model.auth.SignInResponse;
import redix.support.Api;
import redix.support.History;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;
import redix.support.Routes;
import redix.support.Routes.Route;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ChangePasswordTest {
    private static final @NotNull String NEW_PASSWORD = "Changed-passw0rd-2";

    private static @NotNull Response change(final @NotNull String token, final @NotNull String current, final @NotNull String next) {
        return given(Api.as(token)).body(ChangePasswordRequest.builder().currentPassword(current).newPassword(next).build()).post("/auth/change-password");
    }

    private static @NotNull Response signIn(final @NotNull String username, final @NotNull String password) {
        return given(Api.anonymous()).body(Map.of("username", username, "password", password)).post("/auth/sign-in");
    }

    @Test(description = "Changing a temporary password ends the forced change and the new password works", testName = "aeb51ea6-5fad-4985-9eec-931d0d61f5c0", priority = 1)
    public void changingATemporaryPasswordEndsTheForcedChangeAndTheNewPasswordWorks() {
        final @NotNull Person newcomer = People.newcomer(Projects.approved());
        assertTrue(People.signIn(newcomer.username(), People.TEMPORARY).mustChangePassword());
        final @NotNull ChangePasswordResponse changed = change(newcomer.token(), People.TEMPORARY, NEW_PASSWORD).then().statusCode(200).extract()
                .as(ChangePasswordResponse.class);
        assertFalse(changed.mustChangePassword());
        given(Api.as(changed.accessToken())).get("/projects").then().statusCode(200);
        assertFalse(People.signIn(newcomer.username(), NEW_PASSWORD).mustChangePassword());
    }

    @Test(description = "A wrong current password is refused and counts toward the lockout", testName = "e45e606c-9e10-4fbd-9c3c-38ba4fed0cac", priority = 2)
    public void aWrongCurrentPasswordIsRefusedAndCountsTowardTheLockout() {
        final @NotNull Person person = People.in(Projects.approved());
        for (int i = 0; i < 4; i++) {
            change(person.token(), "Wrong-passw0rd-9", NEW_PASSWORD).then().statusCode(400).body("code", equalTo("invalidCredentials"));
        }
        signIn(person.username(), "Wrong-passw0rd-9").then().statusCode(401).body("code", equalTo("invalidCredentials"));
        signIn(person.username(), People.PASSWORD).then().statusCode(401).body("code", equalTo("accountLocked"));
    }

    @Test(description = "A new password short without a letter or a digit or unchanged is refused", testName = "ca48488f-14f9-4cf6-944e-d5bdcd4287fd", priority = 3)
    public void aNewPasswordShortWithoutALetterOrADigitOrUnchangedIsRefused() {
        final @NotNull Person person = People.in(Projects.approved());
        for (final String weak : List.of("Short1a", "NoDigitsHere", "1234567890", People.PASSWORD)) {
            change(person.token(), People.PASSWORD, weak).then().statusCode(400).body("code", equalTo("weakPassword"));
        }
        signIn(person.username(), People.PASSWORD).then().statusCode(200);
    }

    @Test(description = "A new password that is the user name is refused", testName = "9af13653-cc51-4ca1-824d-ee2908e5f2b5", priority = 4)
    public void aNewPasswordThatIsTheUserNameIsRefused() {
        final @NotNull Person person = People.in(Projects.approved());
        change(person.token(), People.PASSWORD, person.username()).then().statusCode(400).body("code", equalTo("weakPassword"));
        signIn(person.username(), People.PASSWORD).then().statusCode(200);
    }

    @Test(description = "Password policy tests", testName = "2514e70b-7bad-4fda-a055-f8436108cb5a", priority = 5)
    public void passwordPolicyTests() {
        final @NotNull Person person = People.in(Projects.approved());
        change(person.token(), People.PASSWORD, "Seven1a").then().statusCode(400).body("code", equalTo("weakPassword"));
        final @NotNull String eight = "Eight1ab";
        final @NotNull String token = change(person.token(), People.PASSWORD, eight).then().statusCode(200).extract().as(ChangePasswordResponse.class).accessToken();
        change(token, eight, NEW_PASSWORD).then().statusCode(200);
        signIn(person.username(), NEW_PASSWORD).then().statusCode(200);
    }

    @Test(description = "Changing the password signs out the other devices and keeps this one", testName = "53e362d6-eb51-4d0a-aeee-4a43dcd9e2e8", priority = 6)
    public void changingThePasswordSignsOutTheOtherDevicesAndKeepsThisOne() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull SignInResponse other = People.signIn(person.username(), People.PASSWORD);
        final @NotNull String kept = change(person.token(), People.PASSWORD, NEW_PASSWORD).then().statusCode(200).extract().as(ChangePasswordResponse.class)
                .accessToken();
        given(Api.as(other.accessToken())).get("/projects").then().statusCode(401);
        given(Api.anonymous()).body(Map.of("refreshToken", other.refreshToken())).post("/auth/refresh").then().statusCode(401);
        given(Api.as(kept)).get("/projects").then().statusCode(200);
        given(person.asked()).get("/projects").then().statusCode(200);
    }

    @Test(description = "A password change is in the history with the values before and after but never the password hash", testName = "48efbe91-3c2e-45c3-a350-c4f43beccaf0", priority = 7)
    public void aPasswordChangeIsInTheHistoryWithTheValuesBeforeAndAfterButNeverThePasswordHash() {
        final @NotNull Person person = People.in(Projects.approved());
        assertTrue(History.kindsOf(person.asked()).contains("passwordChanged"));
        final @NotNull String written = History.mineAsWritten(person.asked());
        assertFalse(written.contains("Hash") || written.contains(People.PASSWORD) || written.contains(People.TEMPORARY), written);
    }

    @Test(description = "Changing the password needs a session", testName = "e751582b-769a-487e-bbd0-262d132555cc", priority = 8)
    public void changingThePasswordNeedsASession() {
        given(Api.anonymous()).body(ChangePasswordRequest.builder().currentPassword(People.PASSWORD).newPassword(NEW_PASSWORD).build()).post("/auth/change-password")
                .then().statusCode(401);
    }

    @Test(description = "Every endpoint not open to all refuses a session with a temporary password", testName = "5052123c-23d4-4cdd-a0f5-fa4890e86724", priority = 9)
    public void everyEndpointNotOpenToAllRefusesASessionWithATemporaryPassword() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person newcomer = People.newcomer(project);
        final @NotNull List<Route> guarded = Routes.every().stream().filter(route -> !route.open() && !route.takesATemporaryPassword()).toList();
        assertTrue(guarded.size() > 100, "the document names " + guarded.size() + " guarded routes");
        for (final Route route : guarded) {
            final int status = given(Api.as(newcomer.token())).body("{}").request(route.method(), route.in(project)).statusCode();
            assertEquals(status, 403, route + " answered a temporary password");
        }
    }
}
