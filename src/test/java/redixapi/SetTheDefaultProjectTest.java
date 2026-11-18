// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Emails;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;
import redix.support.Words;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class SetTheDefaultProjectTest {
    private static final @NotNull String NEW_PASSWORD = "Reset-passw0rd-2";

    private static @NotNull Response makeDefault(final @NotNull Person person, final @NotNull String projectId) {
        return given(person.asked()).post("/projects/" + projectId + "/default");
    }

    private static @NotNull String defaultOf(final @NotNull Person person) {
        return People.signIn(person.username(), People.PASSWORD).defaultProjectId();
    }

    private static @NotNull String emailOf(final @NotNull Person person) {
        return People.signIn(person.username(), People.PASSWORD).email();
    }

    private static void addExisting(final @NotNull String projectId, final @NotNull Person person) {
        given(Api.asAdmin()).body(Map.of("username", person.username())).post("/projects/" + projectId + "/users/existing").then().statusCode(200);
    }

    /** The code sent to address once person asks for it, in language. */
    private static @NotNull String confirmationTo(final @NotNull Person person, final @NotNull String address, final @NotNull String language) {
        final int before = Emails.to(address).size();
        given(person.asked()).header("Accept-Language", language).body(Map.of("email", address)).post("/me/email").then().statusCode(202);
        return Emails.next(address, before);
    }

    private static @NotNull Response confirm(final @NotNull Person person, final @NotNull String code) {
        return given(person.asked()).body(Map.of("code", code)).post("/me/email/confirm");
    }

    private static @NotNull String resetCodeFor(final @NotNull Person person) {
        final int before = Emails.to(person.email()).size();
        given(Api.anonymous()).body(Map.of("login", person.username())).post("/auth/forgot-password").then().statusCode(202);
        return Emails.codeIn(Emails.next(person.email(), before));
    }

    private static @NotNull Response reset(final @NotNull Person person, final @NotNull String code) {
        return given(Api.anonymous()).body(Map.of("login", person.username(), "code", code, "newPassword", NEW_PASSWORD)).post("/auth/reset-with-code");
    }

    private static @NotNull String wrongCode(final @NotNull String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test(description = "A default project is kept for the next session which opens it", testName = "151fc630-8ed4-4bdf-bf43-978cbebcde66", priority = 1)
    public void aDefaultProjectIsKeptForTheNextSessionWhichOpensIt() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        assertEquals(defaultOf(person), "");
        makeDefault(person, project).then().statusCode(200).body("defaultProjectId", equalTo(project));
        assertEquals(defaultOf(person), project);
    }

    @Test(description = "A default without access is not offered", testName = "c1d4fe96-e1b7-49ef-8022-860e2f118554", priority = 2)
    public void aDefaultWithoutAccessIsNotOffered() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        makeDefault(person, project).then().statusCode(200);
        given(Api.asAdmin()).delete("/projects/" + project + "/users/" + person.id() + "/permissions/readProject").then().statusCode(200);
        assertEquals(defaultOf(person), "");
    }

    @Test(description = "A default project he is inactive in is skipped not cleared and comes back when he is active again", testName = "e894067c-3426-434e-be59-681c57e280fc", priority = 3)
    public void aDefaultProjectHeIsInactiveInIsSkippedNotClearedAndComesBackWhenHeIsActiveAgain() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        makeDefault(person, project).then().statusCode(200);
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + person.id() + "/disable").then().statusCode(200);
        assertEquals(defaultOf(person), "");
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + person.id() + "/enable").then().statusCode(200);
        assertEquals(defaultOf(person), project);
    }

    @Test(description = "A project without access cannot be the default", testName = "8945b4db-5bef-4c52-8723-d1a898f72875", priority = 4)
    public void aProjectWithoutAccessCannotBeTheDefault() {
        final @NotNull Person person = People.in(Projects.approved());
        makeDefault(person, Projects.approved()).then().statusCode(403);
        assertEquals(defaultOf(person), "");
    }

    @Test(description = "A waiting project cannot be the default", testName = "b87a89ed-d511-4785-84b5-0460d9c99500", priority = 5)
    public void aWaitingProjectCannotBeTheDefault() {
        final @NotNull Projects.Registered approved = Projects.registered();
        final @NotNull Map<String, Object> waiting = Projects.registration("Waiting " + System.nanoTime(), List.of("A1"));
        final @NotNull String waitingId = Projects.register(waiting).then().statusCode(201).extract().path("id");
        final @NotNull String token = People.signIn((String) waiting.get("username"), People.PASSWORD).accessToken();
        given(Api.as(token)).post("/projects/" + waitingId + "/default").then().statusCode(403);
        assertEquals(People.signIn((String) waiting.get("username"), People.PASSWORD).defaultProjectId(), "");
        given(approved.asked()).post("/projects/" + approved.id() + "/default").then().statusCode(200);
    }

    @Test(description = "A finished project can be the default", testName = "0cc0f9fb-5307-4daa-9c4e-a4e25d87732a", priority = 6)
    public void aFinishedProjectCanBeTheDefault() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);
        makeDefault(person, project).then().statusCode(200);
        assertEquals(defaultOf(person), project);
    }

    @Test(description = "A new default replaces the old", testName = "fc2e9d5a-078f-4d7b-938f-b345e3a7cc51", priority = 7)
    public void aNewDefaultReplacesTheOld() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull Person person = People.in(project);
        addExisting(other, person);
        makeDefault(person, project).then().statusCode(200);
        makeDefault(person, other).then().statusCode(200).body("defaultProjectId", equalTo(other));
        assertEquals(defaultOf(person), other);
    }

    @Test(description = "A default project is a row keyed by its user so a new default changes that row", testName = "69caad81-e233-4f75-83d7-a5f494cc7363", priority = 8)
    public void aDefaultProjectIsARowKeyedByItsUserSoANewDefaultChangesThatRow() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull Person person = People.in(project);
        addExisting(other, person);
        makeDefault(person, project).then().statusCode(200);
        makeDefault(person, other).then().statusCode(200);
        given(person.asked()).delete("/projects/default").then().statusCode(200).body("defaultProjectId", equalTo(""));
        assertEquals(defaultOf(person), "", "one removal leaves none, so the second default changed the first's row");
    }

    @Test(description = "The database refuses a second default project for one user by its key", testName = "586d8d1b-8b52-4e31-aae8-053dd2f5c102", priority = 9, enabled = false)
    public void theDatabaseRefusesASecondDefaultProjectForOneUserByItsKey() {
        throw new AssertionError("about the table's key, inside the server; Go's internal/projects tests prove it");
    }

    @Test(description = "User default projects keyed by user_every default stays keyed by its user and its own ID goes", testName = "dcf8aea4-c451-4e82-8e9c-16522ae5cd2c", priority = 10, enabled = false)
    public void userDefaultProjectsKeyedByUserEveryDefaultStaysKeyedByItsUserAndItsOwnIdGoes() {
        throw new AssertionError("about the migration that keyed the table by its user, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "Removing the default leaves none so sign in shows the list", testName = "bb3178b3-fb79-4caa-b09c-ef1886ad7d4e", priority = 11)
    public void removingTheDefaultLeavesNoneSoSignInShowsTheList() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        makeDefault(person, project).then().statusCode(200);
        given(person.asked()).delete("/projects/default").then().statusCode(200).body("defaultProjectId", equalTo(""));
        assertEquals(defaultOf(person), "");
        given(person.asked()).delete("/projects/default").then().statusCode(200);
    }

    @Test(description = "A default is one users alone", testName = "48360c69-1e0d-4980-b5b8-0fc1ce8a32f1", priority = 12)
    public void aDefaultIsOneUsersAlone() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull Person other = People.in(project);
        makeDefault(person, project).then().statusCode(200);
        assertEquals(defaultOf(other), "");
        given(other.asked()).delete("/projects/default").then().statusCode(200);
        assertEquals(defaultOf(person), project);
    }

    @Test(description = "A new address holds only once its code is entered and the old one until then", testName = "4143d142-bc3f-449d-ac37-153770dadb1f", priority = 13)
    public void aNewAddressHoldsOnlyOnceItsCodeIsEnteredAndTheOldOneUntilThen() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull String address = "new." + person.email();
        final @NotNull String code = Emails.codeIn(confirmationTo(person, address, "en"));
        assertEquals(emailOf(person), person.email());
        confirm(person, code).then().statusCode(204);
        assertEquals(emailOf(person), address);
    }

    @Test(description = "A wrong code leaves the old address", testName = "703e89c7-6f34-464e-8413-5f4db1a1adff", priority = 14)
    public void aWrongCodeLeavesTheOldAddress() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull String code = Emails.codeIn(confirmationTo(person, "new." + person.email(), "en"));
        confirm(person, wrongCode(code)).then().statusCode(400).body("code", equalTo("codeWrong"));
        assertEquals(emailOf(person), person.email());
    }

    @Test(description = "A new address holding ends the reset code sent to the old one", testName = "bab0a270-9151-4a28-96fc-571f8e3e48b5", priority = 15)
    public void aNewAddressHoldingEndsTheResetCodeSentToTheOldOne() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull String resetCode = resetCodeFor(person);
        confirm(person, Emails.codeIn(confirmationTo(person, "new." + person.email(), "en"))).then().statusCode(204);
        reset(person, resetCode).then().statusCode(400).body("code", equalTo("codeWrong"));
    }

    @Test(description = "A code asked for a new address ends no reset code while the old address holds", testName = "81a36580-e916-42e4-9fc9-4c153450a457", priority = 16)
    public void aCodeAskedForANewAddressEndsNoResetCodeWhileTheOldAddressHolds() {
        final @NotNull Person person = People.in(Projects.approved());
        final @NotNull String resetCode = resetCodeFor(person);
        confirmationTo(person, "new." + person.email(), "en");
        reset(person, resetCode).then().statusCode(204);
    }

    @Test(description = "A reset code confirms no address and no code confirms nothing", testName = "7a5da571-6b11-4231-948c-446f90a01d81", priority = 17)
    public void aResetCodeConfirmsNoAddressAndNoCodeConfirmsNothing() {
        final @NotNull Person person = People.in(Projects.approved());
        confirm(person, "123456").then().statusCode(400).body("code", equalTo("codeWrong"));
        confirm(person, resetCodeFor(person)).then().statusCode(400).body("code", equalTo("codeWrong"));
        assertEquals(emailOf(person), person.email());
    }

    @Test(description = "An address another account holds or one not well formed is refused and nothing is sent", testName = "14277f25-6fea-46a5-8322-4b8420c85fb2", priority = 18)
    public void anAddressAnotherAccountHoldsOrOneNotWellFormedIsRefusedAndNothingIsSent() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull Person other = People.in(project);
        final int sent = Emails.to(other.email()).size();
        given(person.asked()).body(Map.of("email", other.email().toUpperCase())).post("/me/email").then().statusCode(409).body("code", equalTo("emailTaken"));
        given(person.asked()).body(Map.of("email", "not-an-email")).post("/me/email").then().statusCode(400).body("code", equalTo("emailInvalid"));
        Emails.settle();
        assertEquals(Emails.to(other.email()).size(), sent);
        assertEquals(Emails.to("not-an-email").size(), 0);
    }

    @Test(description = "An address another account took before its code was entered is refused", testName = "06b2739c-5547-4294-a137-96efddea494a", priority = 19)
    public void anAddressAnotherAccountTookBeforeItsCodeWasEnteredIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        final @NotNull Person other = People.in(project);
        final @NotNull String address = "wanted." + person.email();
        final @NotNull String code = Emails.codeIn(confirmationTo(person, address, "en"));
        given(Api.asAdmin()).body(Map.of("displayName", "Other", "mobile", "", "email", address)).put("/users/" + other.id()).then().statusCode(200);
        confirm(person, code).then().statusCode(409).body("code", equalTo("emailTaken"));
        assertEquals(emailOf(person), person.email());
    }

    @Test(description = "The admin sets his email in settings and then resets with it", testName = "99528fcf-2dfb-4e0b-a1f5-c0a13b44a2c8", priority = 20, enabled = false)
    public void theAdminSetsHisEmailInSettingsAndThenResetsWithIt() {
        throw new AssertionError("resetting the admin's password ends every admin session the run signs its requests with");
    }

    @Test(description = "The confirmation is in the language the phone asked in", testName = "df70aa4e-0cbf-420b-9137-3aa6a34da08e", priority = 21)
    public void theConfirmationIsInTheLanguageThePhoneAskedIn() {
        final @NotNull Person english = People.in(Projects.approved());
        assertEquals(Emails.subjectOf(confirmationTo(english, "new." + english.email(), "en")), Words.of("en", "email.confirmSubject"));
        final @NotNull Person arabic = People.in(Projects.approved());
        final @NotNull String email = confirmationTo(arabic, "new." + arabic.email(), "ar");
        assertEquals(Emails.subjectOf(email), Words.of("ar", "email.confirmSubject"));
        assertTrue(!Words.of("ar", "email.confirmSubject").equals(Words.of("en", "email.confirmSubject")));
    }

    @Test(description = "A confirmed address is in the history by the person with the old and the new", testName = "df8eaa6b-82a4-4356-82c5-49f4d34dc24e", priority = 22, enabled = false)
    public void aConfirmedAddressIsInTheHistoryByThePersonWithTheOldAndTheNew() {
        throw new AssertionError("History has no route that reads a person's email lines over HTTP; Go's internal/auth tests prove it");
    }
}
