// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Entries;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.assertEquals;

public class TellPeopleWhatHappenedTest {
    private static @NotNull JsonPath told(final @NotNull RequestSpecification as) {
        return given(as).get("/notifications").then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull List<String> kindsAbout(final @NotNull RequestSpecification as, final @NotNull String entryId) {
        return told(as).getList("findAll { it.entryId == '" + entryId + "' }.kind");
    }

    private static @NotNull Person author(final @NotNull String projectId) {
        return People.in(projectId, "recordEntries", "chargeAnyAccount", "deleteEntries");
    }

    private static @NotNull String sentBy(final @NotNull Person author, final @NotNull String projectId) {
        final @NotNull String entry = Entries.draft(projectId, author.asked());
        given(author.asked()).post("/projects/" + projectId + "/entries/" + entry + "/submit").then().statusCode(200);
        return entry;
    }

    @Test(description = "Sending an entry tells whoever decides and not who sent it", testName = "7af8f836-4eff-4582-97f4-7a3b3d8f7bed", priority = 1)
    public void sendingAnEntryTellsWhoeverDecidesAndNotWhoSentIt() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person decider = People.in(project, "approveEntries");
        final @NotNull Person author = author(project);
        final @NotNull String entry = sentBy(author, project);
        assertEquals(kindsAbout(decider.asked(), entry), List.of("entrySubmitted"));
        assertEquals(kindsAbout(author.asked(), entry), List.of());
        assertEquals(kindsAbout(People.in(project).asked(), entry), List.of());
    }

    @Test(description = "Saving a draft tells nobody", testName = "03a2976b-9088-40d8-9882-fc6119996988", priority = 2)
    public void savingADraftTellsNobody() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person decider = People.in(project, "approveEntries");
        final @NotNull String draft = Entries.draft(project, author(project).asked());
        assertEquals(kindsAbout(decider.asked(), draft), List.of());
    }

    @Test(description = "Sending a draft later tells whoever decides", testName = "6581c63a-09ae-471a-bc73-fae5e098018b", priority = 3)
    public void sendingADraftLaterTellsWhoeverDecides() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person decider = People.in(project, "approveEntries");
        final @NotNull Person author = author(project);
        final @NotNull String draft = Entries.draft(project, author.asked());
        given(author.asked()).post("/projects/" + project + "/entries/" + draft + "/submit").then().statusCode(200);
        assertEquals(kindsAbout(decider.asked(), draft), List.of("entrySubmitted"));
    }

    @Test(description = "Approving tells its author", testName = "d5ff00b0-6fb9-4d06-a37c-d1782a1d4672", priority = 4)
    public void approvingTellsItsAuthor() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person author = author(project);
        final @NotNull String entry = sentBy(author, project);
        Entries.approve(project, entry);
        assertEquals(kindsAbout(author.asked(), entry), List.of("entryApproved"));
    }

    @Test(description = "Rejecting tells its author", testName = "c8f5c153-2fac-4295-822a-c30b5c674d21", priority = 5)
    public void rejectingTellsItsAuthor() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person author = author(project);
        final @NotNull String entry = sentBy(author, project);
        given(Api.asAdmin()).body(Map.of("reason", "Wrong supplier")).post("/projects/" + project + "/entries/" + entry + "/reject").then().statusCode(200);
        assertEquals(kindsAbout(author.asked(), entry), List.of("entryRejected"));
    }

    @Test(description = "Nobody is told of their own decision", testName = "1de537ac-0b10-425f-9b41-37c91092f94c", priority = 6)
    public void nobodyIsToldOfTheirOwnDecision() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person decider = People.in(project, "approveEntries", "recordEntries", "chargeAnyAccount");
        final @NotNull String entry = sentBy(decider, project);
        given(decider.asked()).body(Map.of("entryIds", List.of(entry))).post("/projects/" + project + "/entries/approve").then().statusCode(200);
        assertEquals(kindsAbout(decider.asked(), entry), List.of());
    }

    @Test(description = "The admins decision tells the manager of the project", testName = "77cd614d-e7f5-42f7-8379-15e3b8b543cb", priority = 7)
    public void theAdminsDecisionTellsTheManagerOfTheProject() {
        final @NotNull Map<String, Object> registration = Projects.registration("Told " + System.nanoTime(), List.of("A1"));
        final @NotNull String project = Projects.register(registration).then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + project + "/approve").then().statusCode(200);
        final @NotNull String token = People.signIn((String) registration.get("username"), People.PASSWORD).accessToken();
        assertEquals(told(Api.as(token)).getList("findAll { it.projectId == '" + project + "' }.kind"), List.of("projectApproved"));
    }

    @Test(description = "Marking one read changes only your own", testName = "905c7320-0e16-488c-8836-a37273566f68", priority = 8)
    public void markingOneReadChangesOnlyYourOwn() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person decider = People.in(project, "approveEntries");
        final @NotNull Person other = People.in(project, "approveEntries");
        final @NotNull String entry = sentBy(author(project), project);
        final @NotNull String mine = told(decider.asked()).getString("find { it.entryId == '" + entry + "' }.id");
        given(other.asked()).post("/notifications/" + mine + "/read").then().statusCode(404);
        assertEquals(told(decider.asked()).getString("find { it.id == '" + mine + "' }.isRead"), "false");
        given(decider.asked()).post("/notifications/" + mine + "/read").then().statusCode(200);
        assertEquals(told(decider.asked()).getString("find { it.id == '" + mine + "' }.isRead"), "true");
        assertEquals(told(other.asked()).getString("find { it.entryId == '" + entry + "' }.isRead"), "false");
    }

    @Test(description = "Deleting a draft takes what was told about it", testName = "a2759345-2635-4aaf-a0ad-b8b5f3efb621", priority = 9)
    public void deletingADraftTakesWhatWasToldAboutIt() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person decider = People.in(project, "approveEntries");
        final @NotNull Person author = author(project);
        final @NotNull String entry = sentBy(author, project);
        given(Api.asAdmin()).body(Map.of("reason", "Wrong supplier")).post("/projects/" + project + "/entries/" + entry + "/reject").then().statusCode(200);
        assertEquals(kindsAbout(author.asked(), entry), List.of("entryRejected"));
        given(author.asked()).delete("/projects/" + project + "/entries/" + entry).then().statusCode(200);
        assertEquals(kindsAbout(author.asked(), entry), List.of());
        assertEquals(kindsAbout(decider.asked(), entry), List.of());
    }

    @Test(description = "Someone who left a project is no longer told of it", testName = "3df479e5-34ba-4d75-9c9f-2f1b4b3c07be", priority = 10)
    public void someoneWhoLeftAProjectIsNoLongerToldOfIt() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person decider = People.in(project, "approveEntries");
        final @NotNull String entry = sentBy(author(project), project);
        assertEquals(kindsAbout(decider.asked(), entry), List.of("entrySubmitted"));
        given(Api.asAdmin()).delete("/projects/" + project + "/users/" + decider.id() + "/permissions/readProject").then().statusCode(200);
        assertEquals(told(decider.asked()).getList("findAll { it.projectId == '" + project + "' }"), List.of());
    }

    @Test(description = "Nobody reads them without signing in", testName = "11847f8a-0127-4cdd-be52-f1b1d7169898", priority = 11)
    public void nobodyReadsThemWithoutSigningIn() {
        given(Api.anonymous()).get("/notifications").then().statusCode(401);
    }

    @Test(description = "A notification about an entry names it in a row of its own and one about the project has none", testName = "b960d471-d697-46f7-ab9c-cb195349f891", priority = 12, enabled = false)
    public void aNotificationAboutAnEntryNamesItInARowOfItsOwnAndOneAboutTheProjectHasNone() {
        throw new AssertionError("about the row that keeps a notification's entry, inside the server; Go's internal/notifications tests prove it");
    }

    @Test(description = "The database refuses a notification naming an entry that is not", testName = "ea4345ee-e5ea-43b4-907e-d3cee67a51a7", priority = 13, enabled = false)
    public void theDatabaseRefusesANotificationNamingAnEntryThatIsNot() {
        throw new AssertionError("about the table's foreign key, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "The database keeps a notification naming its project", testName = "e30d3943-182e-475e-9a4d-b9df110faaca", priority = 14, enabled = false)
    public void theDatabaseKeepsANotificationNamingItsProject() {
        throw new AssertionError("about the table's foreign key, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "The database refuses a notification naming no project", testName = "53a87daf-0f7c-4aaf-8655-2a149e254bb4", priority = 15, enabled = false)
    public void theDatabaseRefusesANotificationNamingNoProject() {
        throw new AssertionError("about the table's foreign key, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "Notification project key_every notification keeps its project which the database now names", testName = "14b5911f-8eb5-4a4c-9725-95063c7119be", priority = 16, enabled = false)
    public void notificationProjectKeyEveryNotificationKeepsItsProjectWhichTheDatabaseNowNames() {
        throw new AssertionError("about the migration that gave every notification its project's key, inside the server; Go's internal/db tests prove it");
    }
}
