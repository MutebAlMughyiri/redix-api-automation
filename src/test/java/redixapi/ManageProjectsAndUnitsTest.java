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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ManageProjectsAndUnitsTest {
    private static @NotNull String uniqueName() {
        return "Project " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static @NotNull Map<String, Object> project(final @NotNull String name, final @NotNull List<String> units) {
        final @NotNull Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("startDate", LocalDate.now().toString());
        body.put("units", units);
        body.put("partners", List.of());
        return body;
    }

    private static @NotNull Response start(final @NotNull Map<String, Object> body) {
        return given(Api.asAdmin()).body(body).post("/projects");
    }

    /** A project the admin started with units and approved, which the admin manages, as starting it answered. */
    private static @NotNull JsonPath approvedWith(final @NotNull String... units) {
        final @NotNull JsonPath started = start(project(uniqueName(), List.of(units))).then().statusCode(201).extract().jsonPath();
        given(Api.asAdmin()).post("/projects/" + started.getString("id") + "/approve").then().statusCode(200);
        return started;
    }

    private static @NotNull JsonPath listed(final @NotNull String projectId) {
        return new JsonPath(given(Api.asAdmin()).get("/projects").then().statusCode(200).extract().jsonPath().prettify())
                .setRootPath("find { it.id == '" + projectId + "' }");
    }

    private static @NotNull List<String> projectNames() {
        return given(Api.asAdmin()).get("/projects").then().statusCode(200).extract().jsonPath().getList("name");
    }

    private static @NotNull Response describe(final @NotNull String projectId, final @NotNull String description) {
        return given(Api.asAdmin()).body(Map.of("description", description)).put("/projects/" + projectId + "/description");
    }

    private static @NotNull Response addUnit(final @NotNull String projectId, final @NotNull String name) {
        return given(Api.asAdmin()).body(Map.of("name", name)).post("/projects/" + projectId + "/units");
    }

    private static @NotNull Response renameUnit(final @NotNull String projectId, final @NotNull String unitId, final @NotNull String name) {
        return given(Api.asAdmin()).body(Map.of("name", name)).put("/projects/" + projectId + "/units/" + unitId);
    }

    private static @NotNull Response close(final @NotNull String projectId) {
        return given(Api.asAdmin()).post("/projects/" + projectId + "/close");
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    @Test(description = "More_info_a description is kept for everyone in the project and in the history", testName = "9f8499bc-1e97-414e-b75e-5e2da38d2dc1", priority = 1)
    public void moreInfoADescriptionIsKeptForEveryoneInTheProjectAndInTheHistory() {
        final @NotNull String project = approvedWith("A1").getString("id");
        final @NotNull Person member = People.in(project);
        describe(project, "  Villas by the sea  ").then().statusCode(200).body("description", equalTo("Villas by the sea"));
        given(member.asked()).get("/projects").then().statusCode(200).body("find { it.id == '" + project + "' }.description", equalTo("Villas by the sea"));
    }

    @Test(description = "More_info_an empty description clears it", testName = "81625f15-ba3a-499b-b55b-ba7070da8864", priority = 2)
    public void moreInfoAnEmptyDescriptionClearsIt() {
        final @NotNull String project = approvedWith("A1").getString("id");
        describe(project, "Villas by the sea").then().statusCode(200);
        describe(project, "").then().statusCode(200).body("description", equalTo(""));
        assertEquals(listed(project).getString("description"), "");
    }

    @Test(description = "More_info_a description of2000 characters is kept and one longer is refused", testName = "2777ff0c-c14a-4585-b7c2-32027c36b2d4", priority = 3)
    public void moreInfoADescriptionOf2000CharactersIsKeptAndOneLongerIsRefused() {
        final @NotNull String project = approvedWith("A1").getString("id");
        describe(project, "d".repeat(2000)).then().statusCode(200);
        refusedAs(describe(project, "d".repeat(2001)), 400, "invalidDescription");
        assertEquals(listed(project).getString("description").length(), 2000);
    }

    @Test(description = "More_info_a project made with a description too long is not made", testName = "1e8d2a61-38c8-40ab-9b8d-5ae558aace54", priority = 4)
    public void moreInfoAProjectMadeWithADescriptionTooLongIsNotMade() {
        final @NotNull String name = uniqueName();
        final @NotNull Map<String, Object> body = project(name, List.of("A1"));
        body.put("description", "d".repeat(2001));
        refusedAs(start(body), 400, "invalidDescription");
        assertTrue(!projectNames().contains(name));
    }

    @Test(description = "More_info_a registration with a description too long creates nothing", testName = "1991c670-7754-4902-90db-a1d5cb82a980", priority = 5)
    public void moreInfoARegistrationWithADescriptionTooLongCreatesNothing() {
        final @NotNull Map<String, Object> body = Projects.registration(uniqueName(), List.of("A1"));
        body.put("description", "d".repeat(2001));
        refusedAs(Projects.register(body), 400, "invalidDescription");
        given(Api.anonymous()).body(Map.of("username", body.get("username"), "password", People.PASSWORD)).post("/auth/sign-in").then().statusCode(401);
    }

    @Test(description = "More_info_only whoever manages the project describes it", testName = "6ed94fdb-e94c-4065-9a79-1031af7a1292", priority = 6)
    public void moreInfoOnlyWhoeverManagesTheProjectDescribesIt() {
        final @NotNull String project = approvedWith("A1").getString("id");
        given(People.in(project).asked()).body(Map.of("description", "Mine")).put("/projects/" + project + "/description").then().statusCode(403);
        given(People.in(project, "manageProject").asked()).body(Map.of("description", "Ours")).put("/projects/" + project + "/description").then()
                .statusCode(200).body("description", equalTo("Ours"));
    }

    @Test(description = "More_info_a finished project keeps its description", testName = "7dab4f02-43f8-40ea-87ca-96d2842ead63", priority = 7)
    public void moreInfoAFinishedProjectKeepsItsDescription() {
        final @NotNull String project = approvedWith("A1").getString("id");
        describe(project, "Villas by the sea").then().statusCode(200);
        close(project).then().statusCode(200);
        refusedAs(describe(project, "Changed"), 409, "projectFinished");
        assertEquals(listed(project).getString("description"), "Villas by the sea");
    }

    @Test(description = "More_info_a description written when the project is made is kept", testName = "4131d5f6-dc88-4448-81ea-453e1247c7c1", priority = 8)
    public void moreInfoADescriptionWrittenWhenTheProjectIsMadeIsKept() {
        final @NotNull Map<String, Object> body = project(uniqueName(), List.of("A1"));
        body.put("description", "Villas by the sea");
        final @NotNull String project = start(body).then().statusCode(201).body("description", equalTo("Villas by the sea")).extract().path("id");
        assertEquals(listed(project).getString("description"), "Villas by the sea");
    }

    @Test(description = "More_info_the admin reads the description before deciding", testName = "f2d5c59c-9964-41b1-b204-35dea758a733", priority = 9)
    public void moreInfoTheAdminReadsTheDescriptionBeforeDeciding() {
        final @NotNull Map<String, Object> body = Projects.registration(uniqueName(), List.of("A1"));
        body.put("description", "Villas by the sea");
        final @NotNull String project = Projects.register(body).then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).get("/projects/registered").then().statusCode(200)
                .body("find { it.id == '" + project + "' }.description", equalTo("Villas by the sea"));
    }

    @Test(description = "A project keeps its units in order", testName = "3fc22322-e540-4c41-b078-f596a333e9fa", priority = 10)
    public void aProjectKeepsItsUnitsInOrder() {
        assertEquals(approvedWith("B2", "A1", "C3").getList("units.name"), List.of("B2", "A1", "C3"));
    }

    @Test(description = "A bad unit name creates nothing", testName = "41437138-d56c-4c0d-b308-23bda736e25c", priority = 11)
    public void aBadUnitNameCreatesNothing() {
        final @NotNull String name = uniqueName();
        refusedAs(start(project(name, List.of("A1", " "))), 400, "invalidName");
        assertTrue(!projectNames().contains(name));
    }

    @Test(description = "A request missing a field is a bad request", testName = "4ebafc99-c9fc-4031-99a4-d867168ee6a1", priority = 12)
    public void aRequestMissingAFieldIsABadRequest() {
        final @NotNull String name = uniqueName();
        given(Api.asAdmin()).body(Map.of("name", name, "units", List.of("A1"))).post("/projects").then().statusCode(400);
        assertTrue(!projectNames().contains(name));
    }

    @Test(description = "A unit is added at the end", testName = "ce76f080-abaf-41bf-9661-3f7b2e142900", priority = 13)
    public void aUnitIsAddedAtTheEnd() {
        final @NotNull String project = approvedWith("B2", "A1").getString("id");
        addUnit(project, "C0").then().statusCode(200).body("units.name", equalTo(List.of("B2", "A1", "C0")));
    }

    @Test(description = "A finished project is read only and stays listed", testName = "228399a4-fcdd-4db4-9b82-8844df57d76a", priority = 14)
    public void aFinishedProjectIsReadOnlyAndStaysListed() {
        final @NotNull String project = approvedWith("A1").getString("id");
        close(project).then().statusCode(200).body("status", equalTo("finished"));
        refusedAs(addUnit(project, "B2"), 409, "projectFinished");
        assertEquals(listed(project).getString("status"), "finished");
        assertEquals(listed(project).getList("units.name"), List.of("A1"));
    }

    @Test(description = "A running project is renamed and the history shows both names", testName = "6cfcf5ca-9069-4d89-876c-72a652e7490c", priority = 15)
    public void aRunningProjectIsRenamedAndTheHistoryShowsBothNames() {
        final @NotNull String project = approvedWith("A1").getString("id");
        final @NotNull String renamed = uniqueName();
        given(Api.asAdmin()).body(Map.of("name", "  " + renamed + "  ")).put("/projects/" + project).then().statusCode(200).body("name", equalTo(renamed));
        assertEquals(listed(project).getString("name"), renamed);
    }

    @Test(description = "A unit is renamed in place and the history shows both names", testName = "acdc9193-d8cd-43df-806b-8e5177eacbad", priority = 16)
    public void aUnitIsRenamedInPlaceAndTheHistoryShowsBothNames() {
        final @NotNull JsonPath created = approvedWith("A1", "B2", "C3");
        renameUnit(created.getString("id"), created.getString("units[1].id"), "B9").then().statusCode(200)
                .body("units.name", equalTo(List.of("A1", "B9", "C3"))).body("units[1].id", equalTo(created.getString("units[1].id")));
    }

    @Test(description = "A project or a unit is renamed only to an acceptable name", testName = "3408bd1b-e088-4ca0-a778-82fb7bfb4eae", priority = 17)
    public void aProjectOrAUnitIsRenamedOnlyToAnAcceptableName() {
        final @NotNull JsonPath created = approvedWith("A1");
        final @NotNull String project = created.getString("id");
        refusedAs(given(Api.asAdmin()).body(Map.of("name", "   ")).put("/projects/" + project), 400, "invalidName");
        refusedAs(given(Api.asAdmin()).body(Map.of("name", "p".repeat(101))).put("/projects/" + project), 400, "invalidName");
        refusedAs(renameUnit(project, created.getString("units[0].id"), ""), 400, "invalidName");
        refusedAs(renameUnit(project, created.getString("units[0].id"), "u".repeat(101)), 400, "invalidName");
        assertEquals(listed(project).getString("name"), created.getString("name"));
        assertEquals(listed(project).getList("units.name"), List.of("A1"));
    }

    @Test(description = "A rename cannot take another units name and may keep its own", testName = "44a28a22-af0e-4ab3-9dd7-fd4a282baa26", priority = 18)
    public void aRenameCannotTakeAnotherUnitsNameAndMayKeepItsOwn() {
        final @NotNull JsonPath created = approvedWith("A1", "B2");
        final @NotNull String project = created.getString("id");
        refusedAs(renameUnit(project, created.getString("units[1].id"), " A1 "), 409, "unitNameTaken");
        renameUnit(project, created.getString("units[0].id"), "A1").then().statusCode(200).body("units.name", equalTo(List.of("A1", "B2")));
    }

    @Test(description = "A unit nothing uses is deleted and the history keeps what it was", testName = "bfd603de-7f1e-4e1b-ba9f-da2b6706a2dd", priority = 19)
    public void aUnitNothingUsesIsDeletedAndTheHistoryKeepsWhatItWas() {
        final @NotNull JsonPath created = approvedWith("A1", "B2");
        given(Api.asAdmin()).delete("/projects/" + created.getString("id") + "/units/" + created.getString("units[0].id")).then().statusCode(200)
                .body("units.name", equalTo(List.of("B2")));
    }

    @Test(description = "Expenses and contracts belong to the project so a unit is deleted whatever they hold", testName = "67da9801-30ff-476e-aa72-10e3f892e3c3", priority = 20)
    public void expensesAndContractsBelongToTheProjectSoAUnitIsDeletedWhateverTheyHold() {
        final @NotNull JsonPath created = approvedWith("A1", "B2");
        final @NotNull String project = created.getString("id");
        Entries.approved(project);
        given(Api.asAdmin()).delete("/projects/" + project + "/units/" + created.getString("units[0].id")).then().statusCode(200)
                .body("units.name", equalTo(List.of("B2")));
    }

    @Test(description = "A unit added after a deletion still goes last", testName = "d7eccb1e-b98f-4843-a800-49a53df45337", priority = 21)
    public void aUnitAddedAfterADeletionStillGoesLast() {
        final @NotNull JsonPath created = approvedWith("A1", "B2", "C3");
        final @NotNull String project = created.getString("id");
        given(Api.asAdmin()).delete("/projects/" + project + "/units/" + created.getString("units[1].id")).then().statusCode(200);
        addUnit(project, "B2").then().statusCode(200).body("units.name", equalTo(List.of("A1", "C3", "B2")));
    }

    @Test(description = "A finished project refuses renames and deletions", testName = "50bbc73b-c071-4626-b438-8a7b15e95195", priority = 22)
    public void aFinishedProjectRefusesRenamesAndDeletions() {
        final @NotNull JsonPath created = approvedWith("A1");
        final @NotNull String project = created.getString("id");
        close(project).then().statusCode(200);
        refusedAs(given(Api.asAdmin()).body(Map.of("name", uniqueName())).put("/projects/" + project), 409, "projectFinished");
        refusedAs(renameUnit(project, created.getString("units[0].id"), "B2"), 409, "projectFinished");
        refusedAs(given(Api.asAdmin()).delete("/projects/" + project + "/units/" + created.getString("units[0].id")), 409, "projectFinished");
        refusedAs(close(project), 409, "projectFinished");
    }

    @Test(description = "A unit of another project is not found", testName = "3377280b-86cb-445c-85b5-ffeb07c5d548", priority = 23)
    public void aUnitOfAnotherProjectIsNotFound() {
        final @NotNull String project = approvedWith("A1").getString("id");
        final @NotNull String otherUnit = approvedWith("B2").getString("units[0].id");
        renameUnit(project, otherUnit, "C3").then().statusCode(404);
        given(Api.asAdmin()).delete("/projects/" + project + "/units/" + otherUnit).then().statusCode(404);
    }

    @Test(description = "Only whoever manages the project renames and deletes", testName = "6deded6b-b7eb-4516-a16d-2fdc35f99700", priority = 24)
    public void onlyWhoeverManagesTheProjectRenamesAndDeletes() {
        final @NotNull JsonPath created = approvedWith("A1");
        final @NotNull String project = created.getString("id");
        final @NotNull Person member = People.in(project);
        given(member.asked()).body(Map.of("name", uniqueName())).put("/projects/" + project).then().statusCode(403);
        given(member.asked()).body(Map.of("name", "B2")).put("/projects/" + project + "/units/" + created.getString("units[0].id")).then().statusCode(403);
        given(member.asked()).delete("/projects/" + project + "/units/" + created.getString("units[0].id")).then().statusCode(403);
        given(member.asked()).post("/projects/" + project + "/close").then().statusCode(403);
        assertEquals(listed(project).getList("units.name"), List.of("A1"));
    }

    @Test(description = "A project with entries waiting for approval cannot be closed", testName = "00ec1d68-1b39-4658-9ab4-234e8fd0895a", priority = 25)
    public void aProjectWithEntriesWaitingForApprovalCannotBeClosed() {
        final @NotNull String project = approvedWith("A1").getString("id");
        Entries.waiting(project);
        refusedAs(close(project), 409, "pendingEntries");
        assertEquals(listed(project).getString("status"), "active");
    }

    @Test(description = "Only entries waiting for approval hold a project open", testName = "3a51c9fa-078f-4e74-9f28-f42804f19cfa", priority = 26)
    public void onlyEntriesWaitingForApprovalHoldAProjectOpen() {
        final @NotNull String project = approvedWith("A1").getString("id");
        Entries.draft(project);
        Entries.approved(project);
        Entries.rejected(project);
        close(project).then().statusCode(200).body("status", equalTo("finished"));
    }

    @Test(description = "An entry waiting in another project holds nothing open", testName = "01483623-c23c-44d1-be05-2343857f4c7c", priority = 27)
    public void anEntryWaitingInAnotherProjectHoldsNothingOpen() {
        final @NotNull String project = approvedWith("A1").getString("id");
        Entries.waiting(Projects.approved());
        close(project).then().statusCode(200).body("status", equalTo("finished"));
    }

    @Test(description = "The status travels as its glossary name", testName = "1633c73e-7ea9-4336-9f30-98e08155dcf6", priority = 28)
    public void theStatusTravelsAsItsGlossaryName() {
        final @NotNull String pending = Projects.pending();
        assertEquals(listed(pending).getString("status"), "pendingApproval");
        final @NotNull String project = approvedWith("A1").getString("id");
        assertEquals(listed(project).getString("status"), "active");
        close(project).then().statusCode(200);
        assertEquals(listed(project).getString("status"), "finished");
    }

    @Test(description = "A units name cannot repeat in its project compared trimmed", testName = "55492df2-fd15-463f-89df-99ac2f5f2531", priority = 29)
    public void aUnitsNameCannotRepeatInItsProjectComparedTrimmed() {
        final @NotNull String project = approvedWith("A1").getString("id");
        refusedAs(addUnit(project, "  A1  "), 409, "unitNameTaken");
        assertEquals(listed(project).getList("units.name"), List.of("A1"));
    }

    @Test(description = "A new project cannot repeat a units name and saves nothing", testName = "d194902d-d7fa-4f81-8d68-9012f736c52e", priority = 30)
    public void aNewProjectCannotRepeatAUnitsNameAndSavesNothing() {
        final @NotNull String name = uniqueName();
        refusedAs(start(project(name, List.of("A1", " A1"))), 400, "unitNameTaken");
        assertTrue(!projectNames().contains(name));
    }

    @Test(description = "Projects may repeat a name and each their units names", testName = "01058145-28b0-47af-a3b6-0f3bddb26f8a", priority = 31)
    public void projectsMayRepeatANameAndEachTheirUnitsNames() {
        final @NotNull String name = uniqueName();
        final @NotNull String first = start(project(name, List.of("A1"))).then().statusCode(201).extract().path("id");
        final @NotNull String second = start(project(name, List.of("A1"))).then().statusCode(201).extract().path("id");
        assertTrue(!first.equals(second));
        given(Api.asAdmin()).get("/projects").then().body("findAll { it.name == '" + name + "' }.size()", equalTo(2));
    }

    @Test(description = "A registration cannot repeat a units name", testName = "08465e47-9b47-4f7f-a10b-b7fbbfe2e3d8", priority = 32)
    public void aRegistrationCannotRepeatAUnitsName() {
        final @NotNull Map<String, Object> body = Projects.registration(uniqueName(), List.of("A1", "A1 "));
        refusedAs(Projects.register(body), 400, "unitNameTaken");
        given(Api.anonymous()).body(Map.of("username", body.get("username"), "password", People.PASSWORD)).post("/auth/sign-in").then().statusCode(401);
    }

    @Test(description = "A project or a unit needs a name of at most100 characters", testName = "5229df27-8dc9-455b-b35d-bbda0b155a1b", priority = 33)
    public void aProjectOrAUnitNeedsANameOfAtMost100Characters() {
        final @NotNull String hundred = "P" + UUID.randomUUID().toString().replace("-", "") + "p".repeat(67);
        final @NotNull String project = start(project("  " + hundred + "  ", List.of("u".repeat(100)))).then().statusCode(201)
                .body("name", equalTo(hundred)).body("units[0].name", equalTo("u".repeat(100))).extract().path("id");
        refusedAs(start(project(hundred + "p", List.of("A1"))), 400, "invalidName");
        refusedAs(start(project(uniqueName(), List.of("u".repeat(101)))), 400, "invalidName");
        refusedAs(start(project("   ", List.of("A1"))), 400, "invalidName");
        given(Api.asAdmin()).get("/projects").then().body("name", not(hasItem(hundred + "p")));
        assertEquals(listed(project).getString("name"), hundred);
    }

    @Test(description = "Whoever starts a project manages it", testName = "d7b8c6cc-4046-4cc7-ac65-a425e8c28cd1", priority = 34)
    public void whoeverStartsAProjectManagesIt() {
        final @NotNull Map<String, Object> body = Projects.registration(uniqueName(), List.of("A1"));
        final @NotNull String project = Projects.register(body).then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + project + "/approve").then().statusCode(200);
        final @NotNull String token = People.signIn((String) body.get("username"), People.PASSWORD).accessToken();
        given(Api.as(token)).get("/projects").then().statusCode(200).body("find { it.id == '" + project + "' }.permissions", hasItem("manageProject"));
        given(Api.as(token)).body(Map.of("name", "B2")).post("/projects/" + project + "/units").then().statusCode(200);
    }

    @Test(description = "Creating a project is in the history with its name", testName = "83321662-f97a-4953-b17e-ef7836bfbb57", priority = 35, enabled = false)
    public void creatingAProjectIsInTheHistoryWithItsName() {
        throw new AssertionError("History has no route that reads a project's own lines over HTTP; Go's internal/projects tests prove it");
    }

    @Test(description = "Adding a unit is in the history with its name", testName = "004984d7-f449-43ac-b147-1562acf4d232", priority = 36, enabled = false)
    public void addingAUnitIsInTheHistoryWithItsName() {
        throw new AssertionError("History has no route that reads a project's own lines over HTTP; Go's internal/projects tests prove it");
    }

    @Test(description = "Closing a project is in the history with its status old and new", testName = "017244c9-1750-465c-808c-3a79fb1696f5", priority = 37, enabled = false)
    public void closingAProjectIsInTheHistoryWithItsStatusOldAndNew() {
        throw new AssertionError("History has no route that reads a project's own lines over HTTP; Go's internal/projects tests prove it");
    }
}
