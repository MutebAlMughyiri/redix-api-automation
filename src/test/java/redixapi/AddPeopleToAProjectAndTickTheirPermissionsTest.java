// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.model.users.CreateUserRequest;
import redix.support.Api;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class AddPeopleToAProjectAndTickTheirPermissionsTest {
    private static @NotNull Response create(final @NotNull String projectId, final @NotNull CreateUserRequest request) {
        return given(Api.asAdmin()).body(request).post("/projects/" + projectId + "/users");
    }

    private static @NotNull Response tick(final @NotNull String projectId, final @NotNull String userId, final @NotNull String permission) {
        return given(Api.asAdmin()).post("/projects/" + projectId + "/users/" + userId + "/permissions/" + permission);
    }

    private static @NotNull Response untick(final @NotNull String projectId, final @NotNull String userId, final @NotNull String permission) {
        return given(Api.asAdmin()).delete("/projects/" + projectId + "/users/" + userId + "/permissions/" + permission);
    }

    private static void addExisting(final @NotNull String projectId, final @NotNull Person person) {
        given(Api.asAdmin()).body(Map.of("username", person.username())).post("/projects/" + projectId + "/users/existing").then().statusCode(200);
    }

    private static @NotNull List<String> permissionsOf(final @NotNull Person person, final @NotNull String projectId) {
        return given(person.asked()).get("/projects").then().statusCode(200).extract().path("find { it.id == '" + projectId + "' }.permissions");
    }

    private static @NotNull JsonPath user(final @NotNull String projectId, final @NotNull String userId) {
        return new JsonPath(given(Api.asAdmin()).get("/projects/" + projectId + "/users").then().statusCode(200).extract().asString())
                .setRootPath("find { it.id == '" + userId + "' }");
    }

    private static @NotNull String partnerIn(final @NotNull String projectId) {
        given(Api.asAdmin()).body(Map.of("name", "Partner " + System.nanoTime())).post("/projects/" + projectId + "/partners").then().statusCode(200);
        return given(Api.asAdmin()).get("/projects/" + projectId + "/partners").then().statusCode(200).extract().path("[-1].id");
    }

    private static @NotNull List<Integer> atOnce(final @NotNull Supplier<Response> request) {
        return IntStream.range(0, 8).parallel().mapToObj(i -> request.get().statusCode()).distinct().toList();
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    @Test(description = "A user sees only their projects", testName = "dc6df5d5-9718-47d5-8b58-81cd9e9730d2", priority = 1)
    public void aUserSeesOnlyTheirProjects() {
        final @NotNull String project = Projects.approved();
        Projects.approved();
        final @NotNull Person person = People.in(project);
        given(person.asked()).get("/projects").then().statusCode(200).body("id", equalTo(List.of(project)));
    }

    @Test(description = "Adding a person keeps the email the manager typed", testName = "1a41cc51-a3eb-4d6e-a61b-8e7e8c37e284", priority = 2)
    public void addingAPersonKeepsTheEmailTheManagerTyped() {
        final @NotNull CreateUserRequest request = People.request().build();
        create(Projects.approved(), People.request().email("  " + request.getEmail() + "  ").build()).then().statusCode(201).body("email", equalTo(request.getEmail()));
    }

    @Test(description = "Adding a person needs a well formed email and creates nothing without", testName = "eb880a29-4a60-4711-ba5e-b375f84d5a68", priority = 3)
    public void addingAPersonNeedsAWellFormedEmailAndCreatesNothingWithout() {
        final @NotNull String project = Projects.approved();
        for (final String email : List.of("", "no-at-sign", "two@@redix.test", "spaces in@redix.test")) {
            final @NotNull CreateUserRequest request = People.request().email(email).build();
            refusedAs(create(project, request), 400, "emailInvalid");
            given(Api.anonymous()).body(Map.of("username", request.getUsername(), "password", People.TEMPORARY)).post("/auth/sign-in").then().statusCode(401);
        }
    }

    @Test(description = "An email another account holds is refused whatever its case", testName = "070d0873-d82f-401e-bd11-fe40d0386ef7", priority = 4)
    public void anEmailAnotherAccountHoldsIsRefusedWhateverItsCase() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        refusedAs(create(project, People.request().email(person.email().toUpperCase()).build()), 409, "emailTaken");
        refusedAs(create(Projects.approved(), People.request().email(person.email()).build()), 409, "emailTaken");
    }

    @Test(description = "An empty form names the username first and the email only after the other fields_as the form orders them", testName = "dae88848-3507-4539-baf4-ca9a7823ffbd", priority = 5)
    public void anEmptyFormNamesTheUsernameFirstAndTheEmailOnlyAfterTheOtherFieldsAsTheFormOrdersThem() {
        final @NotNull String project = Projects.approved();
        refusedAs(create(project, People.request().displayName("").username("").mobile("").email("").temporaryPassword("").build()), 400, "invalidUsername");
        refusedAs(create(project, People.request().mobile("05-x").email("").build()), 400, "invalidMobile");
        refusedAs(create(project, People.request().temporaryPassword("short").email("").build()), 400, "weakPassword");
    }

    @Test(description = "Permissions follow what is ticked in each project", testName = "70f857e4-72dd-4128-af5d-4c14e0711dd4", priority = 6)
    public void permissionsFollowWhatIsTickedInEachProject() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull Person person = People.in(project, "recordEntries");
        addExisting(other, person);
        tick(other, person.id(), "approveEntries").then().statusCode(200);
        assertTrue(permissionsOf(person, project).contains("recordEntries") && !permissionsOf(person, project).contains("approveEntries"));
        assertTrue(permissionsOf(person, other).contains("approveEntries") && !permissionsOf(person, other).contains("recordEntries"));
    }

    @Test(description = "A manager sees only their projects people and none of their other projects", testName = "d42b2bff-ffc9-416b-a6b6-ec75204dfeb6", priority = 7)
    public void aManagerSeesOnlyTheirProjectsPeopleAndNoneOfTheirOtherProjects() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull Person elsewhere = People.in(other);
        addExisting(other, person);
        final @NotNull Person manager = People.in(project, "manageUsers");
        final @NotNull JsonPath people = given(manager.asked()).get("/projects/" + project + "/users").then().statusCode(200).extract().jsonPath();
        assertFalse(people.getList("id").contains(elsewhere.id()));
        assertEquals(people.getList("find { it.id == '" + person.id() + "' }.projects.projectId"), List.of(project));
        given(manager.asked()).get("/projects/" + other + "/users").then().statusCode(403);
    }

    @Test(description = "A manager creates a login holding what was ticked and seeing the project who must change the password", testName = "af8d3ccf-63c9-4b23-a6d1-cb64a2847fa5", priority = 8)
    public void aManagerCreatesALoginHoldingWhatWasTickedAndSeeingTheProjectWhoMustChangeThePassword() {
        final @NotNull String project = Projects.approved();
        create(project, People.request("recordEntries", "seeReports").build()).then().statusCode(201).body("mustChangePassword", equalTo(true))
                .body("projects[0].permissions", equalTo(List.of("readProject", "recordEntries", "seeReports")));
    }

    @Test(description = "A new login cannot be given the admins permission", testName = "02f52ad0-be1d-4eb7-bf25-33cdfc8746b4", priority = 9)
    public void aNewLoginCannotBeGivenTheAdminsPermission() {
        final @NotNull CreateUserRequest request = People.request("approveProjects").build();
        refusedAs(create(Projects.approved(), request), 400, "invalidPermission");
        given(Api.anonymous()).body(Map.of("username", request.getUsername(), "password", People.TEMPORARY)).post("/auth/sign-in").then().statusCode(401);
    }

    @Test(description = "An existing user is added by username seeing the project", testName = "a665033f-e1db-4ae5-8119-ba44d7cfb3a3", priority = 10)
    public void anExistingUserIsAddedByUsernameSeeingTheProject() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull String other = Projects.approved();
        given(Api.asAdmin()).body(Map.of("username", person.username())).post("/projects/" + other + "/users/existing").then().statusCode(200)
                .body("projects.find { it.projectId == '" + other + "' }.permissions", equalTo(List.of("readProject")));
        assertEquals(permissionsOf(person, other), List.of("readProject"));
    }

    @Test(description = "An unknown username says so", testName = "2a3b3538-4158-45bc-ba0e-e53e8721d966", priority = 11)
    public void anUnknownUsernameSaysSo() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull String other = Projects.approved();
        refusedAs(given(Api.asAdmin()).body(Map.of("username", "nobody" + person.username())).post("/projects/" + other + "/users/existing"), 404, "unknownUser");
        final @NotNull String part = person.username().substring(0, person.username().length() - 1);
        refusedAs(given(Api.asAdmin()).body(Map.of("username", part)).post("/projects/" + other + "/users/existing"), 404, "unknownUser");
    }

    @Test(description = "Ticking gives one permission and unticking takes one", testName = "f0a57567-b927-4327-8f8d-5dd9941a302d", priority = 12)
    public void tickingGivesOnePermissionAndUntickingTakesOne() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        tick(project, person.id(), "recordEntries").then().statusCode(200);
        tick(project, person.id(), "recordEntries").then().statusCode(200);
        assertEquals(permissionsOf(person, project), List.of("readProject", "recordEntries"));
        untick(project, person.id(), "recordEntries").then().statusCode(200);
        assertEquals(permissionsOf(person, project), List.of("readProject"));
    }

    @Test(description = "Unticking see the project takes them out of the project", testName = "2f1bc09f-33a4-46d3-8507-c6ae5944e600", priority = 13)
    public void untickingSeeTheProjectTakesThemOutOfTheProject() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project, "recordEntries");
        untick(project, person.id(), "readProject").then().statusCode(200);
        given(person.asked()).get("/projects").then().statusCode(200).body("id", not(hasItem(project)));
        given(person.asked()).get("/projects/" + project + "/accounts").then().statusCode(403);
        given(Api.asAdmin()).get("/projects/" + project + "/users").then().body("id", not(hasItem(person.id())));
    }

    @Test(description = "Row190_many ticks of one permission at the same moment all answer as saved and keep one row", testName = "5a622b3e-f598-4a11-9129-b492ab03ae9a", priority = 14)
    public void row190ManyTicksOfOnePermissionAtTheSameMomentAllAnswerAsSavedAndKeepOneRow() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        assertEquals(atOnce(() -> tick(project, person.id(), "recordEntries")), List.of(200));
        assertEquals(permissionsOf(person, project), List.of("readProject", "recordEntries"));
        untick(project, person.id(), "recordEntries").then().statusCode(200);
        assertEquals(permissionsOf(person, project), List.of("readProject"), "one untick takes it, so one row was kept");
    }

    @Test(description = "Row190_many unticks of one permission at the same moment all answer as done and leave no row", testName = "387d9d74-3191-47ae-a025-b119063def3f", priority = 15)
    public void row190ManyUnticksOfOnePermissionAtTheSameMomentAllAnswerAsDoneAndLeaveNoRow() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project, "recordEntries");
        assertEquals(atOnce(() -> untick(project, person.id(), "recordEntries")), List.of(200));
        assertEquals(permissionsOf(person, project), List.of("readProject"));
    }

    @Test(description = "Row190_many first role names at the same moment all answer as saved and keep one row", testName = "9482a860-c333-44ea-a80f-4d81cab26df9", priority = 16)
    public void row190ManyFirstRoleNamesAtTheSameMomentAllAnswerAsSavedAndKeepOneRow() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        assertEquals(atOnce(() -> given(Api.asAdmin()).body(Map.of("roleName", "Site engineer")).put("/projects/" + project + "/users/" + person.id() + "/role-name")),
                List.of(200));
        given(person.asked()).get("/projects").then().body("find { it.id == '" + project + "' }.roleName", equalTo("Site engineer"));
    }

    @Test(description = "Row190_a save refused for anything but the same row twice is still refused", testName = "187f4253-dff6-4533-960b-f60c75fec446", priority = 17)
    public void row190ASaveRefusedForAnythingButTheSameRowTwiceIsStillRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        assertEquals(atOnce(() -> tick(project, person.id(), "approveProjects")), List.of(400));
        assertEquals(atOnce(() -> given(Api.asAdmin()).body(Map.of("roleName", "r".repeat(51))).put("/projects/" + project + "/users/" + person.id() + "/role-name")),
                List.of(400));
        assertEquals(permissionsOf(person, project), List.of("readProject"));
    }

    @Test(description = "Row190_a change to a row no longer there is still refused", testName = "e804beda-7441-480e-a54b-1429c1da4f0a", priority = 18)
    public void row190AChangeToARowNoLongerThereIsStillRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull String partner = partnerIn(project);
        given(Api.asAdmin()).delete("/projects/" + project + "/partners/" + partner).then().statusCode(200);
        assertEquals(atOnce(() -> given(Api.asAdmin()).post("/projects/" + project + "/users/" + person.id() + "/partners/" + partner)), List.of(404));
        untick(project, person.id(), "readProject").then().statusCode(200);
        assertEquals(atOnce(() -> tick(project, person.id(), "recordEntries")), List.of(404));
    }

    @Test(description = "Row190_many unchooses of one partner at the same moment all answer as done and leave it unchosen", testName = "f710b9a6-26d1-4f7e-ba2a-8763b773784b", priority = 19)
    public void row190ManyUnchoosesOfOnePartnerAtTheSameMomentAllAnswerAsDoneAndLeaveItUnchosen() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull String partner = partnerIn(project);
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + person.id() + "/partners/" + partner).then().statusCode(200);
        assertEquals(user(project, person.id()).getList("projects[0].partnerIds"), List.of(partner));
        assertEquals(atOnce(() -> given(Api.asAdmin()).delete("/projects/" + project + "/users/" + person.id() + "/partners/" + partner)), List.of(200));
        assertEquals(user(project, person.id()).getList("projects[0].partnerIds"), List.of());
    }

    @Test(description = "The admins permission or an unknown name is refused", testName = "324b869f-d80e-4471-8f4c-3f775fd49990", priority = 20)
    public void theAdminsPermissionOrAnUnknownNameIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        refusedAs(tick(project, person.id(), "approveProjects"), 400, "invalidPermission");
        refusedAs(tick(project, person.id(), "flyToTheMoon"), 400, "invalidPermission");
        assertEquals(permissionsOf(person, project), List.of("readProject"));
    }

    @Test(description = "The last who manages users cannot lose it", testName = "910a1c42-8a40-4d1c-922d-f9e82fc01612", priority = 21)
    public void theLastWhoManagesUsersCannotLoseIt() {
        final @NotNull Projects.Registered project = Projects.registered();
        final @NotNull String managerId = People.signIn(project.username(), People.PASSWORD).userId();
        refusedAs(given(project.asked()).delete("/projects/" + project.id() + "/users/" + managerId + "/permissions/manageUsers"), 409, "lastManager");
        refusedAs(given(project.asked()).delete("/projects/" + project.id() + "/users/" + managerId + "/permissions/readProject"), 409, "lastManager");
        given(project.asked()).get("/projects/" + project.id() + "/users").then().statusCode(200);
    }

    @Test(description = "With a second manager the first can leave", testName = "b099c879-b73d-4f36-a147-e8ebef5a1cb9", priority = 22)
    public void withASecondManagerTheFirstCanLeave() {
        final @NotNull String project = Projects.approved();
        final @NotNull String adminId = People.signIn(System.getenv("REDIX_ADMIN_USERNAME"), System.getenv("REDIX_ADMIN_NEW_PASSWORD")).userId();
        final @NotNull Person second = People.in(project, "manageUsers");
        given(second.asked()).delete("/projects/" + project + "/users/" + adminId + "/permissions/manageUsers").then().statusCode(200);
        refusedAs(given(second.asked()).delete("/projects/" + project + "/users/" + second.id() + "/permissions/manageUsers"), 409, "lastManager");
        given(second.asked()).post("/projects/" + project + "/users/" + adminId + "/permissions/manageUsers").then().statusCode(200);
    }

    @Test(description = "A manager inactive in the project does not count as the one who stays", testName = "7ec02a72-77b7-4354-a901-92e4ed817847", priority = 23)
    public void aManagerInactiveInTheProjectDoesNotCountAsTheOneWhoStays() {
        final @NotNull String project = Projects.approved();
        final @NotNull String adminId = People.signIn(System.getenv("REDIX_ADMIN_USERNAME"), System.getenv("REDIX_ADMIN_NEW_PASSWORD")).userId();
        final @NotNull Person second = People.in(project, "manageUsers");
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + second.id() + "/disable").then().statusCode(200);
        refusedAs(untick(project, adminId, "manageUsers"), 409, "lastManager");
    }

    @Test(description = "The people of a project are listed those active there first by name", testName = "583a3916-4262-4c0a-8ada-9f240663bb3c", priority = 24)
    public void thePeopleOfAProjectAreListedThoseActiveThereFirstByName() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person zed = People.newcomer(project, People.request().displayName("Zed " + System.nanoTime()).build());
        final @NotNull Person amy = People.newcomer(project, People.request().displayName("Amy " + System.nanoTime()).build());
        final @NotNull Person bob = People.newcomer(project, People.request().displayName("Bob " + System.nanoTime()).build());
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + amy.id() + "/disable").then().statusCode(200);
        final @NotNull List<String> order = given(Api.asAdmin()).get("/projects/" + project + "/users").then().statusCode(200).extract().path("id");
        assertTrue(order.indexOf(bob.id()) < order.indexOf(zed.id()) && order.indexOf(zed.id()) < order.indexOf(amy.id()), "listed " + order);
        assertEquals(order.getLast(), amy.id());
    }

    @Test(description = "Every permission change is in the history", testName = "f26caba8-21d3-41cb-b93a-f7faec753f0f", priority = 25, enabled = false)
    public void everyPermissionChangeIsInTheHistory() {
        throw new AssertionError("History has no route that reads a permission's lines over HTTP; Go's internal/users tests prove it");
    }
}
