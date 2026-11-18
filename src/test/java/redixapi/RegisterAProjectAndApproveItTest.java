// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;
import redix.support.Projects.Registered;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class RegisterAProjectAndApproveItTest {
    private static @NotNull String uniqueName() {
        return "Registered " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static @NotNull Response signIn(final @NotNull Map<String, Object> registration) {
        return given(Api.anonymous()).body(Map.of("username", registration.get("username"), "password", People.PASSWORD)).post("/auth/sign-in");
    }

    private static @NotNull String tokenOf(final @NotNull Map<String, Object> registration) {
        return signIn(registration).then().statusCode(200).extract().path("accessToken");
    }

    private static @NotNull JsonPath listedTo(final @NotNull String token, final @NotNull String projectId) {
        return new JsonPath(given(Api.as(token)).get("/projects").then().statusCode(200).extract().asString()).setRootPath("find { it.id == '" + projectId + "' }");
    }

    @Test(description = "A registered project has its manager", testName = "1b177f9e-ae16-48cc-845c-457a6fcc5ee9", priority = 1)
    public void aRegisteredProjectHasItsManager() {
        final @NotNull Map<String, Object> registration = Projects.registration(uniqueName(), List.of("A1", "B2"));
        final @NotNull String project = Projects.register(registration).then().statusCode(201).body("status", equalTo("pendingApproval")).extract().path("id");
        final @NotNull JsonPath listed = listedTo(tokenOf(registration), project);
        assertEquals(listed.getString("status"), "pendingApproval");
        assertEquals(listed.getList("units.name"), List.of("A1", "B2"));
    }

    @Test(description = "A bad field creates nothing", testName = "515bb9d4-acf4-43af-8846-927b4b02aac6", priority = 2)
    public void aBadFieldCreatesNothing() {
        for (final Map.Entry<String, String> bad : List.of(Map.entry("mobile", "05-12"), Map.entry("password", "short"), Map.entry("name", " "))) {
            final @NotNull Map<String, Object> registration = Projects.registration(uniqueName(), List.of("A1"));
            registration.put(bad.getKey(), bad.getValue());
            Projects.register(registration).then().statusCode(400);
            signIn(registration).then().statusCode(401);
        }
    }

    @Test(description = "The declared partners are kept in their order and a blank one creates nothing", testName = "36c90ba8-9feb-41bf-b78f-6994a12f7d5e", priority = 3)
    public void theDeclaredPartnersAreKeptInTheirOrderAndABlankOneCreatesNothing() {
        final @NotNull Map<String, Object> registration = Projects.registration(uniqueName(), List.of("A1"));
        registration.put("partners", List.of("Zaid", "Abdullah"));
        final @NotNull String project = Projects.register(registration).then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + project + "/approve").then().statusCode(200);
        assertEquals(given(Api.as(tokenOf(registration))).get("/projects/" + project + "/partners").then().statusCode(200).extract().path("name"), List.of("Zaid", "Abdullah"));
        final @NotNull Map<String, Object> blank = Projects.registration(uniqueName(), List.of("A1"));
        blank.put("partners", List.of("Zaid", " "));
        Projects.register(blank).then().statusCode(400).body("code", equalTo("invalidName"));
        signIn(blank).then().statusCode(401);
    }

    @Test(description = "Someone signed in registers with their own login", testName = "003fa9c0-4104-4571-9144-101184ce3a0d", priority = 4)
    public void someoneSignedInRegistersWithTheirOwnLogin() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull String project = given(person.asked()).body(Map.of("name", uniqueName(), "startDate", LocalDate.now().toString(), "units", List.of("A1"), "partners", List.of()))
                .post("/projects").then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + project + "/approve").then().statusCode(200);
        assertTrue(listedTo(person.token(), project).getList("permissions").contains("manageProject"));
    }

    @Test(description = "A waiting project gives no permission", testName = "91b3a644-bcd9-42f4-b47e-0a970b035b16", priority = 5)
    public void aWaitingProjectGivesNoPermission() {
        final @NotNull Map<String, Object> registration = Projects.registration(uniqueName(), List.of("A1"));
        final @NotNull String project = Projects.register(registration).then().statusCode(201).extract().path("id");
        final @NotNull String token = tokenOf(registration);
        assertEquals(listedTo(token, project).getList("permissions"), List.of());
        given(Api.as(token)).get("/projects/" + project + "/accounts").then().statusCode(403);
        given(Api.as(token)).body(Map.of("name", "B2")).post("/projects/" + project + "/units").then().statusCode(403);
    }

    @Test(description = "Only the admin approves", testName = "4a7e7bdd-29ba-45de-b2c7-e3669e45b896", priority = 6)
    public void onlyTheAdminApproves() {
        final @NotNull Map<String, Object> registration = Projects.registration(uniqueName(), List.of("A1"));
        final @NotNull String project = Projects.register(registration).then().statusCode(201).extract().path("id");
        given(Api.as(tokenOf(registration))).post("/projects/" + project + "/approve").then().statusCode(403);
        given(People.in(Projects.approved(), "manageProject").asked()).post("/projects/" + project + "/approve").then().statusCode(403);
        given(Api.asAdmin()).post("/projects/" + project + "/approve").then().statusCode(200);
    }

    @Test(description = "The admin sees what is needed to decide", testName = "9f406204-4ffb-4a1f-830d-2576c2b9f9c7", priority = 7)
    public void theAdminSeesWhatIsNeededToDecide() {
        final @NotNull Map<String, Object> registration = Projects.registration(uniqueName(), List.of("A1", "B2"));
        registration.put("description", "Four villas");
        final @NotNull String project = Projects.register(registration).then().statusCode(201).extract().path("id");
        final @NotNull JsonPath waiting = new JsonPath(given(Api.asAdmin()).get("/projects/registered").then().statusCode(200).extract().asString())
                .setRootPath("find { it.id == '" + project + "' }");
        assertEquals(waiting.getString("name"), registration.get("name"));
        assertEquals(waiting.getString("managerName"), registration.get("displayName"));
        assertEquals(waiting.getString("managerUsername"), registration.get("username"));
        assertEquals(waiting.getString("managerMobile"), registration.get("mobile"));
        assertEquals(waiting.getList("units"), List.of("A1", "B2"));
        assertEquals(waiting.getString("description"), "Four villas");
        given(People.in(Projects.approved(), "manageProject").asked()).get("/projects/registered").then().statusCode(403);
    }

    @Test(description = "The admin sees nothing inside a project", testName = "9f517241-d3d2-4d6b-b0c5-d711007991c5", priority = 8)
    public void theAdminSeesNothingInsideAProject() {
        final @NotNull Registered registered = Projects.registered();
        given(Api.asAdmin()).get("/projects/" + registered.id() + "/accounts").then().statusCode(403);
        given(Api.asAdmin()).get("/projects/" + registered.id() + "/entries").then().statusCode(403);
        assertTrue(given(Api.asAdmin()).get("/projects").then().extract().jsonPath().getList("findAll { it.id == '" + registered.id() + "' }").isEmpty());
    }

    @Test(description = "A rejection says why", testName = "0dac4d94-0960-4e83-b6ce-812c789bdaa3", priority = 9)
    public void aRejectionSaysWhy() {
        final @NotNull Map<String, Object> registration = Projects.registration(uniqueName(), List.of("A1"));
        final @NotNull String project = Projects.register(registration).then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).body(Map.of("reason", " ")).post("/projects/" + project + "/reject").then().statusCode(400).body("code", equalTo("invalidReason"));
        given(Api.asAdmin()).body(Map.of("reason", "No land deed attached")).post("/projects/" + project + "/reject").then().statusCode(200);
        final @NotNull JsonPath listed = listedTo(tokenOf(registration), project);
        assertEquals(listed.getString("status"), "rejected");
        assertEquals(listed.getString("rejectionReason"), "No land deed attached");
    }

    @Test(description = "A project is decided once", testName = "3a3a6d50-9099-4b79-91c1-10bc1e5739d7", priority = 10)
    public void aProjectIsDecidedOnce() {
        final @NotNull String approved = Projects.registered().id();
        given(Api.asAdmin()).post("/projects/" + approved + "/approve").then().statusCode(409).body("code", equalTo("notPending"));
        given(Api.asAdmin()).body(Map.of("reason", "Too late")).post("/projects/" + approved + "/reject").then().statusCode(409).body("code", equalTo("notPending"));
        final @NotNull String rejected = Projects.register(Projects.registration(uniqueName(), List.of("A1"))).then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).body(Map.of("reason", "No deed")).post("/projects/" + rejected + "/reject").then().statusCode(200);
        given(Api.asAdmin()).post("/projects/" + rejected + "/approve").then().statusCode(409).body("code", equalTo("notPending"));
    }

    @Test(description = "A manager cannot take over the admin", testName = "3fe20ad8-a60c-437d-b4a3-f70992ff6a45", priority = 11)
    public void aManagerCannotTakeOverTheAdmin() {
        final @NotNull Registered registered = Projects.registered();
        final @NotNull String adminId = People.admin().userId();
        given(registered.asked()).body(Map.of("temporaryPassword", "Second-temp0rary-1")).post("/users/" + adminId + "/reset-password").then().statusCode(403);
        given(registered.asked()).body(Map.of("displayName", "Taken", "mobile", "", "email", "taken@redix.test")).put("/users/" + adminId).then().statusCode(403);
        given(registered.asked()).body(Map.of("username", System.getenv("REDIX_ADMIN_USERNAME"))).post("/projects/" + registered.id() + "/users/existing").then().statusCode(404)
                .body("code", equalTo("unknownUser"));
    }

    @Test(description = "Too many tries are refused", testName = "2df265d1-ed99-494c-86e0-a1d6dc6fd1ae", priority = 12, enabled = false)
    public void tooManyTriesAreRefused() {
        throw new AssertionError("needs a server with a low REDIX_Registration__PermitsPerHour; the test server's is raised so the tests' registrations never meet it");
    }

    @Test(description = "Every decision is in the history", testName = "199dd480-1a67-49ae-bf03-f94eb06dd4f8", priority = 13, enabled = false)
    public void everyDecisionIsInTheHistory() {
        throw new AssertionError("History has no route that reads a registration's lines over HTTP; Go's internal/projects tests prove it");
    }
}
