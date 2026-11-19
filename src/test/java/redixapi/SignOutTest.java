// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.model.auth.Session;
import redix.support.Api;
import redix.support.History;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.assertEquals;

public class SignOutTest {
    @Test(description = "A sign out ends this device only", testName = "a5c218a9-2bf8-4f8d-942b-235896d0b698", priority = 1)
    public void aSignOutEndsThisDeviceOnly() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull Session other = People.signIn(person.username(), People.PASSWORD);
        final @NotNull Session leaving = People.signIn(person.username(), People.PASSWORD);
        given(Api.as(leaving.accessToken())).post("/auth/sign-out").then().statusCode(204);
        given(Api.as(leaving.accessToken())).get("/projects").then().statusCode(401);
        given(Api.anonymous()).body(Map.of("refreshToken", leaving.refreshToken())).post("/auth/refresh").then().statusCode(401);
        given(Api.as(other.accessToken())).get("/projects").then().statusCode(200);
        given(person.asked()).get("/projects").then().statusCode(200);
    }

    @Test(description = "A sign out is in the history", testName = "22246f46-cf57-45de-a037-7d190f4d7c20", priority = 2)
    public void aSignOutIsInTheHistory() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull Session leaving = People.signIn(person.username(), People.PASSWORD);
        given(Api.as(leaving.accessToken())).post("/auth/sign-out").then().statusCode(204);
        assertEquals(History.kindsOf(person.asked()).get(0), "signOut");
    }

    @Test(description = "Signing out needs a session", testName = "5c7c69f0-6417-4c9f-8108-a51ab6a36494", priority = 3)
    public void signingOutNeedsASession() {
        given(Api.anonymous()).post("/auth/sign-out").then().statusCode(401);
    }
}
