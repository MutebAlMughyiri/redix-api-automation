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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ManageAccountsAndCashCustodiesTest {
    private static final @NotNull String RETIRED = "accounts have no opening balance since 10 Nov 2026 (redix-api 0073f6f, redix-app #89): money enters an account only through "
            + "entries and transfers";

    private static @NotNull String accounts(final @NotNull String projectId) {
        return "/projects/" + projectId + "/accounts";
    }

    private static @NotNull Response add(final @NotNull String projectId, final @NotNull String type, final @NotNull String name, final @NotNull String custodianId) {
        final @NotNull Map<String, Object> body = new HashMap<>(Map.of("type", type, "name", name));
        if (!custodianId.isEmpty()) {
            body.put("custodianId", custodianId);
        }
        return given(Api.asAdmin()).body(body).post(accounts(projectId));
    }

    private static @NotNull JsonPath listed(final @NotNull String projectId) {
        return given(Api.asAdmin()).get(accounts(projectId)).then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull String added(final @NotNull String projectId, final @NotNull String type, final @NotNull String name, final @NotNull String custodianId) {
        add(projectId, type, name, custodianId).then().statusCode(200);
        return listed(projectId).getString("find { it.name == '" + name + "' }.id");
    }

    private static @NotNull Response edit(final @NotNull String projectId, final @NotNull String accountId, final @NotNull String name, final @NotNull String custodianId) {
        final @NotNull Map<String, Object> body = new HashMap<>(Map.of("name", name));
        if (!custodianId.isEmpty()) {
            body.put("custodianId", custodianId);
        }
        return given(Api.asAdmin()).body(body).put(accounts(projectId) + "/" + accountId);
    }

    private static @NotNull List<String> offeredTo(final @NotNull Person person, final @NotNull String projectId) {
        return given(person.asked()).get(accounts(projectId) + "/offered").then().statusCode(200).extract().path("name");
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    @Test(description = "Finance adds a bank account with its opening balance", testName = "60b84ca4-2ff6-47d7-a5f6-e8e9dff7757e", priority = 1)
    public void financeAddsABankAccountWithItsOpeningBalance() {
        final @NotNull String project = Projects.approved();
        add(project, "bank", "  Al Rajhi  ", "").then().statusCode(200);
        final @NotNull JsonPath bank = listed(project).setRootPath("find { it.name == 'Al Rajhi' }");
        assertEquals(bank.getString("type"), "bank");
        assertEquals(bank.getInt("balance"), 0, "an account holds nothing until money moves into it");
        assertEquals(bank.getString("custodianId"), "");
        assertTrue(bank.getBoolean("isActive"));
    }

    @Test(description = "Finance adds a cash custody held by someone in the project", testName = "f926d554-b580-4616-80d9-42b113bf5e28", priority = 2)
    public void financeAddsACashCustodyHeldBySomeoneInTheProject() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person custodian = People.in(project);
        added(project, "cashCustody", "Site cash", custodian.id());
        final @NotNull JsonPath custody = listed(project).setRootPath("find { it.name == 'Site cash' }");
        assertEquals(custody.getString("type"), "cashCustody");
        assertEquals(custody.getString("custodianId"), custodian.id());
        assertFalse(custody.getString("custodianName").isEmpty());
    }

    @Test(description = "A bank account may have a custodian", testName = "5aad66ae-b2ca-4230-a1d8-3e68b4f7a235", priority = 3)
    public void aBankAccountMayHaveACustodian() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person custodian = People.in(project);
        added(project, "bank", "Al Rajhi", custodian.id());
        assertEquals(listed(project).getString("find { it.name == 'Al Rajhi' }.custodianId"), custodian.id());
    }

    @Test(description = "An overdrawn account may open below zero", testName = "8f840cad-c9ce-4e22-be72-1405d7f98e4c", priority = 4, enabled = false)
    public void anOverdrawnAccountMayOpenBelowZero() {
        throw new AssertionError(RETIRED);
    }

    @Test(description = "A cash custody needs a custodian", testName = "104dc725-fd06-4bc3-b9e7-15f5bfdc65dd", priority = 5)
    public void aCashCustodyNeedsACustodian() {
        final @NotNull String project = Projects.approved();
        refusedAs(add(project, "cashCustody", "Site cash", ""), 400, "custodianRequired");
        assertEquals(listed(project).getList("name"), List.of());
    }

    @Test(description = "An edit cannot leave a cash custody with nobody", testName = "6abdeb7d-0185-4b08-930e-87d90bd3b3b5", priority = 6)
    public void anEditCannotLeaveACashCustodyWithNobody() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person custodian = People.in(project);
        final @NotNull String custody = added(project, "cashCustody", "Site cash", custodian.id());
        refusedAs(edit(project, custody, "Site cash", ""), 400, "custodianRequired");
        assertEquals(listed(project).getString("find { it.id == '" + custody + "' }.custodianId"), custodian.id());
    }

    @Test(description = "A custodian must be in the project", testName = "e6bf7092-60e5-4492-901c-2e77f2e1c8c4", priority = 7)
    public void aCustodianMustBeInTheProject() {
        final @NotNull String project = Projects.approved();
        refusedAs(add(project, "cashCustody", "Site cash", People.in(Projects.approved()).id()), 400, "unknownCustodian");
        final @NotNull String bank = added(project, "bank", "Al Rajhi", "");
        refusedAs(edit(project, bank, "Al Rajhi", People.in(Projects.approved()).id()), 400, "unknownCustodian");
    }

    @Test(description = "An account needs a name of at most100 characters", testName = "5d22d102-34db-40d8-bf77-15c9a9c3db56", priority = 8)
    public void anAccountNeedsANameOfAtMost100Characters() {
        final @NotNull String project = Projects.approved();
        refusedAs(add(project, "bank", "a".repeat(101), ""), 400, "invalidName");
        refusedAs(add(project, "bank", "  ", ""), 400, "invalidName");
        final @NotNull String bank = added(project, "bank", "a".repeat(100), "");
        refusedAs(edit(project, bank, "", ""), 400, "invalidName");
    }

    @Test(description = "An opening balance has two decimals and a limit", testName = "6046e2fd-3070-4306-b970-a10642f04ce3", priority = 9, enabled = false)
    public void anOpeningBalanceHasTwoDecimalsAndALimit() {
        throw new AssertionError(RETIRED);
    }

    @Test(description = "An edit changes the name the opening balance and the custodian", testName = "4f823c2b-f305-46f8-9192-9418f326f7aa", priority = 10, enabled = false)
    public void anEditChangesTheNameTheOpeningBalanceAndTheCustodian() {
        throw new AssertionError(RETIRED + "; an edit of the name and the custodian is the 11th test, the type's staying the 3rd");
    }

    @Test(description = "An edit can leave a bank account with nobody", testName = "e5c90d3b-fb51-4f56-8972-dcb1001fea87", priority = 11)
    public void anEditCanLeaveABankAccountWithNobody() {
        final @NotNull String project = Projects.approved();
        final @NotNull String bank = added(project, "bank", "Al Rajhi", People.in(project).id());
        edit(project, bank, "Al Rajhi Main", "").then().statusCode(200);
        final @NotNull JsonPath edited = listed(project).setRootPath("find { it.id == '" + bank + "' }");
        assertEquals(edited.getString("name"), "Al Rajhi Main");
        assertEquals(edited.getString("custodianId"), "");
        assertEquals(edited.getString("type"), "bank");
    }

    @Test(description = "A changed opening balance is in the history old and new", testName = "d929184e-4c6b-4994-a5af-b0b10737412e", priority = 12, enabled = false)
    public void aChangedOpeningBalanceIsInTheHistoryOldAndNew() {
        throw new AssertionError(RETIRED);
    }

    @Test(description = "An opening balance cannot change once the account has an entry", testName = "197ff5d5-697f-4a76-b483-c80363a313f4", priority = 13, enabled = false)
    public void anOpeningBalanceCannotChangeOnceTheAccountHasAnEntry() {
        throw new AssertionError(RETIRED);
    }

    @Test(description = "An account with an entry still changes its name", testName = "8d2cedd3-ad6e-4fc6-b830-30e98878f43e", priority = 14)
    public void anAccountWithAnEntryStillChangesItsName() {
        final @NotNull String project = Projects.approved();
        Entries.draft(project);
        final @NotNull String bank = listed(project).getString("[0].id");
        edit(project, bank, "Renamed bank", "").then().statusCode(200);
        assertEquals(listed(project).getString("find { it.id == '" + bank + "' }.name"), "Renamed bank");
    }

    @Test(description = "A disabled account leaves entry and stays on the list", testName = "fe6c8def-a6fb-410a-a575-8d311e999826", priority = 15)
    public void aDisabledAccountLeavesEntryAndStaysOnTheList() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person finance = People.in(project, "recordEntries", "chargeAnyAccount");
        final @NotNull String bank = added(project, "bank", "Al Rajhi", "");
        given(Api.asAdmin()).post(accounts(project) + "/" + bank + "/disable").then().statusCode(200);
        assertFalse(offeredTo(finance, project).contains("Al Rajhi"));
        assertEquals(listed(project).getString("find { it.id == '" + bank + "' }.isActive"), "false");
    }

    @Test(description = "Enabling brings an account back into entry", testName = "87ab739a-9794-4a89-a89c-eccf149f4529", priority = 16)
    public void enablingBringsAnAccountBackIntoEntry() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person finance = People.in(project, "recordEntries", "chargeAnyAccount");
        final @NotNull String bank = added(project, "bank", "Al Rajhi", "");
        given(Api.asAdmin()).post(accounts(project) + "/" + bank + "/disable").then().statusCode(200);
        given(Api.asAdmin()).post(accounts(project) + "/" + bank + "/enable").then().statusCode(200);
        assertTrue(offeredTo(finance, project).contains("Al Rajhi"));
    }

    @Test(description = "Banks come first then in the order they were added", testName = "fbe62904-c704-4887-930e-a7f1e29cbfe4", priority = 17)
    public void banksComeFirstThenInTheOrderTheyWereAdded() {
        final @NotNull String project = Projects.approved();
        final @NotNull String custodian = People.in(project).id();
        added(project, "cashCustody", "Cash one", custodian);
        added(project, "bank", "Bank one", "");
        added(project, "cashCustody", "Cash two", custodian);
        added(project, "bank", "Bank two", "");
        assertEquals(listed(project).getList("name"), List.of("Bank one", "Bank two", "Cash one", "Cash two"));
    }

    @Test(description = "A custodian sees their custody and every other account without changing them", testName = "b1f0d897-deb9-447e-9bd6-941ec1085882", priority = 18)
    public void aCustodianSeesTheirCustodyAndEveryOtherAccountWithoutChangingThem() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person custodian = People.in(project, "recordEntries");
        final @NotNull String custody = added(project, "cashCustody", "Site cash", custodian.id());
        added(project, "bank", "Al Rajhi", "");
        final @NotNull JsonPath seen = given(custodian.asked()).get(accounts(project)).then().statusCode(200).extract().jsonPath();
        assertEquals(seen.getList("name"), List.of("Al Rajhi", "Site cash"));
        assertTrue(seen.getBoolean("find { it.id == '" + custody + "' }.isMine"));
        assertFalse(seen.getBoolean("find { it.name == 'Al Rajhi' }.isMine"));
        given(custodian.asked()).body(Map.of("name", "Mine")).put(accounts(project) + "/" + custody).then().statusCode(403);
        given(custodian.asked()).post(accounts(project) + "/" + custody + "/disable").then().statusCode(403);
    }

    @Test(description = "Field is offered only the accounts they hold", testName = "de546f5b-46b4-4eaf-a149-7b9a666e8a06", priority = 19)
    public void fieldIsOfferedOnlyTheAccountsTheyHold() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person field = People.in(project, "recordEntries");
        added(project, "cashCustody", "Site cash", field.id());
        added(project, "bank", "Al Rajhi", "");
        assertEquals(offeredTo(field, project), List.of("Site cash"));
    }

    @Test(description = "Field holding nothing is offered nothing", testName = "b8bf4cc2-a8d3-4a70-afc6-1c9d843c578e", priority = 20)
    public void fieldHoldingNothingIsOfferedNothing() {
        final @NotNull String project = Projects.approved();
        added(project, "bank", "Al Rajhi", "");
        assertEquals(offeredTo(People.in(project, "recordEntries"), project), List.of());
    }

    @Test(description = "Field is not offered a disabled account they hold", testName = "07252876-0ff9-4dbc-8d3a-b21fb006f4e2", priority = 21)
    public void fieldIsNotOfferedADisabledAccountTheyHold() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person field = People.in(project, "recordEntries");
        final @NotNull String custody = added(project, "cashCustody", "Site cash", field.id());
        given(Api.asAdmin()).post(accounts(project) + "/" + custody + "/disable").then().statusCode(200);
        assertEquals(offeredTo(field, project), List.of());
    }

    @Test(description = "Finance is offered every active account", testName = "7537216d-22cb-42ea-931e-b3737160cad8", priority = 22)
    public void financeIsOfferedEveryActiveAccount() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person finance = People.in(project, "recordEntries", "chargeAnyAccount");
        added(project, "cashCustody", "Site cash", People.in(project).id());
        added(project, "bank", "Al Rajhi", "");
        final @NotNull String gone = added(project, "bank", "Closed bank", "");
        given(Api.asAdmin()).post(accounts(project) + "/" + gone + "/disable").then().statusCode(200);
        assertEquals(offeredTo(finance, project), List.of("Al Rajhi", "Site cash"));
    }

    @Test(description = "One projects accounts are never anothers", testName = "9c1ef0bd-e29c-4e11-803a-58332f6291a2", priority = 23)
    public void oneProjectsAccountsAreNeverAnothers() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull String theirs = added(other, "bank", "Their bank", "");
        assertEquals(listed(project).getList("name"), List.of());
        edit(project, theirs, "Taken", "").then().statusCode(404);
        given(Api.asAdmin()).post(accounts(project) + "/" + theirs + "/disable").then().statusCode(404);
        given(Api.asAdmin()).delete(accounts(project) + "/" + theirs).then().statusCode(404);
        assertEquals(listed(other).getList("name"), List.of("Their bank"));
    }

    @Test(description = "A finished project refuses every change and stays readable", testName = "3a7e588e-7b29-4326-ab78-ab2ddafab341", priority = 24)
    public void aFinishedProjectRefusesEveryChangeAndStaysReadable() {
        final @NotNull String project = Projects.approved();
        final @NotNull String bank = added(project, "bank", "Al Rajhi", "");
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);
        refusedAs(add(project, "bank", "Second bank", ""), 409, "projectFinished");
        refusedAs(edit(project, bank, "Renamed", ""), 409, "projectFinished");
        refusedAs(given(Api.asAdmin()).post(accounts(project) + "/" + bank + "/disable"), 409, "projectFinished");
        refusedAs(given(Api.asAdmin()).delete(accounts(project) + "/" + bank), 409, "projectFinished");
        assertEquals(listed(project).getList("name"), List.of("Al Rajhi"));
    }

    @Test(description = "Everyone with access reads accounts and only finance changes them", testName = "c7fd4902-89ef-46b6-a78e-d4278a70d8ef", priority = 25)
    public void everyoneWithAccessReadsAccountsAndOnlyFinanceChangesThem() {
        final @NotNull String project = Projects.approved();
        added(project, "bank", "Al Rajhi", "");
        final @NotNull Person reader = People.in(project);
        given(reader.asked()).get(accounts(project)).then().statusCode(200).body("name", equalTo(List.of("Al Rajhi")));
        given(reader.asked()).body(Map.of("type", "bank", "name", "Mine")).post(accounts(project)).then().statusCode(403);
        given(People.in(project, "manageSettings").asked()).body(Map.of("type", "bank", "name", "Ours")).post(accounts(project)).then().statusCode(200);
        given(People.in(Projects.approved()).asked()).get(accounts(project)).then().statusCode(403);
    }

    @Test(description = "The members list names each active person once with nothing else", testName = "b503ea49-b087-43d3-b865-c519e7df54a9", priority = 26)
    public void theMembersListNamesEachActivePersonOnceWithNothingElse() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person active = People.in(project, "recordEntries", "attachFiles");
        final @NotNull Person inactive = People.in(project);
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + inactive.id() + "/disable").then().statusCode(200);
        final @NotNull JsonPath members = given(Api.asAdmin()).get("/projects/" + project + "/members").then().statusCode(200).extract().jsonPath();
        final @NotNull List<String> ids = members.getList("id");
        assertTrue(ids.contains(active.id()) && !ids.contains(inactive.id()));
        assertEquals(ids.stream().distinct().count(), (long) ids.size());
        assertEquals(members.getMap("[0]").keySet(), Set.of("id", "displayName"));
    }

    @Test(description = "Everyone with access reads the members and nobody else", testName = "1bc1aa06-6807-4bbf-a537-46ee73d5e0e4", priority = 27)
    public void everyoneWithAccessReadsTheMembersAndNobodyElse() {
        final @NotNull String project = Projects.approved();
        given(People.in(project).asked()).get("/projects/" + project + "/members").then().statusCode(200);
        given(People.in(Projects.approved()).asked()).get("/projects/" + project + "/members").then().statusCode(403);
        given(Api.anonymous()).get("/projects/" + project + "/members").then().statusCode(401);
    }
}
