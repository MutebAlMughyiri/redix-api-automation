// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.model.auth.SignInResponse;
import redix.support.Api;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class KeepAPhoneWithFingerprintOrFaceSignedInTest {
    private static @NotNull JsonPath biometrics(final @NotNull String token, final boolean on) {
        return given(Api.as(token)).body(Map.of("on", on)).post("/auth/biometrics").then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull Response renew(final @NotNull String refreshToken) {
        return given(Api.anonymous()).body(Map.of("refreshToken", refreshToken)).post("/auth/refresh");
    }

    @Test(description = "An unused phone with biometrics never times out", testName = "5327000f-3250-408c-ae6e-cf47e1c1b937", priority = 1, enabled = false)
    public void anUnusedPhoneWithBiometricsNeverTimesOut() {
        throw new AssertionError("needs the server's clock moved past 12 hours; Go's internal/auth tests prove it");
    }

    @Test(description = "Turning biometrics off brings back the12 hours", testName = "78b4db70-c8a8-44eb-af03-e6ca25d887f4", priority = 2, enabled = false)
    public void turningBiometricsOffBringsBackThe12Hours() {
        throw new AssertionError("needs the server's clock moved 12 hours; Go's internal/auth tests prove it");
    }

    @Test(description = "Biometrics do not outlive a revoked phone", testName = "2cdd53ee-53f0-4c67-83c1-d17aab66ef9f", priority = 3)
    public void biometricsDoNotOutliveARevokedPhone() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull JsonPath signedOut = biometrics(People.signIn(person.username(), People.PASSWORD).accessToken(), true);
        given(Api.as(signedOut.getString("accessToken"))).post("/auth/sign-out").then().statusCode(204);
        renew(signedOut.getString("refreshToken")).then().statusCode(401);
        final @NotNull JsonPath other = biometrics(People.signIn(person.username(), People.PASSWORD).accessToken(), true);
        given(person.asked()).body(Map.of("currentPassword", People.PASSWORD, "newPassword", "Changed-passw0rd-2")).post("/auth/change-password").then()
                .statusCode(200);
        renew(other.getString("refreshToken")).then().statusCode(401);
    }

    @Test(description = "Biometrics belong to one phone", testName = "6e858bbb-9be6-4bb0-ae88-89a8b75ca533", priority = 4)
    public void biometricsBelongToOnePhone() {
        final @NotNull Person person = People.in(Projects.approved());
        biometrics(person.token(), true);
        final @NotNull SignInResponse otherPhone = People.signIn(person.username(), People.PASSWORD);
        assertFalse(otherPhone.usesBiometrics());
        renew(otherPhone.refreshToken()).then().statusCode(200).body("usesBiometrics", equalTo(false));
    }

    @Test(description = "A new sign in starts with biometrics off", testName = "53624ee7-b833-4295-826f-35d89d60845e", priority = 5)
    public void aNewSignInStartsWithBiometricsOff() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull JsonPath on = biometrics(person.token(), true);
        given(Api.as(on.getString("accessToken"))).post("/auth/sign-out").then().statusCode(204);
        assertFalse(People.signIn(person.username(), People.PASSWORD).usesBiometrics());
    }

    @Test(description = "Turning biometrics on is answered", testName = "0b2feacf-b39f-4b1a-926e-5421f774949b", priority = 6)
    public void turningBiometricsOnIsAnswered() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull JsonPath on = biometrics(person.token(), true);
        assertTrue(on.getBoolean("usesBiometrics"));
        assertFalse(on.getString("accessToken").isEmpty() || on.getString("refreshToken").isEmpty());
        renew(on.getString("refreshToken")).then().statusCode(200).body("usesBiometrics", equalTo(true));
        final @NotNull JsonPath off = biometrics(on.getString("accessToken"), false);
        assertFalse(off.getBoolean("usesBiometrics"));
        renew(off.getString("refreshToken")).then().statusCode(200).body("usesBiometrics", equalTo(false));
    }

    @Test(description = "Changing the password keeps biometrics on this phone", testName = "2a3b5f6f-4a18-45cf-acfd-a69813d660db", priority = 7)
    public void changingThePasswordKeepsBiometricsOnThisPhone() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull JsonPath on = biometrics(person.token(), true);
        given(Api.as(on.getString("accessToken"))).body(Map.of("currentPassword", People.PASSWORD, "newPassword", "Changed-passw0rd-2"))
                .post("/auth/change-password").then().statusCode(200).body("usesBiometrics", equalTo(true));
    }

    @Test(description = "A temporary password cannot turn biometrics on", testName = "3fe1d1cf-e76e-49ff-adc4-f7536dc1e8eb", priority = 8)
    public void aTemporaryPasswordCannotTurnBiometricsOn() {
        final @NotNull Person newcomer = People.newcomer(Projects.approved());
        given(newcomer.asked()).body(Map.of("on", true)).post("/auth/biometrics").then().statusCode(403);
        assertFalse(People.signIn(newcomer.username(), People.TEMPORARY).usesBiometrics());
    }

    @Test(description = "Turning biometrics on and off is in history", testName = "0b45b195-52ec-4410-af6f-54ccda7ac05d", priority = 9, enabled = false)
    public void turningBiometricsOnAndOffIsInHistory() {
        throw new AssertionError("History has no route that reads a phone's lines over HTTP; Go's internal/auth tests prove it");
    }
}
