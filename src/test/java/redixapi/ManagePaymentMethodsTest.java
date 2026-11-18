// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Entries;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ManagePaymentMethodsTest {
    private static final @NotNull List<String> SEEDED = List.of("شبكة", "تحويل", "كاش");

    private static @NotNull String methods(final @NotNull String projectId) {
        return "/projects/" + projectId + "/payment-methods";
    }

    private static @NotNull JsonPath listed(final @NotNull String projectId) {
        return given(Api.asAdmin()).get(methods(projectId)).then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull List<String> offered(final @NotNull String projectId) {
        return given(Api.asAdmin()).get(methods(projectId) + "/offered").then().statusCode(200).extract().path("name");
    }

    private static @NotNull Response add(final @NotNull String projectId, final @NotNull String name) {
        return given(Api.asAdmin()).body(Map.of("name", name)).post(methods(projectId));
    }

    private static @NotNull String idOf(final @NotNull String projectId, final @NotNull String name) {
        return listed(projectId).getString("find { it.name == '" + name + "' }.id");
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    @Test(description = "An approved project starts with the three methods", testName = "0d5958da-8180-4d67-bb0f-787ef5c7d475", priority = 1)
    public void anApprovedProjectStartsWithTheThreeMethods() {
        final @NotNull JsonPath seeded = listed(Projects.approved());
        assertEquals(seeded.getList("name"), SEEDED);
        assertEquals(seeded.getList("isActive"), List.of(true, true, true));
    }

    @Test(description = "Projects take their own rows of the templates methods", testName = "537b7219-47f7-4c8f-97c9-e9e208586fda", priority = 2)
    public void projectsTakeTheirOwnRowsOfTheTemplatesMethods() {
        final @NotNull List<String> first = new ArrayList<>(listed(Projects.approved()).getList("id"));
        first.retainAll(listed(Projects.approved()).getList("id"));
        assertEquals(first, List.of(), "two projects share a method's row");
    }

    @Test(description = "A project copied from an earlier one still starts from the seed", testName = "a559e4b0-bacf-48d1-81f8-41d7771b40aa", priority = 3)
    public void aProjectCopiedFromAnEarlierOneStillStartsFromTheSeed() {
        final @NotNull String source = Projects.approved();
        add(source, "Cheque").then().statusCode(200);
        given(Api.asAdmin()).delete(methods(source) + "/" + idOf(source, "تحويل")).then().statusCode(200);
        final @NotNull String copy = given(Api.asAdmin()).body(Map.of("name", "Copy " + UUID.randomUUID().toString().substring(0, 8), "startDate", LocalDate.now().toString(),
                "units", List.of("A1"), "partners", List.of(), "structureSourceId", source)).post("/projects").then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + copy + "/approve").then().statusCode(200);
        assertEquals(listed(copy).getList("name"), SEEDED);
    }

    @Test(description = "Finance adds a method at the end of the list", testName = "871066b1-95fb-4804-850c-3ce34eeba1e7", priority = 4)
    public void financeAddsAMethodAtTheEndOfTheList() {
        final @NotNull String project = Projects.approved();
        add(project, "  Cheque  ").then().statusCode(200);
        assertEquals(listed(project).getList("name").getLast(), "Cheque");
        assertTrue(offered(project).contains("Cheque"));
    }

    @Test(description = "Finance renames a method", testName = "b3f6464d-f553-43aa-b5ff-5702f7c8705e", priority = 5)
    public void financeRenamesAMethod() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = idOf(project, "تحويل");
        given(Api.asAdmin()).body(Map.of("name", "Bank transfer")).put(methods(project) + "/" + id).then().statusCode(200);
        assertEquals(listed(project).getList("name"), List.of("شبكة", "Bank transfer", "كاش"));
        assertEquals(listed(project).getString("[1].id"), id);
    }

    @Test(description = "A method needs a name of at most100 characters", testName = "e2522e43-64e4-4325-9919-94fadb7f71f6", priority = 6)
    public void aMethodNeedsANameOfAtMost100Characters() {
        final @NotNull String project = Projects.approved();
        refusedAs(add(project, "m".repeat(101)), 400, "invalidName");
        refusedAs(add(project, "   "), 400, "invalidName");
        refusedAs(given(Api.asAdmin()).body(Map.of("name", "")).put(methods(project) + "/" + idOf(project, "شبكة")), 400, "invalidName");
        add(project, "m".repeat(100)).then().statusCode(200);
        assertEquals(listed(project).getList("name").size(), 4);
    }

    @Test(description = "A disabled method leaves entry and stays on the list", testName = "cc741e08-bbec-4ab6-87e3-5e55766976fd", priority = 7)
    public void aDisabledMethodLeavesEntryAndStaysOnTheList() {
        final @NotNull String project = Projects.approved();
        given(Api.asAdmin()).post(methods(project) + "/" + idOf(project, "كاش") + "/disable").then().statusCode(200);
        assertFalse(offered(project).contains("كاش"));
        assertEquals(listed(project).getString("find { it.name == 'كاش' }.isActive"), "false");
    }

    @Test(description = "Enabling brings a method back into entry", testName = "215f85e6-5d2c-4688-9929-c212e38db69d", priority = 8)
    public void enablingBringsAMethodBackIntoEntry() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = idOf(project, "كاش");
        given(Api.asAdmin()).post(methods(project) + "/" + id + "/disable").then().statusCode(200);
        given(Api.asAdmin()).post(methods(project) + "/" + id + "/enable").then().statusCode(200);
        assertEquals(offered(project), SEEDED);
    }

    @Test(description = "Finance deletes a method no expense was paid with", testName = "db06567f-6685-4fbc-ac85-ef1b0dc92e4a", priority = 9)
    public void financeDeletesAMethodNoExpenseWasPaidWith() {
        final @NotNull String project = Projects.approved();
        given(Api.asAdmin()).delete(methods(project) + "/" + idOf(project, "كاش")).then().statusCode(200);
        assertEquals(listed(project).getList("name"), List.of("شبكة", "تحويل"));
    }

    @Test(description = "A method an expense was paid with is not deleted", testName = "19082cf7-a551-4995-bc79-ff73bb44354b", priority = 10)
    public void aMethodAnExpenseWasPaidWithIsNotDeleted() {
        final @NotNull String project = Projects.approved();
        Entries.draft(project);
        refusedAs(given(Api.asAdmin()).delete(methods(project) + "/" + idOf(project, "شبكة")), 409, "paymentMethodUsed");
        given(Api.asAdmin()).post(methods(project) + "/" + idOf(project, "شبكة") + "/disable").then().statusCode(200);
        assertEquals(listed(project).getList("name"), SEEDED);
    }

    @Test(description = "A change in one project leaves the template and other projects alone", testName = "cdf64cc6-f2cd-4ca8-a2b7-6b87c4fe8e74", priority = 11)
    public void aChangeInOneProjectLeavesTheTemplateAndOtherProjectsAlone() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        add(project, "Cheque").then().statusCode(200);
        given(Api.asAdmin()).body(Map.of("name", "Card")).put(methods(project) + "/" + idOf(project, "شبكة")).then().statusCode(200);
        given(Api.asAdmin()).delete(methods(project) + "/" + idOf(project, "كاش")).then().statusCode(200);
        assertEquals(listed(other).getList("name"), SEEDED);
        assertEquals(listed(Projects.approved()).getList("name"), SEEDED);
    }

    @Test(description = "A route of one project never reaches anothers methods", testName = "113f6239-799d-45e4-af28-1b40fd3035de", priority = 12)
    public void aRouteOfOneProjectNeverReachesAnothersMethods() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull String theirs = idOf(other, "شبكة");
        given(Api.asAdmin()).body(Map.of("name", "Taken")).put(methods(project) + "/" + theirs).then().statusCode(404);
        given(Api.asAdmin()).post(methods(project) + "/" + theirs + "/disable").then().statusCode(404);
        given(Api.asAdmin()).delete(methods(project) + "/" + theirs).then().statusCode(404);
        assertEquals(listed(other).getList("name"), SEEDED);
    }

    @Test(description = "Everyone with access reads methods and only finance changes them", testName = "9da0ab60-96b6-40dc-8bb3-6b8ae883e3e7", priority = 13)
    public void everyoneWithAccessReadsMethodsAndOnlyFinanceChangesThem() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person reader = People.in(project, "recordEntries");
        given(reader.asked()).get(methods(project)).then().statusCode(200).body("name", equalTo(SEEDED));
        given(reader.asked()).body(Map.of("name", "Cheque")).post(methods(project)).then().statusCode(403);
        given(reader.asked()).delete(methods(project) + "/" + idOf(project, "كاش")).then().statusCode(403);
        given(People.in(project, "manageSettings").asked()).body(Map.of("name", "Cheque")).post(methods(project)).then().statusCode(200);
    }

    @Test(description = "A finished project refuses every change and stays readable", testName = "d35628bd-262d-40fb-b70b-caeea6a8f1ad", priority = 14)
    public void aFinishedProjectRefusesEveryChangeAndStaysReadable() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = idOf(project, "كاش");
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);
        refusedAs(add(project, "Cheque"), 409, "projectFinished");
        refusedAs(given(Api.asAdmin()).body(Map.of("name", "Cash")).put(methods(project) + "/" + id), 409, "projectFinished");
        refusedAs(given(Api.asAdmin()).post(methods(project) + "/" + id + "/disable"), 409, "projectFinished");
        refusedAs(given(Api.asAdmin()).delete(methods(project) + "/" + id), 409, "projectFinished");
        assertEquals(listed(project).getList("name"), SEEDED);
    }

    @Test(description = "A method added after a delete goes last and no two methods share a place", testName = "6a54cfee-d658-4763-85a7-e79d23303419", priority = 15)
    public void aMethodAddedAfterADeleteGoesLastAndNoTwoMethodsShareAPlace() {
        final @NotNull String project = Projects.approved();
        given(Api.asAdmin()).delete(methods(project) + "/" + idOf(project, "شبكة")).then().statusCode(200);
        add(project, "Cheque").then().statusCode(200);
        add(project, "Card").then().statusCode(200);
        assertEquals(listed(project).getList("name"), List.of("تحويل", "كاش", "Cheque", "Card"));
    }
}
