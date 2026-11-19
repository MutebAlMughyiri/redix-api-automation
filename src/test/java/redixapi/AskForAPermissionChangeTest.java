// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class AskForAPermissionChangeTest {
    private static @NotNull String requests(final @NotNull String projectId) {
        return "/projects/" + projectId + "/permission-requests";
    }

    /** What as asking for gives and takes, with reason, answers. */
    private static @NotNull Response ask(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull List<String> gives, final @NotNull List<String> takes, final @NotNull String reason) {
        return given(as).body(Map.of("gives", gives, "takes", takes, "reason", reason)).post(requests(projectId));
    }

    /** The id of the request as made, asking for gives with reason. */
    private static @NotNull String asked(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull List<String> gives) {
        return ask(as, projectId, gives, List.of(), "I record the expenses").then().statusCode(201).extract().path("id");
    }

    private static @NotNull List<String> permissionsOf(final @NotNull Person person, final @NotNull String projectId) {
        return given(person.asked()).get("/projects").then().statusCode(200).extract().path("find { it.id == '" + projectId + "' }.permissions");
    }

    private static @NotNull List<String> toldOf(final @NotNull RequestSpecification as) {
        return given(as).get("/notifications").then().statusCode(200).extract().jsonPath().getList("kind");
    }

    private static @NotNull JsonPath waiting(final @NotNull String projectId) {
        return given(Api.asAdmin()).get(requests(projectId)).then().statusCode(200).extract().jsonPath();
    }

    @Test(description = "Asking changes nothing until a manager decides and the managers are told", testName = "be349b1a-ebdc-4aa8-8258-1a665e8cb071", priority = 1)
    public void askingChangesNothingUntilAManagerDecidesAndTheManagersAreTold() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project, "seeReports");
        final int told = toldOf(Api.asAdmin()).size();

        ask(person.asked(), project, List.of("recordEntries"), List.of("seeReports"), "I record the expenses").then().statusCode(201)
                .body("status", equalTo("waiting")).body("reason", equalTo("I record the expenses"));

        assertTrue(permissionsOf(person, project).contains("seeReports") && !permissionsOf(person, project).contains("recordEntries"),
                "asking changed what the person holds");
        assertEquals(toldOf(Api.asAdmin()).size(), told + 1, "the manager was not told");
        assertTrue(toldOf(Api.asAdmin()).contains("permissionRequested"), "the manager's notice is not a permission request");
        final @NotNull JsonPath listed = waiting(project);
        assertEquals(listed.getList("gives"), List.of(List.of("recordEntries")));
        assertEquals(listed.getList("takes"), List.of(List.of("seeReports")));
        assertFalse(listed.getString("[0].displayName").isEmpty(), "the request does not name who asked");
        assertEquals(given(person.asked()).get(requests(project) + "/mine").then().statusCode(200).extract().jsonPath().getList("status"), List.of("waiting"));
    }

    @Test(description = "An approved request is applied as ticking is and the person is told", testName = "681d8172-b1f7-4785-b974-be5df5f57beb", priority = 2)
    public void anApprovedRequestIsAppliedAsTickingIsAndThePersonIsTold() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project, "seeReports");
        final @NotNull String request = ask(person.asked(), project, List.of("recordEntries", "moveMoney"), List.of("seeReports"), "I record the expenses")
                .then().statusCode(201).extract().path("id");

        given(Api.asAdmin()).post(requests(project) + "/" + request + "/approve").then().statusCode(200);

        final @NotNull List<String> held = permissionsOf(person, project);
        assertTrue(held.containsAll(List.of("recordEntries", "moveMoney", "readProject")) && !held.contains("seeReports"), "held after approval: " + held);
        assertTrue(toldOf(person.asked()).contains("permissionRequestApproved"), "the person was not told it was approved");
        given(Api.asAdmin()).post(requests(project) + "/" + request + "/approve").then().statusCode(404);
    }

    @Test(description = "A rejection needs its reason and changes nothing", testName = "275cc556-ee72-48dd-82e0-4392f32aab08", priority = 3)
    public void aRejectionNeedsItsReasonAndChangesNothing() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull String reject = requests(project) + "/" + asked(person.asked(), project, List.of("recordEntries")) + "/reject";

        given(Api.asAdmin()).body(Map.of("reason", " ")).post(reject).then().statusCode(400).body("code", equalTo("reasonRequired"));
        given(Api.asAdmin()).body(Map.of("reason", "Not this month")).post(reject).then().statusCode(200);

        assertFalse(permissionsOf(person, project).contains("recordEntries"), "a rejected request gave its permission");
        final @NotNull JsonPath mine = given(person.asked()).get(requests(project) + "/mine").then().statusCode(200).extract().jsonPath();
        assertEquals(mine.getString("[0].status"), "rejected");
        assertEquals(mine.getString("[0].rejectionReason"), "Not this month");
        assertTrue(toldOf(person.asked()).contains("permissionRequestRejected"), "the person was not told it was rejected");
    }

    @Test(description = "A new request replaces the waiting one and one can be withdrawn", testName = "dac34156-be6d-4ac4-8453-603024514af5", priority = 4)
    public void aNewRequestReplacesTheWaitingOneAndOneCanBeWithdrawn() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        ask(person.asked(), project, List.of("recordEntries"), List.of(), "First").then().statusCode(201);
        ask(person.asked(), project, List.of("seeReports"), List.of(), "Second").then().statusCode(201);

        assertEquals(waiting(project).getList("reason"), List.of("Second"));

        given(person.asked()).delete(requests(project) + "/mine").then().statusCode(200);
        given(person.asked()).delete(requests(project) + "/mine").then().statusCode(404);
        assertEquals(waiting(project).getList("$"), List.of());
    }

    @Test(description = "A request asks only for what changes something with a reason", testName = "9119a44f-fda6-4d5b-bc09-653a94b3511b", priority = 5)
    public void aRequestAsksOnlyForWhatChangesSomethingWithAReason() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project, "seeReports");
        final @NotNull Map<String, Response> refused = Map.of(
                "nothingAsked", ask(person.asked(), project, List.of(), List.of(), "Nothing"),
                "alreadyHeld", ask(person.asked(), project, List.of("seeReports"), List.of(), "Again"),
                "notHeld", ask(person.asked(), project, List.of(), List.of("moveMoney"), "Not mine"),
                "reasonRequired", ask(person.asked(), project, List.of("moveMoney"), List.of(), "  "));

        refused.forEach((code, answer) -> answer.then().statusCode(400).body("code", equalTo(code)));
        ask(person.asked(), project, List.of("approveProjects"), List.of(), "The admin's").then().statusCode(400).body("code", equalTo("invalidPermission"));
        assertEquals(given(person.asked()).get(requests(project) + "/mine").then().statusCode(200).extract().jsonPath().getList("$"), List.of(),
                "a refused request was kept");
    }

    @Test(description = "The last manager keeps managing users whatever they ask", testName = "4d64ed9a-6031-4896-b554-f4ffbb352f83", priority = 6)
    public void theLastManagerKeepsManagingUsersWhateverTheyAsk() {
        final @NotNull String project = Projects.approved();
        final @NotNull String request = ask(Api.asAdmin(), project, List.of(), List.of("manageUsers"), "I step down").then().statusCode(201).extract().path("id");

        given(Api.asAdmin()).post(requests(project) + "/" + request + "/approve").then().statusCode(409).body("code", equalTo("lastManager"));

        given(Api.asAdmin()).get("/projects/" + project + "/users").then().statusCode(200);
    }

    @Test(description = "Only whoever manages the users sees the requests and decides them", testName = "8325f98a-5c70-4062-8d0b-487ea9319a15", priority = 7)
    public void onlyWhoeverManagesTheUsersSeesTheRequestsAndDecidesThem() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull String request = asked(person.asked(), project, List.of("recordEntries"));

        given(person.asked()).get(requests(project)).then().statusCode(403);
        given(person.asked()).post(requests(project) + "/" + request + "/approve").then().statusCode(403);
        given(person.asked()).body(Map.of("reason", "Mine")).post(requests(project) + "/" + request + "/reject").then().statusCode(403);
    }
}
