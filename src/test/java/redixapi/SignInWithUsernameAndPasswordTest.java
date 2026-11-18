// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.model.auth.ForgotPasswordRequest;
import redix.model.auth.RefreshSessionRequest;
import redix.model.auth.RefreshSessionResponse;
import redix.model.auth.ResetWithCodeRequest;
import redix.model.auth.SignInResponse;
import redix.support.Api;
import redix.support.Emails;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class SignInWithUsernameAndPasswordTest {
    private static final @NotNull String NEW_PASSWORD = "Reset-passw0rd-2";

    private static @NotNull Person person() {
        return People.in(Projects.approved());
    }

    private static @NotNull Response signIn(final @NotNull String username, final @NotNull String password) {
        return given(Api.anonymous()).body(Map.of("username", username, "password", password)).post("/auth/sign-in");
    }

    private static @NotNull Response askForCode(final @NotNull String login, final @NotNull String language) {
        return given(Api.anonymous()).header("Accept-Language", language).body(ForgotPasswordRequest.builder().login(login).build()).post("/auth/forgot-password");
    }

    private static @NotNull Response reset(final @NotNull String login, final @NotNull String code, final @NotNull String newPassword) {
        return given(Api.anonymous()).body(ResetWithCodeRequest.builder().login(login).code(code).newPassword(newPassword).build()).post("/auth/reset-with-code");
    }

    private static @NotNull String codeFor(final @NotNull Person person) {
        return Emails.codeIn(emailAfterAsking(person, person.username(), "en"));
    }

    private static @NotNull String emailAfterAsking(final @NotNull Person person, final @NotNull String login, final @NotNull String language) {
        final int before = Emails.to(person.email()).size();
        askForCode(login, language).then().statusCode(202);
        return Emails.next(person.email(), before);
    }

    private static @NotNull String wrongCode(final @NotNull String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    @Test(description = "The right password opens a session with A15 minute access token", testName = "30e7ec12-32cd-4572-a4ac-9e241e087486", priority = 1)
    public void theRightPasswordOpensASessionWithA15MinuteAccessToken() {
        final @NotNull Person person = person();
        final @NotNull SignInResponse session = signIn(person.username(), People.PASSWORD).then().statusCode(200).extract().as(SignInResponse.class);
        final @NotNull Duration left = Duration.between(Instant.now(), Instant.parse(session.accessTokenExpiresAt()));
        assertFalse(session.accessToken().isEmpty());
        assertFalse(session.refreshToken().isEmpty());
        assertTrue(left.compareTo(Duration.ofMinutes(14)) > 0 && left.compareTo(Duration.ofMinutes(16)) < 0, "the token works " + left);
    }

    @Test(description = "A wrong password and an unknown username get the same answer", testName = "36cec06d-8dcb-425a-a215-77b432c6a981", priority = 2)
    public void aWrongPasswordAndAnUnknownUsernameGetTheSameAnswer() {
        final @NotNull Person person = person();
        refusedAs(signIn(person.username(), "Wrong-passw0rd-9"), 401, "invalidCredentials");
        refusedAs(signIn("nobody" + person.username(), "Wrong-passw0rd-9"), 401, "invalidCredentials");
    }

    @Test(description = "Five wrong passwords in a row lock the account for15 minutes", testName = "d27f2259-f985-4c2e-8d82-30498e7b845a", priority = 3)
    public void fiveWrongPasswordsInARowLockTheAccountFor15Minutes() {
        final @NotNull Person person = person();
        for (int i = 0; i < 5; i++) {
            signIn(person.username(), "Wrong-passw0rd-9").then().statusCode(401);
        }
        refusedAs(signIn(person.username(), People.PASSWORD), 401, "accountLocked");
    }

    @Test(description = "The right password clears the count of wrong ones", testName = "68bff94c-a1a9-408e-9c5a-ae11eef04038", priority = 4)
    public void theRightPasswordClearsTheCountOfWrongOnes() {
        final @NotNull Person person = person();
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 4; i++) {
                refusedAs(signIn(person.username(), "Wrong-passw0rd-9"), 401, "invalidCredentials");
            }
            signIn(person.username(), People.PASSWORD).then().statusCode(200);
        }
    }

    @Test(description = "Someone inactive in every project signs in and renews as anyone does", testName = "9f1d19a5-5f5b-4a29-b8a4-3885859a1c2f", priority = 5)
    public void someoneInactiveInEveryProjectSignsInAndRenewsAsAnyoneDoes() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + person.id() + "/disable").then().statusCode(200);
        final @NotNull SignInResponse session = signIn(person.username(), People.PASSWORD).then().statusCode(200).extract().as(SignInResponse.class);
        given(Api.anonymous()).body(RefreshSessionRequest.builder().refreshToken(session.refreshToken()).build()).post("/auth/refresh").then().statusCode(200);
    }

    @Test(description = "Someone inactive in every project is told a wrong password as anyone is", testName = "ee8208f4-5c56-4477-b3f3-a6417dda9864", priority = 6)
    public void someoneInactiveInEveryProjectIsToldAWrongPasswordAsAnyoneIs() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person person = People.in(project);
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + person.id() + "/disable").then().statusCode(200);
        refusedAs(signIn(person.username(), "Wrong-passw0rd-9"), 401, "invalidCredentials");
    }

    @Test(description = "An access token works for15 minutes by the servers clock", testName = "780bb397-efc3-4fea-b324-89e44150c9f9", priority = 7, enabled = false)
    public void anAccessTokenWorksFor15MinutesByTheServersClock() {
        throw new AssertionError("needs the server's clock moved 15 minutes; Go's TestReplayDotNetsAnswers proves it");
    }

    @Test(description = "A session says when its access token expires at sign in and at each renewal", testName = "90322d93-99a3-488b-ba44-052a2f419538", priority = 8)
    public void aSessionSaysWhenItsAccessTokenExpiresAtSignInAndAtEachRenewal() {
        final @NotNull Person person = person();
        final @NotNull SignInResponse session = signIn(person.username(), People.PASSWORD).then().statusCode(200).extract().as(SignInResponse.class);
        final @NotNull RefreshSessionResponse renewed = given(Api.anonymous()).body(RefreshSessionRequest.builder().refreshToken(session.refreshToken()).build())
                .post("/auth/refresh").then().statusCode(200).extract().as(RefreshSessionResponse.class);
        assertTrue(Instant.parse(session.accessTokenExpiresAt()).isAfter(Instant.now()));
        assertTrue(!Instant.parse(renewed.accessTokenExpiresAt()).isBefore(Instant.parse(session.accessTokenExpiresAt())));
    }

    @Test(description = "An access token is refused once the moment its session named has passed", testName = "bd7d9f50-2ec6-434f-a838-6b5b776cba63", priority = 9, enabled = false)
    public void anAccessTokenIsRefusedOnceTheMomentItsSessionNamedHasPassed() {
        throw new AssertionError("needs the server's clock moved past the token's end");
    }

    @Test(description = "A refresh replaces the refresh token", testName = "49e3afff-0324-4616-9ac3-1eea9684d840", priority = 10)
    public void aRefreshReplacesTheRefreshToken() {
        final @NotNull Person person = person();
        final @NotNull String first = signIn(person.username(), People.PASSWORD).then().statusCode(200).extract().as(SignInResponse.class).refreshToken();
        final @NotNull String second = given(Api.anonymous()).body(RefreshSessionRequest.builder().refreshToken(first).build()).post("/auth/refresh").then()
                .statusCode(200).extract().as(RefreshSessionResponse.class).refreshToken();
        assertNotEquals(second, first);
        refusedAs(given(Api.anonymous()).body(RefreshSessionRequest.builder().refreshToken(first).build()).post("/auth/refresh"), 401, "sessionExpired");
    }

    @Test(description = "A session used within12 hours stays open and one unused for12 hours ends", testName = "99b554d2-021b-4457-912c-b4c53f3c1f33", priority = 11, enabled = false)
    public void aSessionUsedWithin12HoursStaysOpenAndOneUnusedFor12HoursEnds() {
        throw new AssertionError("needs the server's clock moved 12 hours");
    }

    @Test(description = "The first user is the admin from configuration and must change the password", testName = "b3526a9c-222c-49ff-b8db-0f1d6c3443b4", priority = 12)
    public void theFirstUserIsTheAdminFromConfigurationAndMustChangeThePassword() {
        final @NotNull SignInResponse admin = People.signIn(System.getenv("REDIX_ADMIN_USERNAME"), System.getenv("REDIX_ADMIN_NEW_PASSWORD"));
        assertTrue(admin.permissions().contains("approveProjects"), "the admin holds " + admin.permissions());
        assertFalse(admin.mustChangePassword(), "the first sign-in of this run changed the configured password");
    }

    @Test(description = "A migration that stops before the users table seeds nothing and succeeds_as a rollback does", testName = "e7e11692-91a6-4f9d-ab00-a5d81e83a13a", priority = 13, enabled = false)
    public void aMigrationThatStopsBeforeTheUsersTableSeedsNothingAndSucceedsAsARollbackDoes() {
        throw new AssertionError("about the database's migrations, inside the server; Go's internal/auth tests prove it");
    }

    @Test(description = "A sign in is in the history with the user and the time", testName = "484ac12d-59e9-48da-8d0d-645c515d9df5", priority = 14, enabled = false)
    public void aSignInIsInTheHistoryWithTheUserAndTheTime() {
        throw new AssertionError("History has no route to read it over HTTP yet");
    }

    @Test(description = "A session says what its user holds everywhere", testName = "63740293-33e6-4379-85fc-8f7567e50178", priority = 15)
    public void aSessionSaysWhatItsUserHoldsEverywhere() {
        final @NotNull Person person = person();
        assertTrue(signIn(person.username(), People.PASSWORD).then().statusCode(200).extract().as(SignInResponse.class).permissions().isEmpty());
    }

    @Test(description = "A code to the accounts email and a new password reset it and the old one no longer signs in", testName = "4c44309e-5fb4-435f-ab9b-06a28fc495dc", priority = 16)
    public void aCodeToTheAccountsEmailAndANewPasswordResetItAndTheOldOneNoLongerSignsIn() {
        final @NotNull Person person = person();
        reset(person.username(), codeFor(person), NEW_PASSWORD).then().statusCode(204);
        refusedAs(signIn(person.username(), People.PASSWORD), 401, "invalidCredentials");
        signIn(person.username(), NEW_PASSWORD).then().statusCode(200);
    }

    @Test(description = "The email names the account as the username does whatever its case", testName = "19c34aaa-c0d1-48f0-a2c9-5414884fe7c6", priority = 17)
    public void theEmailNamesTheAccountAsTheUsernameDoesWhateverItsCase() {
        final @NotNull Person person = person();
        reset(person.email().toUpperCase(), Emails.codeIn(emailAfterAsking(person, person.email().toUpperCase(), "en")), NEW_PASSWORD).then().statusCode(204);
        signIn(person.username(), NEW_PASSWORD).then().statusCode(200);
    }

    @Test(description = "A wrong code resets nothing", testName = "0cf23a1d-b29d-42ca-a9fb-644d5e126bbf", priority = 18)
    public void aWrongCodeResetsNothing() {
        final @NotNull Person person = person();
        refusedAs(reset(person.username(), wrongCode(codeFor(person)), NEW_PASSWORD), 400, "codeWrong");
        signIn(person.username(), People.PASSWORD).then().statusCode(200);
    }

    @Test(description = "The admin resets the same way", testName = "0b8cda87-e8cd-4ed7-9554-84bbacbe4cb8", priority = 19, enabled = false)
    public void theAdminResetsTheSameWay() {
        throw new AssertionError("resetting the admin's password ends every admin session the run signs its requests with");
    }

    @Test(description = "An unknown account or one with no email gets the same answer as a known one and nothing is sent", testName = "9b176248-d928-4618-83f6-9b07e3b87ea1", priority = 20)
    public void anUnknownAccountOrOneWithNoEmailGetsTheSameAnswerAsAKnownOneAndNothingIsSent() {
        final @NotNull Person person = person();
        final int before = Emails.total();
        askForCode("nobody" + person.username(), "en").then().statusCode(202);
        Emails.settle();
        assertEquals(Emails.total(), before, "an unknown account is sent nothing");
        askForCode(person.username(), "en").then().statusCode(202);
    }

    @Test(description = "An azure sender not set up throws nothing so the answer stays as it was", testName = "19db30d0-c44b-4912-8947-de38bb5c15f8", priority = 21, enabled = false)
    public void anAzureSenderNotSetUpThrowsNothingSoTheAnswerStaysAsItWas() {
        throw new AssertionError("Azure Communication Services left with .NET; email goes over SMTP to the server's own Postfix (redix-api #127 D9)");
    }

    @Test(description = "The answer leaves before the code is sent so how long it takes says nothing", testName = "d16904f4-d974-4346-9680-99c7e5723b87", priority = 22)
    public void theAnswerLeavesBeforeTheCodeIsSentSoHowLongItTakesSaysNothing() {
        final @NotNull Person person = person();
        for (final String login : new String[] {person.username(), "nobody" + person.username()}) {
            final @NotNull Response answer = askForCode(login, "en");
            answer.then().statusCode(202);
            assertTrue(answer.time() < 2000, login + " took " + answer.time() + " ms");
        }
    }

    @Test(description = "A code works once", testName = "42e5660e-1c2c-444e-909e-b118463b2ae2", priority = 23)
    public void aCodeWorksOnce() {
        final @NotNull Person person = person();
        final @NotNull String code = codeFor(person);
        reset(person.username(), code, NEW_PASSWORD).then().statusCode(204);
        refusedAs(reset(person.username(), code, "Again-passw0rd-3"), 400, "codeWrong");
    }

    @Test(description = "A code works within15 minutes and not after", testName = "1e5be367-2ba0-4374-9c54-9199de729a75", priority = 24, enabled = false)
    public void aCodeWorksWithin15MinutesAndNotAfter() {
        throw new AssertionError("needs the server's clock moved 15 minutes");
    }

    @Test(description = "Five wrong codes end it so the right one is refused after them but not with a try left", testName = "ce1491b0-bc39-48fc-86a7-744dfb15cf4f", priority = 25)
    public void fiveWrongCodesEndItSoTheRightOneIsRefusedAfterThemButNotWithATryLeft() {
        final @NotNull Person ended = person();
        final @NotNull String code = codeFor(ended);
        for (int i = 0; i < 5; i++) {
            refusedAs(reset(ended.username(), wrongCode(code), NEW_PASSWORD), 400, "codeWrong");
        }
        refusedAs(reset(ended.username(), code, NEW_PASSWORD), 400, "codeWrong");
        final @NotNull Person tried = person();
        final @NotNull String kept = codeFor(tried);
        for (int i = 0; i < 4; i++) {
            refusedAs(reset(tried.username(), wrongCode(kept), NEW_PASSWORD), 400, "codeWrong");
        }
        reset(tried.username(), kept, NEW_PASSWORD).then().statusCode(204);
    }

    @Test(description = "A new code ends the earlier one", testName = "7edf9b69-3c10-4d03-98fc-c813196109bd", priority = 26)
    public void aNewCodeEndsTheEarlierOne() {
        final @NotNull Person person = person();
        final @NotNull String earlier = codeFor(person);
        final @NotNull String later = codeFor(person);
        if (!earlier.equals(later)) {
            refusedAs(reset(person.username(), earlier, NEW_PASSWORD), 400, "codeWrong");
        }
        reset(person.username(), later, NEW_PASSWORD).then().statusCode(204);
    }

    @Test(description = "Asking too often is refused for a while and asking within the limit is not", testName = "794e0c6d-1f91-4fd0-a212-e132db223799", priority = 27, enabled = false)
    public void askingTooOftenIsRefusedForAWhileAndAskingWithinTheLimitIsNot() {
        throw new AssertionError("needs a server with a low REDIX_EmailCodes__PermitsPerHour; the test server's is raised so the tests' asks never meet it");
    }

    @Test(description = "One login typed too often is refused whatever its case and another login is not", testName = "9e13a0b4-0b79-4a8d-a650-d928d02d4fc5", priority = 28)
    public void oneLoginTypedTooOftenIsRefusedWhateverItsCaseAndAnotherLoginIsNot() {
        final @NotNull Person person = person();
        for (int i = 0; i < 5; i++) {
            askForCode(i % 2 == 0 ? person.username() : person.username().toUpperCase(), "en").then().statusCode(202);
        }
        askForCode(person.username().toUpperCase(), "en").then().statusCode(429);
        askForCode(person().username(), "en").then().statusCode(202);
    }

    @Test(description = "An accounts login is limited as an unknown one is and the refused ask sends nothing", testName = "97997a5c-c7a6-4897-bce9-e867fe937c86", priority = 29)
    public void anAccountsLoginIsLimitedAsAnUnknownOneIsAndTheRefusedAskSendsNothing() {
        final @NotNull Person person = person();
        final @NotNull String unknown = "nobody" + person.username();
        for (int i = 0; i < 5; i++) {
            askForCode(person.username(), "en").then().statusCode(202);
            askForCode(unknown, "en").then().statusCode(202);
        }
        Emails.next(person.email(), 4);
        final int sent = Emails.to(person.email()).size();
        askForCode(person.username(), "en").then().statusCode(429);
        askForCode(unknown, "en").then().statusCode(429);
        Emails.settle();
        assertEquals(Emails.to(person.email()).size(), sent, "the refused ask sent nothing");
    }

    @Test(description = "Only the codes hash is kept", testName = "291b1a89-f457-4dfc-820d-f3f6bad02d3c", priority = 30, enabled = false)
    public void onlyTheCodesHashIsKept() {
        throw new AssertionError("about what the database keeps, inside the server; Go's internal/auth tests prove it");
    }

    @Test(description = "A reset ends every session unlocks the account and asks for no other change", testName = "7a426813-25d0-4d4b-9a9a-de1e012fa8fc", priority = 31)
    public void aResetEndsEverySessionUnlocksTheAccountAndAsksForNoOtherChange() {
        final @NotNull Person person = person();
        for (int i = 0; i < 5; i++) {
            signIn(person.username(), "Wrong-passw0rd-9").then().statusCode(401);
        }
        reset(person.username(), codeFor(person), NEW_PASSWORD).then().statusCode(204);
        given(person.asked()).get("/projects").then().statusCode(401);
        assertFalse(signIn(person.username(), NEW_PASSWORD).then().statusCode(200).extract().as(SignInResponse.class).mustChangePassword());
    }

    @Test(description = "A wrong code ends no session", testName = "6b1396fc-54ec-4af2-a575-7c73f16443c6", priority = 32)
    public void aWrongCodeEndsNoSession() {
        final @NotNull Person person = person();
        refusedAs(reset(person.username(), wrongCode(codeFor(person)), NEW_PASSWORD), 400, "codeWrong");
        given(person.asked()).get("/projects").then().statusCode(200);
    }

    @Test(description = "A weak new password is refused and the code stays unspent", testName = "772557f0-434d-4e8e-9084-9c19aaa465e1", priority = 33)
    public void aWeakNewPasswordIsRefusedAndTheCodeStaysUnspent() {
        final @NotNull Person person = person();
        final @NotNull String code = codeFor(person);
        refusedAs(reset(person.username(), code, "123"), 400, "weakPassword");
        reset(person.username(), code, NEW_PASSWORD).then().statusCode(204);
    }

    @Test(description = "The email is in the language the phone asked in and arabic for any other", testName = "0b5c8555-1bd3-4561-9c89-a62661058d1d", priority = 34)
    public void theEmailIsInTheLanguageThePhoneAskedInAndArabicForAnyOther() {
        final @NotNull Person english = person();
        assertEquals(subjectOf(emailAfterAsking(english, english.username(), "en")), word("en", "email.resetSubject"));
        final @NotNull Person other = person();
        assertEquals(subjectOf(emailAfterAsking(other, other.username(), "fr")), word("ar", "email.resetSubject"));
    }

    @Test(description = "An emails subject and body are the translations words in the language asked with its code", testName = "2c8d84b3-3acf-488e-b33e-b620d57252d0", priority = 35)
    public void anEmailsSubjectAndBodyAreTheTranslationsWordsInTheLanguageAskedWithItsCode() {
        final @NotNull Person person = person();
        final @NotNull String email = emailAfterAsking(person, person.username(), "ar");
        assertEquals(subjectOf(email), word("ar", "email.resetSubject"));
        final @NotNull String body = word("ar", "email.resetBody");
        assertTrue(email.contains(body.substring(0, Math.min(12, body.indexOf('{') < 0 ? body.length() : body.indexOf('{')))), email);
    }

    @Test(description = "Development keeps each email as a file in its folder and sends nothing", testName = "1916d3c7-fb37-4b1c-8c0a-f48554d7039e", priority = 36)
    public void developmentKeepsEachEmailAsAFileInItsFolderAndSendsNothing() {
        final @NotNull Person person = person();
        final @NotNull String email = emailAfterAsking(person, person.username(), "en");
        assertTrue(email.startsWith("To: " + person.email() + "\nSubject: "), email);
    }

    @Test(description = "A reset is in the history by the person with no password hash", testName = "f4694e67-2d49-4188-bd45-b949b4655dcd", priority = 37, enabled = false)
    public void aResetIsInTheHistoryByThePersonWithNoPasswordHash() {
        throw new AssertionError("History has no route to read it over HTTP yet");
    }

    private static @NotNull String subjectOf(final @NotNull String email) {
        return email.split("\n")[1].substring("Subject: ".length());
    }

    private static @NotNull String word(final @NotNull String language, final @NotNull String key) {
        return given(Api.anonymous()).get("/translations/" + language).then().statusCode(200).extract().as(new TypeRef<Map<String, String>>() {}).get(key);
    }
}
