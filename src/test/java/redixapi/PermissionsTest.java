// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Entries;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;
import redix.support.Projects.Registered;
import redix.support.Routes;
import redix.support.Routes.Route;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class PermissionsTest {
    /** Every permission someone may hold in a project, in the order the API lists them; SeeAllCapital and SeeChosenCapital exclude each other. */
    private static final @NotNull List<String> IN_A_PROJECT = List.of("manageUsers", "readProject", "seeAllCapital", "seeChosenCapital", "manageProject",
            "manageStructure", "manageSettings", "addSupplier", "chargeAnyAccount", "recordEntries", "manageAnyEntry", "deleteEntries", "removeApprovedEntries",
            "attachFiles", "approveEntries", "moveMoney", "seeReports", "generateReports", "seeProjectHistory", "manageProjectFiles");

    /** The routes that change something open to anyone: signing in, renewing, the forgotten password, registering a project. */
    private static final @NotNull Set<String> OPEN_CHANGES = Set.of("POST /auth/sign-in", "POST /auth/refresh", "POST /auth/forgot-password",
            "POST /auth/reset-with-code", "POST /projects/register");

    private static @NotNull List<String> permissionsOf(final @NotNull String token, final @NotNull String projectId) {
        return given(Api.as(token)).get("/projects").then().statusCode(200).extract().path("find { it.id == '" + projectId + "' }.permissions");
    }

    private static @NotNull Response tick(final @NotNull String projectId, final @NotNull String userId, final @NotNull String permission) {
        return given(Api.asAdmin()).post("/projects/" + projectId + "/users/" + userId + "/permissions/" + permission);
    }

    private static @NotNull Response choose(final @NotNull String projectId, final @NotNull String userId, final @NotNull String partnerId) {
        return given(Api.asAdmin()).post("/projects/" + projectId + "/users/" + userId + "/partners/" + partnerId);
    }

    private static @NotNull String partnerIn(final @NotNull String projectId) {
        given(Api.asAdmin()).body(Map.of("name", "Partner " + System.nanoTime())).post("/projects/" + projectId + "/partners").then().statusCode(200);
        return given(Api.asAdmin()).get("/projects/" + projectId + "/partners").then().statusCode(200).extract().path("[-1].id");
    }

    private static @NotNull List<String> chosenOf(final @NotNull String projectId, final @NotNull String userId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/users").then().statusCode(200).extract()
                .path("find { it.id == '" + userId + "' }.projects[0].partnerIds");
    }

    /** What the gate answered: "forbidden" for a caller lacking the route's permission, otherwise the route's own code or none. */
    private static @NotNull String gateOf(final @NotNull Response answer) {
        return answer.statusCode() == 403 && "forbidden".equals(answer.jsonPath().getString("code")) ? "forbidden" : "through";
    }

    @Test(description = "The admin holds approving projects from the first start", testName = "bedcedf5-d112-4304-95ae-2ea3225d4e38", priority = 1)
    public void theAdminHoldsApprovingProjectsFromTheFirstStart() {
        assertTrue(People.admin().permissions().contains("approveProjects"));
        given(Api.asAdmin()).get("/projects/registered").then().statusCode(200);
    }

    @Test(description = "A new row gives a permission at the next request", testName = "a9020768-ebb1-45b4-836b-f70ef2823dbd", priority = 2)
    public void aNewRowGivesAPermissionAtTheNextRequest() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull String report = "/projects/" + project + "/reports/items/" + UUID.randomUUID();
        assertEquals(gateOf(given(person.asked()).get(report)), "forbidden");
        tick(project, person.id(), "seeReports").then().statusCode(200);
        assertEquals(gateOf(given(person.asked()).get(report)), "through");
    }

    @Test(description = "Every route of a project he is inactive in refuses him as for a project he is not in", testName = "72235917-baf4-4638-a96c-478c693f8977", priority = 3)
    public void everyRouteOfAProjectHeIsInactiveInRefusesHimAsForAProjectHeIsNotIn() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person inactive = People.in(project, IN_A_PROJECT.stream().filter(name -> !List.of("seeChosenCapital", "readProject").contains(name)).toArray(String[]::new));
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + inactive.id() + "/disable").then().statusCode(200);
        for (final Route route : Routes.every().stream().filter(route -> route.path().contains("{projectId}") && !route.permission().isEmpty()).toList()) {
            assertEquals(gateOf(given(inactive.asked()).body("{}").request(route.method(), route.in(project))), "forbidden", route.toString());
        }
    }

    @Test(description = "A permission held everywhere reaches every project and an unknown one is not found", testName = "2769b8ae-de76-4d8b-95bc-5421ede728a9", priority = 4)
    public void aPermissionHeldEverywhereReachesEveryProjectAndAnUnknownOneIsNotFound() {
        given(Api.asAdmin()).post("/projects/" + Projects.pending() + "/approve").then().statusCode(200);
        final @NotNull Registered registered = Projects.registered();
        given(Api.asAdmin()).post("/projects/" + registered.id() + "/approve").then().statusCode(409).body("code", equalTo("notPending"));
        given(Api.asAdmin()).post("/projects/" + UUID.randomUUID() + "/approve").then().statusCode(404);
    }

    @Test(description = "The database refuses a permission the code does not know and the admins in a project", testName = "d5136ff9-ee42-4846-88ee-c7c5769d8261", priority = 5)
    public void theDatabaseRefusesAPermissionTheCodeDoesNotKnowAndTheAdminsInAProject() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        tick(project, person.id(), "flyToTheMoon").then().statusCode(400).body("code", equalTo("invalidPermission"));
        tick(project, person.id(), "approveProjects").then().statusCode(400).body("code", equalTo("invalidPermission"));
        assertEquals(permissionsOf(person.token(), project), List.of("readProject"));
    }

    @Test(description = "See all and chosen capital exclude each other and a registrant sees all", testName = "9cc39cf2-ab36-4428-b918-33989010bc53", priority = 6)
    public void seeAllAndChosenCapitalExcludeEachOtherAndARegistrantSeesAll() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        tick(project, person.id(), "seeAllCapital").then().statusCode(200);
        tick(project, person.id(), "seeChosenCapital").then().statusCode(200);
        assertEquals(permissionsOf(person.token(), project), List.of("readProject", "seeChosenCapital"));
        tick(project, person.id(), "seeAllCapital").then().statusCode(200);
        assertEquals(permissionsOf(person.token(), project), List.of("readProject", "seeAllCapital"));
        final @NotNull Registered registered = Projects.registered();
        final @NotNull List<String> held = permissionsOf(registered.token(), registered.id());
        assertTrue(held.contains("seeAllCapital") && !held.contains("seeChosenCapital"), "the registrant holds " + held);
    }

    @Test(description = "Choosing a partner gives chosen capital and takes see all and taking chosen capital forgets the partners", testName = "a8c2f4d4-13a6-4254-90ef-5ead762c4214", priority = 7)
    public void choosingAPartnerGivesChosenCapitalAndTakesSeeAllAndTakingChosenCapitalForgetsThePartners() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project, "seeAllCapital");
        final @NotNull String partner = partnerIn(project);
        choose(project, person.id(), partner).then().statusCode(200);
        assertEquals(permissionsOf(person.token(), project), List.of("readProject", "seeChosenCapital"));
        assertEquals(chosenOf(project, person.id()), List.of(partner));
        given(Api.asAdmin()).delete("/projects/" + project + "/users/" + person.id() + "/permissions/seeChosenCapital").then().statusCode(200);
        assertEquals(chosenOf(project, person.id()), List.of());
        assertEquals(permissionsOf(person.token(), project), List.of("readProject"));
    }

    @Test(description = "A person is created with chosen partners but not with both capital ticks nor another projects partner", testName = "d447e482-7dea-4ee7-8be8-2f17c7b97df1", priority = 8)
    public void aPersonIsCreatedWithChosenPartnersButNotWithBothCapitalTicksNorAnotherProjectsPartner() {
        final @NotNull String project = Projects.approved();
        final @NotNull String partner = partnerIn(project);
        final @NotNull String created = given(Api.asAdmin()).body(People.request("seeChosenCapital").partnerIds(List.of(partner)).build())
                .post("/projects/" + project + "/users").then().statusCode(201).extract().path("id");
        assertEquals(chosenOf(project, created), List.of(partner));
        given(Api.asAdmin()).body(People.request("seeAllCapital", "seeChosenCapital").build()).post("/projects/" + project + "/users").then().statusCode(400)
                .body("code", equalTo("invalidPermission"));
        given(Api.asAdmin()).body(People.request("seeChosenCapital").partnerIds(List.of(partnerIn(Projects.approved()))).build()).post("/projects/" + project + "/users")
                .then().statusCode(400).body("code", equalTo("unknownPartner"));
    }

    @Test(description = "Choosing a partner of another project or for someone outside is not found", testName = "2c9880b9-5ee2-41e7-8e86-b0716d917b16", priority = 9)
    public void choosingAPartnerOfAnotherProjectOrForSomeoneOutsideIsNotFound() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        choose(project, person.id(), partnerIn(Projects.approved())).then().statusCode(404);
        choose(project, People.in(Projects.approved()).id(), partnerIn(project)).then().statusCode(404);
        assertEquals(permissionsOf(person.token(), project), List.of("readProject"));
    }

    @Test(description = "Whoever registers a project holds every permission of a project in it seeing all capital rather than chosen partners", testName = "dfb0eda0-10a6-4492-8d36-7b6c19418b8f", priority = 10)
    public void whoeverRegistersAProjectHoldsEveryPermissionOfAProjectInItSeeingAllCapitalRatherThanChosenPartners() {
        final @NotNull Registered registered = Projects.registered();
        assertEquals(permissionsOf(registered.token(), registered.id()),
                IN_A_PROJECT.stream().filter(name -> !List.of("seeChosenCapital", "removeApprovedEntries").contains(name)).toList());
    }

    @Test(description = "Every changing route names a policy", testName = "1b44f032-8f23-424b-a0f4-7912edb7662a", priority = 11)
    public void everyChangingRouteNamesAPolicy() {
        final @NotNull List<String> unguarded = new ArrayList<>();
        for (final Route route : Routes.every()) {
            if (!route.method().equals("GET") && route.open() && !OPEN_CHANGES.contains(route.toString())) {
                unguarded.add(route.toString());
            }
        }
        assertEquals(unguarded, List.of(), "a route changes something with no gate");
    }

    @Test(description = "Every route of a project names a permission", testName = "bcfb5143-3ed2-48fa-b3ce-785e2691b75a", priority = 12)
    public void everyRouteOfAProjectNamesAPermission() {
        final @NotNull List<String> unnamed = Routes.every().stream().filter(route -> route.path().startsWith("/projects/{projectId}") && route.permission().isEmpty())
                .map(Route::toString).toList();
        assertEquals(unnamed, List.of(), "a route of a project names no permission");
    }

    @Test(description = "Every route refuses whoever lacks its permission and opens once it is ticked", testName = "046a14c5-ade5-412b-931a-ddeb8106664a", priority = 13)
    public void everyRouteRefusesWhoeverLacksItsPermissionAndOpensOnceItIsTicked() {
        final @NotNull List<Route> gated = Routes.every().stream().filter(route -> route.path().contains("{projectId}") && !route.permission().isEmpty()
                && !route.permission().equals("approveProjects")).toList();
        assertTrue(gated.size() > 100, gated.size() + " routes need a permission of a project");
        for (final String permission : gated.stream().map(Route::permission).distinct().toList()) {
            final @NotNull String project = Projects.approved();
            final @NotNull Person person = permission.equals("readProject") ? People.in(Projects.approved()) : People.in(project);
            final @NotNull List<Route> theirs = gated.stream().filter(route -> route.permission().equals(permission)).toList();
            for (final Route route : theirs) {
                assertEquals(gateOf(given(person.asked()).body("{}").request(route.method(), route.in(project))), "forbidden", route + " without " + permission);
            }
            if (permission.equals("readProject")) {
                given(Api.asAdmin()).body(Map.of("username", person.username())).post("/projects/" + project + "/users/existing").then().statusCode(200);
            } else {
                tick(project, person.id(), permission).then().statusCode(200);
            }
            for (final Route route : theirs) {
                assertEquals(gateOf(given(person.asked()).body("{}").request(route.method(), route.in(project))), "through", route + " with " + permission);
            }
        }
    }

    @Test(description = "Another project is forbidden", testName = "792f125c-fddf-4dcf-b421-9890d799a9c5", priority = 14)
    public void anotherProjectIsForbidden() {
        final @NotNull Person person = People.in(Projects.approved(), "manageSettings");
        final @NotNull String other = Projects.approved();
        given(person.asked()).get("/projects/" + other + "/accounts").then().statusCode(403);
        given(person.asked()).body(Map.of("name", "Bank", "type", "bank")).post("/projects/" + other + "/accounts").then().statusCode(403);
    }

    @Test(description = "Permissions follow what is ticked in each project", testName = "d4b2935a-f5fe-41eb-a3d2-69535930fc73", priority = 15)
    public void permissionsFollowWhatIsTickedInEachProject() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull Person person = People.in(project, "manageSettings");
        given(Api.asAdmin()).body(Map.of("username", person.username())).post("/projects/" + other + "/users/existing").then().statusCode(200);
        given(person.asked()).body(Map.of("name", "Bank", "type", "bank")).post("/projects/" + project + "/accounts").then().statusCode(200);
        given(person.asked()).body(Map.of("name", "Bank", "type", "bank")).post("/projects/" + other + "/accounts").then().statusCode(403);
    }

    @Test(description = "Field and reader cannot change a project", testName = "63a38ce0-92b2-4d5d-a159-8e07e3c24e0f", priority = 16)
    public void fieldAndReaderCannotChangeAProject() {
        final @NotNull String project = Projects.approved();
        for (final Person person : List.of(People.in(project, "recordEntries", "attachFiles"), People.in(project))) {
            given(person.asked()).body(Map.of("name", "Renamed")).put("/projects/" + project).then().statusCode(403);
            given(person.asked()).body(Map.of("name", "B2")).post("/projects/" + project + "/units").then().statusCode(403);
            given(person.asked()).body(Map.of("name", "Bank", "type", "bank")).post("/projects/" + project + "/accounts").then().statusCode(403);
            given(person.asked()).body(Map.of("name", "Works")).post("/projects/" + project + "/categories").then().statusCode(403);
            given(person.asked()).post("/projects/" + project + "/close").then().statusCode(403);
        }
    }

    @Test(description = "A sessionless request is unauthorized", testName = "3836ca2a-3168-4853-aeb0-eff73826bf03", priority = 17)
    public void aSessionlessRequestIsUnauthorized() {
        final @NotNull String project = Projects.approved();
        for (final Route route : Routes.every().stream().filter(route -> !route.open()).toList()) {
            assertEquals(given(Api.anonymous()).body("{}").request(route.method(), route.in(project)).statusCode(), 401, route.toString());
        }
    }

    @Test(description = "Whoever starts a project manages it", testName = "f08d6974-6a3b-4921-b641-dca8df9cb43d", priority = 18)
    public void whoeverStartsAProjectManagesIt() {
        final @NotNull Registered registered = Projects.registered();
        assertTrue(permissionsOf(registered.token(), registered.id()).containsAll(List.of("manageProject", "manageUsers", "manageStructure", "manageSettings")));
        given(registered.asked()).body(Map.of("name", "B2")).post("/projects/" + registered.id() + "/units").then().statusCode(200);
        assertTrue(permissionsOf(People.admin().accessToken(), Projects.approved()).contains("manageProject"), "the admin manages the project they start");
    }

    @Test(description = "Whoever registers a project holds every permission of a project in it", testName = "0353b780-89e6-4517-9d99-bcd2a23e10c3", priority = 19)
    public void whoeverRegistersAProjectHoldsEveryPermissionOfAProjectInIt() {
        final @NotNull Registered registered = Projects.registered();
        assertEquals(permissionsOf(registered.token(), registered.id()).size(), 18);
    }

    @Test(description = "Deleting is its own permission", testName = "2e546995-7570-4aa6-98d9-7d99df618d50", priority = 20)
    public void deletingIsItsOwnPermission() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project, "recordEntries", "chargeAnyAccount");
        final @NotNull String entry = Entries.draft(project, person.asked());
        given(person.asked()).delete("/projects/" + project + "/entries/" + entry).then().statusCode(403);
        tick(project, person.id(), "deleteEntries").then().statusCode(200);
        given(person.asked()).delete("/projects/" + project + "/entries/" + entry).then().statusCode(200);
        given(person.asked()).get("/projects/" + project + "/entries").then().statusCode(200).body("id", not(hasItem(entry)));
    }

    @Test(description = "Report permissions_whoever reads a project sees its reports and whoever moved money or saw all capital generates them", testName = "1db9de15-b1c9-4275-9842-b1b5586e2a73", priority = 21, enabled = false)
    public void reportPermissionsWhoeverReadsAProjectSeesItsReportsAndWhoeverMovedMoneyOrSawAllCapitalGeneratesThem() {
        throw new AssertionError("about the migration that ticked them on the rows it found, inside the server; Go's internal/db tests prove it");
    }
}
