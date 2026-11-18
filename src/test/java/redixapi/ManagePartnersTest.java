// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Entries;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;
import redix.support.Structure;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ManagePartnersTest {
    private static @NotNull String partners(final @NotNull String projectId) {
        return "/projects/" + projectId + "/partners";
    }

    private static @NotNull Response add(final @NotNull String projectId, final @NotNull String name, final @NotNull String userId) {
        final @NotNull Map<String, Object> body = new HashMap<>(Map.of("name", name));
        if (!userId.isEmpty()) {
            body.put("userId", userId);
        }
        return given(Api.asAdmin()).body(body).post(partners(projectId));
    }

    private static @NotNull String added(final @NotNull String projectId, final @NotNull String name, final @NotNull String userId) {
        add(projectId, name, userId).then().statusCode(200);
        return listed(projectId, Api.asAdmin()).getString("find { it.name == '" + name + "' }.id");
    }

    private static @NotNull Response edit(final @NotNull String projectId, final @NotNull String partnerId, final @NotNull String name, final @NotNull String userId) {
        final @NotNull Map<String, Object> body = new HashMap<>(Map.of("name", name));
        if (!userId.isEmpty()) {
            body.put("userId", userId);
        }
        return given(Api.asAdmin()).body(body).put(partners(projectId) + "/" + partnerId);
    }

    private static @NotNull JsonPath listed(final @NotNull String projectId, final @NotNull RequestSpecification as) {
        return given(as).get(partners(projectId)).then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull List<String> capitalTables(final @NotNull String projectId) {
        return Structure.tablesOf(projectId, Structure.itemId(projectId, Structure.CAPITAL)).getList("name");
    }

    private static @NotNull JsonPath history(final @NotNull String projectId, final @NotNull RequestSpecification as, final @NotNull String query) {
        return given(as).get(partners(projectId) + "/history" + query).then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull String movedIn(final @NotNull String projectId, final @NotNull String partnerId, final int amount) {
        final @NotNull String entry = Entries.capital(projectId, partnerId, Api.asAdmin(), false, amount);
        Entries.submit(projectId, entry);
        Entries.approve(projectId, entry);
        return entry;
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    @Test(description = "Finance adds a partner by name with no login", testName = "0d2408f5-43cb-4d08-8875-a205654692dc", priority = 1)
    public void financeAddsAPartnerByNameWithNoLogin() {
        final @NotNull String project = Projects.approved();
        add(project, "  Abdullah  ", "").then().statusCode(200);
        final @NotNull JsonPath partner = listed(project, Api.asAdmin()).setRootPath("find { it.name == 'Abdullah' }");
        assertEquals(partner.getString("userId"), "");
        assertEquals(partner.getString("userName"), "");
    }

    @Test(description = "A partner is linked to their login", testName = "fa9a7eb6-c884-4081-83c0-76f3b9c54374", priority = 2)
    public void aPartnerIsLinkedToTheirLogin() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        added(project, "Abdullah", person.id());
        final @NotNull JsonPath partner = listed(project, Api.asAdmin()).setRootPath("find { it.name == 'Abdullah' }");
        assertEquals(partner.getString("userId"), person.id());
        assertFalse(partner.getString("userName").isEmpty());
    }

    @Test(description = "A partners login is a row of its own that taking the login away removes", testName = "2c9fb354-7aef-4d8f-8542-56faebd0823a", priority = 3)
    public void aPartnersLoginIsARowOfItsOwnThatTakingTheLoginAwayRemoves() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull String partner = added(project, "Abdullah", person.id());
        edit(project, partner, "Abdullah", "").then().statusCode(200);
        assertEquals(listed(project, Api.asAdmin()).getString("find { it.id == '" + partner + "' }.userId"), "");
        added(project, "Second", person.id());
    }

    @Test(description = "The database refuses a partners login naming no user", testName = "73ea07c5-0739-4af4-aac6-a80c36fd7b1a", priority = 4, enabled = false)
    public void theDatabaseRefusesAPartnersLoginNamingNoUser() {
        throw new AssertionError("about the table's foreign key, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "A deleted partners login may be another partners in the project", testName = "2393bf2d-8796-4b49-b979-3624e0d62c8b", priority = 5)
    public void aDeletedPartnersLoginMayBeAnotherPartnersInTheProject() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull String partner = added(project, "Abdullah", person.id());
        given(Api.asAdmin()).delete(partners(project) + "/" + partner).then().statusCode(200);
        added(project, "Second", person.id());
        assertEquals(listed(project, Api.asAdmin()).getString("find { it.name == 'Second' }.userId"), person.id());
    }

    @Test(description = "Partners show in the order they were added", testName = "0a33d664-d32e-4b51-9fcf-1a3e9f879f41", priority = 6)
    public void partnersShowInTheOrderTheyWereAdded() {
        final @NotNull String project = Projects.approved();
        for (final String name : List.of("Zaid", "Abdullah", "Mona")) {
            add(project, name, "").then().statusCode(200);
        }
        assertEquals(listed(project, Api.asAdmin()).getList("name"), List.of("Zaid", "Abdullah", "Mona"));
    }

    @Test(description = "An edit renames a partner and sets their login", testName = "d3c72c1d-b766-4f7d-a8b7-bfad67f21379", priority = 7)
    public void anEditRenamesAPartnerAndSetsTheirLogin() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull String partner = added(project, "Abdullah", "");
        edit(project, partner, "Abdullah Saleh", person.id()).then().statusCode(200);
        final @NotNull JsonPath edited = listed(project, Api.asAdmin()).setRootPath("find { it.id == '" + partner + "' }");
        assertEquals(edited.getString("name"), "Abdullah Saleh");
        assertEquals(edited.getString("userId"), person.id());
    }

    @Test(description = "An edit can take a login away", testName = "3f72d895-86b5-4f73-b393-c8643513d9ee", priority = 8)
    public void anEditCanTakeALoginAway() {
        final @NotNull String project = Projects.approved();
        final @NotNull String partner = added(project, "Abdullah", People.in(project).id());
        edit(project, partner, "Abdullah", "").then().statusCode(200);
        assertEquals(listed(project, Api.asAdmin()).getString("find { it.id == '" + partner + "' }.userId"), "");
    }

    @Test(description = "A partner needs a name of at most100 characters", testName = "396cb81c-ce5b-475b-93b3-ca00936d2f17", priority = 9)
    public void aPartnerNeedsANameOfAtMost100Characters() {
        final @NotNull String project = Projects.approved();
        refusedAs(add(project, "p".repeat(101), ""), 400, "invalidName");
        refusedAs(add(project, "  ", ""), 400, "invalidName");
        final @NotNull String partner = added(project, "p".repeat(100), "");
        refusedAs(edit(project, partner, "", ""), 400, "invalidName");
    }

    @Test(description = "A login must be in the project", testName = "0f36a6c2-636e-4a47-9ead-badb9e2c9443", priority = 10)
    public void aLoginMustBeInTheProject() {
        final @NotNull String project = Projects.approved();
        refusedAs(add(project, "Abdullah", People.in(Projects.approved()).id()), 400, "unknownUser");
        refusedAs(edit(project, added(project, "Mona", ""), "Mona", People.in(Projects.approved()).id()), 400, "unknownUser");
    }

    @Test(description = "A login is one partner in a project", testName = "ac67ecfd-db43-42b5-91e2-8c7f78a2f3bb", priority = 11)
    public void aLoginIsOnePartnerInAProject() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        added(project, "Abdullah", person.id());
        refusedAs(add(project, "Second", person.id()), 400, "userTaken");
        added(project, "Mona", "");
        added(project, "Zaid", "");
        assertEquals(listed(project, Api.asAdmin()).getList("findAll { it.userId == '' }.name"), List.of("Mona", "Zaid"));
    }

    @Test(description = "The database holds a login to one partner in a project and many with no login", testName = "69a7ad05-e3c8-4456-961f-62d152caf4d8", priority = 12, enabled = false)
    public void theDatabaseHoldsALoginToOnePartnerInAProjectAndManyWithNoLogin() {
        throw new AssertionError("about the table's unique index, inside the server; what a request is answered is the 11th test");
    }

    @Test(description = "One login may be a partner in two projects", testName = "e5f258a5-a3b9-49b1-8762-f39695e98c5d", priority = 13)
    public void oneLoginMayBeAPartnerInTwoProjects() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull Person person = People.in(project);
        given(Api.asAdmin()).body(Map.of("username", person.username())).post("/projects/" + other + "/users/existing").then().statusCode(200);
        added(project, "Abdullah", person.id());
        added(other, "Abdullah", person.id());
    }

    @Test(description = "Without see all capital someone sees only the partners chosen for them and their own login gives no sight", testName = "91958915-8f83-4b23-90f1-5cbb5c800ce2", priority = 14)
    public void withoutSeeAllCapitalSomeoneSeesOnlyThePartnersChosenForThemAndTheirOwnLoginGivesNoSight() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        added(project, "Abdullah", person.id());
        final @NotNull String chosen = added(project, "Mona", "");
        assertEquals(listed(project, person.asked()).getList("name"), List.of());
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + person.id() + "/partners/" + chosen).then().statusCode(200);
        assertEquals(listed(project, person.asked()).getList("name"), List.of("Mona"));
    }

    @Test(description = "Someone who is no partner sees no partners", testName = "5d2a60c3-89da-4d69-9c48-fbb16ec83eca", priority = 15)
    public void someoneWhoIsNoPartnerSeesNoPartners() {
        final @NotNull String project = Projects.approved();
        added(project, "Abdullah", "");
        assertEquals(listed(project, People.in(project, "recordEntries").asked()).getList("name"), List.of());
    }

    @Test(description = "A partner is added by name to the projects one capital item and none is added while it is disabled", testName = "2ddb708f-a49a-4680-afda-ff25dfa43530", priority = 16)
    public void aPartnerIsAddedByNameToTheProjectsOneCapitalItemAndNoneIsAddedWhileItIsDisabled() {
        final @NotNull String project = Projects.approved();
        added(project, "Abdullah", "");
        assertEquals(capitalTables(project), List.of("Abdullah"));
        given(Api.asAdmin()).post("/projects/" + project + "/items/" + Structure.itemId(project, Structure.CAPITAL) + "/disable").then().statusCode(200);
        refusedAs(add(project, "Mona", ""), 400, "itemNotOffered");
        assertEquals(capitalTables(project), List.of("Abdullah"));
    }

    @Test(description = "Whoever holds see all capital sees every partner", testName = "f26ffba2-e371-401f-b3b0-d338c32a65b4", priority = 17)
    public void whoeverHoldsSeeAllCapitalSeesEveryPartner() {
        final @NotNull String project = Projects.approved();
        added(project, "Abdullah", "");
        added(project, "Mona", "");
        assertEquals(listed(project, People.in(project, "seeAllCapital").asked()).getList("name"), List.of("Abdullah", "Mona"));
    }

    @Test(description = "Partners declared with a new project become tables of its ras al mal in their order once approved", testName = "846810af-697b-4881-929b-c0ef7aed65db", priority = 18)
    public void partnersDeclaredWithANewProjectBecomeTablesOfItsRasAlMalInTheirOrderOnceApproved() {
        final @NotNull Map<String, Object> start = new HashMap<>(Map.of("name", "Partners " + UUID.randomUUID().toString().substring(0, 8), "startDate", LocalDate.now().toString(),
                "units", List.of("A1"), "partners", List.of("Zaid", "Abdullah")));
        final @NotNull String project = given(Api.asAdmin()).body(start).post("/projects").then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + project + "/approve").then().statusCode(200);
        assertEquals(capitalTables(project), List.of("Zaid", "Abdullah"));
        start.put("partners", List.of(" "));
        refusedAs(given(Api.asAdmin()).body(start).post("/projects"), 400, "invalidName");
        final @NotNull Map<String, Object> registration = Projects.registration("Registered " + UUID.randomUUID().toString().substring(0, 8), List.of("A1"));
        registration.put("partners", List.of("Mona"));
        final @NotNull String registered = Projects.register(registration).then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + registered + "/approve").then().statusCode(200);
        final @NotNull String token = People.signIn((String) registration.get("username"), People.PASSWORD).accessToken();
        assertEquals(given(Api.as(token)).get(partners(registered)).then().statusCode(200).extract().path("name"), List.of("Mona"));
    }

    @Test(description = "A partner is a table of the capital item last among its tables", testName = "d3d0fba0-7161-4352-b85b-902d46d662ca", priority = 19)
    public void aPartnerIsATableOfTheCapitalItemLastAmongItsTables() {
        final @NotNull String project = Projects.approved();
        final @NotNull String first = added(project, "Abdullah", "");
        final @NotNull String second = added(project, "Mona", "");
        added(project, "Zaid", "");
        assertEquals(capitalTables(project), List.of("Abdullah", "Mona", "Zaid"));
        final @NotNull String capital = Structure.itemId(project, Structure.CAPITAL);
        final @NotNull List<String> order = List.of(Structure.tablesOf(project, capital).getString("find { it.name == 'Zaid' }.id"), second, first);
        given(Api.asAdmin()).body(Map.of("ids", order)).post("/projects/" + project + "/items/" + capital + "/tables/reorder").then().statusCode(200);
        assertEquals(listed(project, Api.asAdmin()).getList("name"), List.of("Zaid", "Mona", "Abdullah"));
    }

    @Test(description = "A partner at zero capital is deleted softly leaving the lists while their movements and history stay and their account is free", testName = "1420b3da-ba04-44ad-b54b-beb3fdb9d85a", priority = 20)
    public void aPartnerAtZeroCapitalIsDeletedSoftlyLeavingTheListsWhileTheirMovementsAndHistoryStayAndTheirAccountIsFree() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull String partner = added(project, "Abdullah", person.id());
        final @NotNull String in = movedIn(project, partner, 5000);
        final @NotNull String out = Entries.capital(project, partner, Api.asAdmin(), true, 5000);
        Entries.submit(project, out);
        Entries.approve(project, out);
        given(Api.asAdmin()).delete(partners(project) + "/" + partner).then().statusCode(200);
        assertEquals(listed(project, Api.asAdmin()).getList("name"), List.of());
        assertEquals(capitalTables(project), List.of());
        assertFalse(given(Api.asAdmin()).get("/projects/" + project + "/capital").then().statusCode(200).extract().jsonPath().getList("partners.id").contains(partner));
        assertTrue(given(Api.asAdmin()).get("/projects/" + project + "/entries").then().statusCode(200).extract().jsonPath().getList("id").containsAll(List.of(in, out)));
        added(project, "Second", person.id());
        given(Api.asAdmin()).delete(partners(project) + "/" + partner).then().statusCode(404);
    }

    @Test(description = "A partner with capital or a movement waiting is not deleted and one never used is", testName = "0f8c9877-f83e-4f97-97cb-fd3ba51b9b2b", priority = 21)
    public void aPartnerWithCapitalOrAMovementWaitingIsNotDeletedAndOneNeverUsedIs() {
        final @NotNull String project = Projects.approved();
        final @NotNull String withCapital = added(project, "Abdullah", "");
        movedIn(project, withCapital, 5000);
        final @NotNull String waiting = added(project, "Mona", "");
        Entries.submit(project, Entries.capital(project, waiting, Api.asAdmin(), false, 3000));
        final @NotNull String unused = added(project, "Zaid", "");
        refusedAs(given(Api.asAdmin()).delete(partners(project) + "/" + withCapital), 409, "partnerNotDeletable");
        refusedAs(given(Api.asAdmin()).delete(partners(project) + "/" + waiting), 409, "partnerNotDeletable");
        given(Api.asAdmin()).delete(partners(project) + "/" + unused).then().statusCode(200);
        assertEquals(listed(project, Api.asAdmin()).getList("name"), List.of("Abdullah", "Mona"));
    }

    @Test(description = "A deleted partner cannot be chosen for someone", testName = "95394bee-cf4e-43f3-a919-a85ea601f5a9", priority = 22)
    public void aDeletedPartnerCannotBeChosenForSomeone() {
        final @NotNull String project = Projects.approved();
        final @NotNull String partner = added(project, "Abdullah", "");
        given(Api.asAdmin()).delete(partners(project) + "/" + partner).then().statusCode(200);
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + People.in(project).id() + "/partners/" + partner).then().statusCode(404);
        edit(project, partner, "Back", "").then().statusCode(404);
    }

    @Test(description = "Each partner action and approved capital movement is a line newest first", testName = "5360db02-4d96-4bd3-9903-19e917d0b975", priority = 23)
    public void eachPartnerActionAndApprovedCapitalMovementIsALineNewestFirst() {
        final @NotNull String project = Projects.approved();
        final @NotNull String partner = added(project, "Abdullah", "");
        edit(project, partner, "Abdullah Saleh", "").then().statusCode(200);
        movedIn(project, partner, 5000);
        final @NotNull JsonPath lines = history(project, Api.asAdmin(), "");
        assertEquals(lines.getList("kind"), List.of("capitalIn", "partnerChanged", "partnerCreated"));
        assertEquals(lines.getString("[1].nameWas"), "Abdullah");
        assertEquals(lines.getDouble("[0].amount"), 5000.0);
    }

    @Test(description = "One partners lines and what i can be kept alone", testName = "d78837c0-9480-48d1-8530-fd7893a90c31", priority = 24)
    public void onePartnersLinesAndWhatICanBeKeptAlone() {
        final @NotNull String project = Projects.approved();
        final @NotNull String abdullah = added(project, "Abdullah", "");
        added(project, "Mona", "");
        assertEquals(history(project, Api.asAdmin(), "?partner=" + abdullah).getList("partner"), List.of("Abdullah"));
        final @NotNull Person manager = People.in(project, "manageSettings", "seeAllCapital");
        given(manager.asked()).body(Map.of("name", "Zaid")).post(partners(project)).then().statusCode(200);
        assertEquals(history(project, manager.asked(), "?mine=true").getList("partner"), List.of("Zaid"));
    }

    @Test(description = "Someone with chosen partners sees only their lines and someone with neither sees none", testName = "9ed30605-f72a-4a92-905c-e23092700506", priority = 25)
    public void someoneWithChosenPartnersSeesOnlyTheirLinesAndSomeoneWithNeitherSeesNone() {
        final @NotNull String project = Projects.approved();
        final @NotNull String chosen = added(project, "Abdullah", "");
        added(project, "Mona", "");
        final @NotNull Person person = People.in(project);
        assertEquals(history(project, person.asked(), "").getList("$"), List.of());
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + person.id() + "/partners/" + chosen).then().statusCode(200);
        assertEquals(history(project, person.asked(), "").getList("partner").stream().distinct().toList(), List.of("Abdullah"));
    }

    @Test(description = "A partner added with a login is one line and taking the login away is a line with none", testName = "3df5bc67-0462-4469-a2a2-cd843fc511f1", priority = 26)
    public void aPartnerAddedWithALoginIsOneLineAndTakingTheLoginAwayIsALineWithNone() {
        final @NotNull String project = Projects.approved();
        final @NotNull String partner = added(project, "Abdullah", People.in(project).id());
        assertEquals(history(project, Api.asAdmin(), "").getList("kind"), List.of("partnerCreated"));
        edit(project, partner, "Abdullah", "").then().statusCode(200);
        final @NotNull JsonPath lines = history(project, Api.asAdmin(), "");
        assertEquals(lines.getList("kind"), List.of("partnerAccount", "partnerCreated"));
        assertEquals(lines.getString("[0].login"), "");
    }

    @Test(description = "Only finance changes partners", testName = "aaa4e7a2-1a02-4073-a4b5-01321878e0d0", priority = 27)
    public void onlyFinanceChangesPartners() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person reader = People.in(project, "seeAllCapital", "recordEntries");
        final @NotNull String partner = added(project, "Abdullah", "");
        given(reader.asked()).body(Map.of("name", "Mona")).post(partners(project)).then().statusCode(403);
        given(reader.asked()).body(Map.of("name", "Taken")).put(partners(project) + "/" + partner).then().statusCode(403);
        given(reader.asked()).delete(partners(project) + "/" + partner).then().statusCode(403);
        given(People.in(project, "manageSettings").asked()).body(Map.of("name", "Mona")).post(partners(project)).then().statusCode(200);
    }

    @Test(description = "One projects partners are never anothers", testName = "87b21696-5dd4-4c0a-8ab3-0ae3253f1511", priority = 28)
    public void oneProjectsPartnersAreNeverAnothers() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull String theirs = added(other, "Abdullah", "");
        assertEquals(listed(project, Api.asAdmin()).getList("name"), List.of());
        edit(project, theirs, "Taken", "").then().statusCode(404);
        given(Api.asAdmin()).delete(partners(project) + "/" + theirs).then().statusCode(404);
        assertEquals(listed(other, Api.asAdmin()).getList("name"), List.of("Abdullah"));
    }

    @Test(description = "A copied project starts with no partners", testName = "f95d1a4c-8d8b-49d0-8cb2-2c729077668f", priority = 29)
    public void aCopiedProjectStartsWithNoPartners() {
        final @NotNull String source = Projects.approved();
        added(source, "Abdullah", "");
        final @NotNull String copy = given(Api.asAdmin()).body(Map.of("name", "Copy " + UUID.randomUUID().toString().substring(0, 8), "startDate", LocalDate.now().toString(),
                "units", List.of("A1"), "partners", List.of(), "structureSourceId", source)).post("/projects").then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + copy + "/approve").then().statusCode(200);
        assertEquals(listed(copy, Api.asAdmin()).getList("name"), List.of());
        assertEquals(capitalTables(copy), List.of());
    }

    @Test(description = "A finished project refuses every change and stays readable", testName = "a7bdff70-1d1c-4c3e-a4c4-799aa65b7f8b", priority = 30)
    public void aFinishedProjectRefusesEveryChangeAndStaysReadable() {
        final @NotNull String project = Projects.approved();
        final @NotNull String partner = added(project, "Abdullah", "");
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);
        refusedAs(add(project, "Mona", ""), 409, "projectFinished");
        refusedAs(edit(project, partner, "Taken", ""), 409, "projectFinished");
        refusedAs(given(Api.asAdmin()).delete(partners(project) + "/" + partner), 409, "projectFinished");
        assertEquals(listed(project, Api.asAdmin()).getList("name"), List.of("Abdullah"));
    }

    @Test(description = "Row_199_someone elses draft holds the partner and says whose while the callers own drafts are deleted with them", testName = "7a5fc01c-fd29-463e-b6e3-ae4c2b841336", priority = 31)
    public void row199SomeoneElsesDraftHoldsThePartnerAndSaysWhoseWhileTheCallersOwnDraftsAreDeletedWithThem() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person someone = People.in(project, "recordEntries", "moveMoney", "chargeAnyAccount", "seeAllCapital");
        final @NotNull String held = added(project, "Abdullah", "");
        Entries.capital(project, held, someone.asked(), false, 1000);
        final @NotNull JsonPath seen = listed(project, Api.asAdmin());
        assertFalse(seen.getBoolean("find { it.id == '" + held + "' }.mayDelete"));
        assertEquals(seen.getList("find { it.id == '" + held + "' }.othersDrafts").size(), 1);
        refusedAs(given(Api.asAdmin()).delete(partners(project) + "/" + held), 409, "partnerNotDeletable");
        final @NotNull String mine = added(project, "Mona", "");
        final @NotNull String draft = Entries.capital(project, mine, Api.asAdmin(), false, 1000);
        given(Api.asAdmin()).delete(partners(project) + "/" + mine).then().statusCode(200);
        assertFalse(given(Api.asAdmin()).get("/projects/" + project + "/entries").then().statusCode(200).extract().jsonPath().getList("id").contains(draft));
    }

    @Test(description = "Row_202_a delete that takes the callers own drafts needs delete entries too while one with no draft of theirs does not", testName = "67a4f7f7-8485-483e-9718-953c7366cee7", priority = 32)
    public void row202ADeleteThatTakesTheCallersOwnDraftsNeedsDeleteEntriesTooWhileOneWithNoDraftOfTheirsDoesNot() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person manager = People.in(project, "manageSettings", "recordEntries", "moveMoney", "chargeAnyAccount", "seeAllCapital");
        final @NotNull String withDraft = added(project, "Abdullah", "");
        Entries.capital(project, withDraft, manager.asked(), false, 1000);
        refusedAs(given(manager.asked()).delete(partners(project) + "/" + withDraft), 409, "partnerNotDeletable");
        given(manager.asked()).delete(partners(project) + "/" + added(project, "Mona", "")).then().statusCode(200);
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + manager.id() + "/permissions/deleteEntries").then().statusCode(200);
        given(manager.asked()).delete(partners(project) + "/" + withDraft).then().statusCode(200);
    }

    @Test(description = "Row_203_whose draft names a partner is told only to whoever may delete partners", testName = "21cc97d7-b0c2-46cc-b22f-248f55aab997", priority = 33)
    public void row203WhoseDraftNamesAPartnerIsToldOnlyToWhoeverMayDeletePartners() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person someone = People.in(project, "recordEntries", "moveMoney", "chargeAnyAccount", "seeAllCapital");
        final @NotNull String partner = added(project, "Abdullah", "");
        Entries.capital(project, partner, someone.asked(), false, 1000);
        assertEquals(listed(project, Api.asAdmin()).getList("find { it.id == '" + partner + "' }.othersDrafts").size(), 1);
        assertEquals(listed(project, People.in(project, "seeAllCapital").asked()).getList("find { it.id == '" + partner + "' }.othersDrafts"), List.of());
    }
}
