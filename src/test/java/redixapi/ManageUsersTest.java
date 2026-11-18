// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.model.users.CreateUserRequest;
import redix.support.Api;
import redix.support.Emails;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;
import redix.support.Projects.Registered;
import redix.support.Routes;
import redix.support.Routes.Route;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ManageUsersTest {
    private static final @NotNull String NEW_PASSWORD = "Reset-passw0rd-2";

    private static @NotNull Response create(final @NotNull String projectId, final @NotNull CreateUserRequest request) {
        return given(Api.asAdmin()).body(request).post("/projects/" + projectId + "/users");
    }

    private static @NotNull Response edit(final @NotNull String userId, final @NotNull String displayName, final @NotNull String mobile, final @NotNull String email) {
        return given(Api.asAdmin()).body(Map.of("displayName", displayName, "mobile", mobile, "email", email)).put("/users/" + userId);
    }

    private static @NotNull Response membership(final @NotNull Person by, final @NotNull String projectId, final @NotNull String userId, final @NotNull String change) {
        return given(by.asked()).post("/projects/" + projectId + "/users/" + userId + "/" + change);
    }

    private static @NotNull Response asAdmin(final @NotNull String projectId, final @NotNull String userId, final @NotNull String change) {
        return given(Api.asAdmin()).post("/projects/" + projectId + "/users/" + userId + "/" + change);
    }

    private static @NotNull JsonPath listedTo(final @NotNull Person person, final @NotNull String projectId) {
        return new JsonPath(given(person.asked()).get("/projects").then().statusCode(200).extract().asString()).setRootPath("find { it.id == '" + projectId + "' }");
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    private static @NotNull Response signIn(final @NotNull String username, final @NotNull String password) {
        return given(Api.anonymous()).body(Map.of("username", username, "password", password)).post("/auth/sign-in");
    }

    private static void takeOut(final @NotNull String projectId, final @NotNull String userId) {
        given(Api.asAdmin()).delete("/projects/" + projectId + "/users/" + userId + "/permissions/readProject").then().statusCode(200);
    }

    private static @NotNull String codeFor(final @NotNull Person person) {
        final int before = Emails.to(person.email()).size();
        given(Api.anonymous()).body(Map.of("login", person.username())).post("/auth/forgot-password").then().statusCode(202);
        return Emails.codeIn(Emails.next(person.email(), before));
    }

    private static @NotNull Response reset(final @NotNull Person person, final @NotNull String code) {
        return given(Api.anonymous()).body(Map.of("login", person.username(), "code", code, "newPassword", NEW_PASSWORD)).post("/auth/reset-with-code");
    }

    @Test(description = "A manager creates a login holding what was ticked and seeing the project who must change the password", testName = "effdd74b-61db-45db-a66a-8756796c957a", priority = 1)
    public void aManagerCreatesALoginHoldingWhatWasTickedAndSeeingTheProjectWhoMustChangeThePassword() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person newcomer = People.newcomer(project, "recordEntries");
        assertTrue(People.signIn(newcomer.username(), People.TEMPORARY).mustChangePassword());
        final @NotNull Person person = People.in(project, "recordEntries");
        given(person.asked()).get("/projects").then().statusCode(200)
                .body("find { it.id == '" + project + "' }.permissions", hasItems("readProject", "recordEntries"));
    }

    @Test(description = "A repeated username is refused whatever its case", testName = "f183ce9f-4993-4eaa-a36d-6a3a18987d8c", priority = 2)
    public void aRepeatedUsernameIsRefusedWhateverItsCase() {
        final @NotNull String project = Projects.approved();
        final @NotNull CreateUserRequest first = People.request().build();
        create(project, first).then().statusCode(201);
        refusedAs(create(project, People.request().username(first.getUsername().toUpperCase()).build()), 409, "usernameTaken");
    }

    @Test(description = "A field outside its limit is refused", testName = "f9f18a2a-d91b-467e-8d1d-c09e541531d9", priority = 3)
    public void aFieldOutsideItsLimitIsRefused() {
        final @NotNull String project = Projects.approved();
        refusedAs(create(project, People.request().displayName("n".repeat(101)).build()), 400, "invalidName");
        refusedAs(create(project, People.request().username("u".repeat(51)).build()), 400, "invalidUsername");
        refusedAs(create(project, People.request().mobile("0".repeat(21)).build()), 400, "invalidMobile");
        refusedAs(create(project, People.request().email("e".repeat(250) + "@x.co").build()), 400, "emailInvalid");
        create(project, People.request().displayName("n".repeat(100)).mobile("0".repeat(20)).build()).then().statusCode(201);
    }

    @Test(description = "Editing changes the name and mobile", testName = "d3194142-b43e-49eb-84ae-16a859e77170", priority = 4)
    public void editingChangesTheNameAndMobile() {
        final @NotNull Person person = People.in(Projects.approved());
        edit(person.id(), "Renamed Person", "0511112222", person.email()).then().statusCode(200).body("displayName", equalTo("Renamed Person"))
                .body("mobile", equalTo("0511112222")).body("username", equalTo(person.username())).body("email", equalTo(person.email()))
                .body("mustChangePassword", equalTo(false));
        signIn(person.username(), People.PASSWORD).then().statusCode(200);
    }

    @Test(description = "The manager changes a persons email or keeps it", testName = "86b25a1d-c06d-44c3-9291-4e8281ce8075", priority = 5)
    public void theManagerChangesAPersonsEmailOrKeepsIt() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull String changed = "changed." + person.email();
        edit(person.id(), "Person", "0511112222", changed).then().statusCode(200).body("email", equalTo(changed));
        edit(person.id(), "Person", "0511112222", changed).then().statusCode(200).body("email", equalTo(changed));
    }

    @Test(description = "The manager cannot give an email another account holds nor one not well formed", testName = "ecdaa3e0-61e5-4f5c-89c3-e8bd9993d1e8", priority = 6)
    public void theManagerCannotGiveAnEmailAnotherAccountHoldsNorOneNotWellFormed() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull Person other = People.in(project);
        refusedAs(edit(person.id(), "Person", "0511112222", other.email().toUpperCase()), 409, "emailTaken");
        refusedAs(edit(person.id(), "Person", "0511112222", "not-an-email"), 400, "emailInvalid");
        given(Api.asAdmin()).get("/projects/" + project + "/users").then().body("find { it.id == '" + person.id() + "' }.email", equalTo(person.email()));
    }

    @Test(description = "The manager changing a persons email ends the reset code sent to the old one", testName = "8e141935-df06-4be4-acdd-0e1f51e0c0df", priority = 7)
    public void theManagerChangingAPersonsEmailEndsTheResetCodeSentToTheOldOne() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull String code = codeFor(person);
        edit(person.id(), "Person", "0511112222", "changed." + person.email()).then().statusCode(200);
        refusedAs(reset(person, code), 400, "codeWrong");
    }

    @Test(description = "The manager changing a persons email ends their code to an address not yet confirmed", testName = "b3d55abe-6b94-4aa2-aeaf-3cd468937f7f", priority = 8)
    public void theManagerChangingAPersonsEmailEndsTheirCodeToAnAddressNotYetConfirmed() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull String asked = "asked." + person.email();
        given(person.asked()).body(Map.of("email", asked)).post("/me/email").then().statusCode(202);
        final @NotNull String code = Emails.codeIn(Emails.next(asked, 0));
        edit(person.id(), "Person", "0511112222", "changed." + person.email()).then().statusCode(200);
        refusedAs(given(person.asked()).body(Map.of("code", code)).post("/me/email/confirm"), 400, "codeWrong");
    }

    @Test(description = "An edit that keeps the email leaves its reset code working", testName = "ebbb1656-c42b-4750-b1a1-b4fde2a65daf", priority = 9)
    public void anEditThatKeepsTheEmailLeavesItsResetCodeWorking() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull String code = codeFor(person);
        edit(person.id(), "Renamed Person", "0511112222", person.email()).then().statusCode(200);
        reset(person, code).then().statusCode(204);
    }

    @Test(description = "A mobile is ascii digits with a leading plus at most", testName = "df1ced5b-5ffd-4f34-b01f-9e68c971ba76", priority = 10)
    public void aMobileIsAsciiDigitsWithALeadingPlusAtMost() {
        final @NotNull String project = Projects.approved();
        for (final String mobile : List.of("٠٥١٢٣٤٥٦٧٨", "05-1234567", "05 1234567", "++966512345678", "0512+34567", "05abc")) {
            refusedAs(create(project, People.request().mobile(mobile).build()), 400, "invalidMobile");
        }
        create(project, People.request().mobile("").build()).then().statusCode(201).body("mobile", equalTo(""));
    }

    @Test(description = "A mobile may start with a plus", testName = "6b630a85-f30d-4453-85d8-7935d4d3a29a", priority = 11)
    public void aMobileMayStartWithAPlus() {
        create(Projects.approved(), People.request().mobile("+966512345678").build()).then().statusCode(201).body("mobile", equalTo("+966512345678"));
    }

    @Test(description = "An edit cannot give a mobile that is not digits", testName = "af053adb-51b1-4cd0-a125-74ac8643f443", priority = 12)
    public void anEditCannotGiveAMobileThatIsNotDigits() {
        final @NotNull Person person = People.in(Projects.approved());
        refusedAs(edit(person.id(), "Person", "٠٥١٢٣", person.email()), 400, "invalidMobile");
        refusedAs(edit(person.id(), "Person", "05-12", person.email()), 400, "invalidMobile");
    }

    @Test(description = "A bad field creates nothing", testName = "de6a70c4-49af-4408-9457-29b593eef02f", priority = 13)
    public void aBadFieldCreatesNothing() {
        final @NotNull CreateUserRequest request = People.request().mobile("05abc").build();
        refusedAs(create(Projects.approved(), request), 400, "invalidMobile");
        signIn(request.getUsername(), People.TEMPORARY).then().statusCode(401);
    }

    @Test(description = "A suppliers mobile is digits on add and on edit", testName = "3296ac2e-b507-4970-993b-89f6546a92d2", priority = 14)
    public void aSuppliersMobileIsDigitsOnAddAndOnEdit() {
        final @NotNull String project = "/projects/" + Projects.approved() + "/suppliers";
        refusedAs(given(Api.asAdmin()).body(Map.of("name", "Cement Co", "mobile", "05-1234")).post(project), 400, "invalidMobile");
        final @NotNull String supplier = given(Api.asAdmin()).body(Map.of("name", "Cement Co", "mobile", "+966512345678")).post(project).then().statusCode(201)
                .extract().path("id");
        refusedAs(given(Api.asAdmin()).body(Map.of("name", "Cement Co", "mobile", "٠٥١٢")).put(project + "/" + supplier), 400, "invalidMobile");
        given(Api.asAdmin()).body(Map.of("name", "Cement Co", "mobile", "0512345678")).put(project + "/" + supplier).then().statusCode(200);
        given(Api.asAdmin()).get(project).then().statusCode(200).body("find { it.id == '" + supplier + "' }.mobile", equalTo("0512345678"));
    }

    @Test(description = "A reset forces a change ends the lock and signs out every device", testName = "48088db6-21b3-4fe8-9e5f-52140603ccb9", priority = 15)
    public void aResetForcesAChangeEndsTheLockAndSignsOutEveryDevice() {
        final @NotNull Person person = People.in(Projects.approved());
        for (int i = 0; i < 5; i++) {
            signIn(person.username(), "Wrong-passw0rd-9").then().statusCode(401);
        }
        given(Api.asAdmin()).body(Map.of("temporaryPassword", "Second-temp0rary-1")).post("/users/" + person.id() + "/reset-password").then().statusCode(200)
                .body("mustChangePassword", equalTo(true));
        given(person.asked()).get("/projects").then().statusCode(401);
        signIn(person.username(), "Second-temp0rary-1").then().statusCode(200).body("mustChangePassword", equalTo(true));
        signIn(person.username(), People.PASSWORD).then().statusCode(401).body("code", equalTo("invalidCredentials"));
    }

    @Test(description = "A manager makes someone inactive in his project and active again and he keeps what he holds", testName = "dcf6a8b4-84a3-4b2a-99d6-a675fa543535", priority = 16)
    public void aManagerMakesSomeoneInactiveInHisProjectAndActiveAgainAndHeKeepsWhatHeHolds() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project, "recordEntries");
        asAdmin(project, person.id(), "disable").then().statusCode(200);
        assertTrue(listedTo(person, project).getBoolean("isInactive"));
        asAdmin(project, person.id(), "enable").then().statusCode(200);
        assertFalse(listedTo(person, project).getBoolean("isInactive"));
        assertTrue(listedTo(person, project).getList("permissions").containsAll(List.of("readProject", "recordEntries")));
    }

    @Test(description = "Every route of a project he is inactive in refuses him as for a project he is not in", testName = "728dfc1b-dd99-4fa8-8d46-ebfe64e2565a", priority = 17)
    public void everyRouteOfAProjectHeIsInactiveInRefusesHimAsForAProjectHeIsNotIn() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person inactive = People.in(project, "recordEntries", "manageSettings");
        final @NotNull Person stranger = People.in(Projects.approved(), "recordEntries", "manageSettings");
        asAdmin(project, inactive.id(), "disable").then().statusCode(200);
        final @NotNull List<Route> inProject = Routes.every().stream().filter(route -> route.path().contains("{projectId}") && !route.open()).toList();
        assertTrue(inProject.size() > 80, "the document names " + inProject.size() + " project routes");
        for (final Route route : inProject) {
            final @NotNull String path = route.in(project);
            final int asInactive = given(inactive.asked()).body("{}").request(route.method(), path).statusCode();
            final int asStranger = given(stranger.asked()).body("{}").request(route.method(), path).statusCode();
            assertEquals(asInactive, asStranger, route + " told someone inactive what it did not tell a stranger");
            assertTrue(asInactive >= 400, route + " answered someone inactive " + asInactive);
        }
    }

    @Test(description = "Inactive in one project he works in another with the same session", testName = "6910af18-178a-4274-9412-5fe8f81d4315", priority = 18)
    public void inactiveInOneProjectHeWorksInAnotherWithTheSameSession() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull Person person = People.in(project);
        given(Api.asAdmin()).body(Map.of("username", person.username())).post("/projects/" + other + "/users/existing").then().statusCode(200);
        asAdmin(project, person.id(), "disable").then().statusCode(200);
        given(person.asked()).get("/projects/" + project + "/accounts").then().statusCode(403);
        given(person.asked()).get("/projects/" + other + "/accounts").then().statusCode(200);
    }

    @Test(description = "His project list shows a project he is inactive in saying so and holding nothing", testName = "23a848ad-0dd8-4773-87ae-5248405ac2a3", priority = 19)
    public void hisProjectListShowsAProjectHeIsInactiveInSayingSoAndHoldingNothing() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project, "recordEntries");
        asAdmin(project, person.id(), "disable").then().statusCode(200);
        assertTrue(listedTo(person, project).getBoolean("isInactive"));
        assertEquals(listedTo(person, project).getList("permissions"), List.of());
    }

    @Test(description = "Someone taken out of a project he was inactive in no longer sees it on his list", testName = "0bd9071c-7bce-4e3a-86d3-0faebb1ca5e7", priority = 20)
    public void someoneTakenOutOfAProjectHeWasInactiveInNoLongerSeesItOnHisList() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        asAdmin(project, person.id(), "disable").then().statusCode(200);
        takeOut(project, person.id());
        given(person.asked()).get("/projects").then().statusCode(200).body("id", not(hasItems(project)));
    }

    @Test(description = "Someone inactive in every project signs in and renews as anyone does", testName = "7e3a8486-db1c-4ed0-9922-ac49d5f3a148", priority = 21)
    public void someoneInactiveInEveryProjectSignsInAndRenewsAsAnyoneDoes() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        asAdmin(project, person.id(), "disable").then().statusCode(200);
        final @NotNull String refreshToken = signIn(person.username(), People.PASSWORD).then().statusCode(200).extract().path("refreshToken");
        given(Api.anonymous()).body(Map.of("refreshToken", refreshToken)).post("/auth/refresh").then().statusCode(200);
    }

    @Test(description = "Someone inactive in every project is told a wrong password as anyone is", testName = "8a29fd61-09ea-45f3-b450-0a68265e9f09", priority = 22)
    public void someoneInactiveInEveryProjectIsToldAWrongPasswordAsAnyoneIs() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        asAdmin(project, person.id(), "disable").then().statusCode(200);
        refusedAs(signIn(person.username(), "Wrong-passw0rd-9"), 401, "invalidCredentials");
    }

    @Test(description = "A login is no longer disabled or enabled as a whole", testName = "801dd3bf-b798-42f2-b96e-8b8704144bcc", priority = 23)
    public void aLoginIsNoLongerDisabledOrEnabledAsAWhole() {
        final @NotNull Person person = People.in(Projects.approved());
        given(Api.asAdmin()).post("/users/" + person.id() + "/disable").then().statusCode(404);
        given(Api.asAdmin()).post("/users/" + person.id() + "/enable").then().statusCode(404);
        signIn(person.username(), People.PASSWORD).then().statusCode(200);
    }

    @Test(description = "The last active manager of a project cannot be made inactive there", testName = "68dfe69f-4eb0-4562-9c40-10bc691721fc", priority = 24)
    public void theLastActiveManagerOfAProjectCannotBeMadeInactiveThere() {
        final @NotNull Registered project = Projects.registered();
        final @NotNull String managerId = People.signIn(project.username(), People.PASSWORD).userId();
        refusedAs(given(project.asked()).post("/projects/" + project.id() + "/users/" + managerId + "/disable"), 409, "lastManager");
        given(Api.asAdmin()).post("/projects/" + project.id() + "/users/" + managerId + "/disable").then().statusCode(403);
        given(project.asked()).get("/projects/" + project.id() + "/accounts").then().statusCode(200);
    }

    @Test(description = "With a second active manager the first can be made inactive and then the second cannot", testName = "a80229d5-1d3c-4e55-8c9a-03ef3fceccdb", priority = 25)
    public void withASecondActiveManagerTheFirstCanBeMadeInactiveAndThenTheSecondCannot() {
        final @NotNull String project = Projects.approved();
        final @NotNull String adminId = People.admin().userId();
        final @NotNull Person second = People.in(project, "manageUsers");
        membership(second, project, adminId, "disable").then().statusCode(200);
        refusedAs(membership(second, project, second.id(), "disable"), 409, "lastManager");
        membership(second, project, adminId, "enable").then().statusCode(200);
    }

    @Test(description = "Making someone inactive and active again each write a history line by the manager", testName = "8ef2c7aa-f0e8-4582-940a-4fad784f49be", priority = 26, enabled = false)
    public void makingSomeoneInactiveAndActiveAgainEachWriteAHistoryLineByTheManager() {
        throw new AssertionError("History has no route that reads a membership's lines over HTTP; Go's internal/users tests prove it");
    }

    @Test(description = "Doing it twice changes nothing", testName = "4762e336-0619-4ffc-b203-e871a8fdb3b9", priority = 27)
    public void doingItTwiceChangesNothing() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        asAdmin(project, person.id(), "disable").then().statusCode(200);
        asAdmin(project, person.id(), "disable").then().statusCode(200);
        assertTrue(listedTo(person, project).getBoolean("isInactive"));
        asAdmin(project, person.id(), "enable").then().statusCode(200);
        asAdmin(project, person.id(), "enable").then().statusCode(200);
        assertFalse(listedTo(person, project).getBoolean("isInactive"));
    }

    @Test(description = "Row190_many disables at the same moment all answer as saved and keep one row", testName = "5127eb2b-6658-450e-b953-3ad1e88073e0", priority = 28)
    public void row190ManyDisablesAtTheSameMomentAllAnswerAsSavedAndKeepOneRow() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        assertEquals(IntStream.range(0, 8).parallel().map(i -> asAdmin(project, person.id(), "disable").statusCode()).boxed().distinct().toList(), List.of(200));
        assertTrue(listedTo(person, project).getBoolean("isInactive"));
        asAdmin(project, person.id(), "enable").then().statusCode(200);
        assertFalse(listedTo(person, project).getBoolean("isInactive"), "one enable undoes them all, so one row was kept");
    }

    @Test(description = "Row190_many enables at the same moment all answer as saved active and write one history line", testName = "b010036e-5fe1-47c1-8cb0-1302c8451b3e", priority = 29)
    public void row190ManyEnablesAtTheSameMomentAllAnswerAsSavedActiveAndWriteOneHistoryLine() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        asAdmin(project, person.id(), "disable").then().statusCode(200);
        assertEquals(IntStream.range(0, 8).parallel().map(i -> asAdmin(project, person.id(), "enable").statusCode()).boxed().distinct().toList(), List.of(200));
        assertFalse(listedTo(person, project).getBoolean("isInactive"));
    }

    @Test(description = "Only whoever manages its users makes someone inactive or active", testName = "4e564e7a-2f87-4588-be22-13f4eb728de3", priority = 30)
    public void onlyWhoeverManagesItsUsersMakesSomeoneInactiveOrActive() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull Person member = People.in(project, "manageProject");
        membership(member, project, person.id(), "disable").then().statusCode(403);
        membership(People.in(project, "manageUsers"), project, person.id(), "disable").then().statusCode(200);
        membership(member, project, person.id(), "enable").then().statusCode(403);
    }

    @Test(description = "Someone not in the project is not found", testName = "3a276c6c-b3c8-4a9f-883e-de2fbace770d", priority = 31)
    public void someoneNotInTheProjectIsNotFound() {
        final @NotNull Person stranger = People.in(Projects.approved());
        final @NotNull String project = Projects.approved();
        asAdmin(project, stranger.id(), "disable").then().statusCode(404);
        asAdmin(project, stranger.id(), "enable").then().statusCode(404);
    }

    @Test(description = "Taking someone out of the project ends his being inactive there so added again he arrives active", testName = "6812ca61-54d2-4c54-a253-4e24a10a8cb5", priority = 32)
    public void takingSomeoneOutOfTheProjectEndsHisBeingInactiveThereSoAddedAgainHeArrivesActive() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        asAdmin(project, person.id(), "disable").then().statusCode(200);
        takeOut(project, person.id());
        given(Api.asAdmin()).body(Map.of("username", person.username())).post("/projects/" + project + "/users/existing").then().statusCode(200);
        assertFalse(listedTo(person, project).getBoolean("isInactive"));
        given(person.asked()).get("/projects/" + project + "/accounts").then().statusCode(200);
    }

    @Test(description = "A user answer says inactive for each project and nothing for the whole login", testName = "6010e6a4-aa11-4aa0-a109-9da3a7fecce6", priority = 33)
    public void aUserAnswerSaysInactiveForEachProjectAndNothingForTheWholeLogin() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull Person person = People.in(project);
        given(Api.asAdmin()).body(Map.of("username", person.username())).post("/projects/" + other + "/users/existing").then().statusCode(200);
        final @NotNull JsonPath user = asAdmin(project, person.id(), "disable").then().statusCode(200).extract().jsonPath();
        assertTrue(user.getBoolean("projects.find { it.projectId == '" + project + "' }.isInactive"));
        assertFalse(user.getMap("").containsKey("isActive"), "the login carries no isActive of its own");
        final @NotNull JsonPath there = given(Api.asAdmin()).get("/projects/" + other + "/users").then().statusCode(200).extract().jsonPath();
        assertFalse(there.getBoolean("find { it.id == '" + person.id() + "' }.projects.find { it.projectId == '" + other + "' }.isInactive"));
    }

    @Test(description = "Someone with no role over a login cannot change it", testName = "e76a7d0a-a78a-4da5-9e61-4ab714fd9c38", priority = 34)
    public void someoneWithNoRoleOverALoginCannotChangeIt() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull Person member = People.in(project, "recordEntries");
        given(member.asked()).body(Map.of("displayName", "Taken", "mobile", "", "email", person.email())).put("/users/" + person.id()).then().statusCode(403);
        given(member.asked()).body(Map.of("temporaryPassword", "Second-temp0rary-1")).post("/users/" + person.id() + "/reset-password").then().statusCode(403);
        signIn(person.username(), People.PASSWORD).then().statusCode(200);
    }

    @Test(description = "A manager cannot reset someone in another project", testName = "4d0aaf34-cd96-4bc7-8e87-cb5ce7d60763", priority = 35)
    public void aManagerCannotResetSomeoneInAnotherProject() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull Person person = People.in(project);
        given(Api.asAdmin()).body(Map.of("username", person.username())).post("/projects/" + other + "/users/existing").then().statusCode(200);
        final @NotNull Person manager = People.in(project, "manageUsers");
        refusedAs(given(manager.asked()).body(Map.of("temporaryPassword", "Second-temp0rary-1")).post("/users/" + person.id() + "/reset-password"), 403, "otherProject");
        signIn(person.username(), People.PASSWORD).then().statusCode(200);
    }

    @Test(description = "A manager cannot take over the admin", testName = "4ecaf3e1-eca0-4582-b232-179f4fe901f9", priority = 36)
    public void aManagerCannotTakeOverTheAdmin() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person manager = People.in(project, "manageUsers");
        final @NotNull String adminId = People.admin().userId();
        refusedAs(given(manager.asked()).body(Map.of("temporaryPassword", "Second-temp0rary-1")).post("/users/" + adminId + "/reset-password"), 403, "otherProject");
        refusedAs(given(manager.asked()).body(Map.of("displayName", "Taken", "mobile", "", "email", "taken@redix.test")).put("/users/" + adminId), 403, "otherProject");
    }

    @Test(description = "Every change is in the history with no password hash", testName = "11e0f363-9a36-4ce7-9df4-2384a719cbb2", priority = 37, enabled = false)
    public void everyChangeIsInTheHistoryWithNoPasswordHash() {
        throw new AssertionError("History has no route that reads a login's lines over HTTP; Go's internal/users tests prove it");
    }

    @Test(description = "An answer never carries the password hash", testName = "06978b4d-0a83-44cb-9e98-caeb5a0aca8e", priority = 38)
    public void anAnswerNeverCarriesThePasswordHash() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        for (final Response answer : List.of(create(project, People.request().build()), edit(person.id(), "Person", "", person.email()),
                given(Api.asAdmin()).body(Map.of("temporaryPassword", "Second-temp0rary-1")).post("/users/" + person.id() + "/reset-password"),
                asAdmin(project, person.id(), "disable"), given(Api.asAdmin()).get("/projects/" + project + "/users"))) {
            answer.then().body(not(containsString("asswordHash"))).body(not(containsString(People.TEMPORARY)));
        }
    }
}
