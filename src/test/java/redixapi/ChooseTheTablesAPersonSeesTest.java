// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Contracts;
import redix.support.Entries;
import redix.support.History;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;
import redix.support.Structure;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class ChooseTheTablesAPersonSeesTest {
    /**
     * A project whose purchases item has its general table and «الأعمدة», with an approved purchase in each, and a person who records, approves and
     * sees reports there (redix-api #106).
     */
    private record Sighted(@NotNull String project, @NotNull String item, @NotNull String general, @NotNull String group, @NotNull String seenEntry,
                           @NotNull String unseenEntry, @NotNull Person person) {
        static @NotNull Sighted made() {
            final @NotNull String project = Projects.approved();
            final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
            final @NotNull String general = Structure.tablesOf(project, item).getString("find { it.kind == 'general' }.id");
            final @NotNull String group = Structure.addSubgroup(project, item, "الأعمدة");
            final @NotNull Person person = People.in(project, "recordEntries", "approveEntries", "seeReports", "chargeAnyAccount");
            return new Sighted(project, item, general, group, approvedIn(project, item, general), approvedIn(project, item, group), person);
        }

        /** Unticks table for the person, as their manager. */
        void untick(final @NotNull String table) {
            given(Api.asAdmin()).delete(path(table)).then().statusCode(200);
        }

        /** Ticks table again for the person, as their manager. */
        void tick(final @NotNull String table) {
            given(Api.asAdmin()).post(path(table)).then().statusCode(200);
        }

        private @NotNull String path(final @NotNull String table) {
            return "/projects/" + project + "/users/" + person.id() + "/tables/" + table;
        }

        /** The ids of the entries «القيود» shows the person. */
        @NotNull List<String> listed() {
            return given(person.asked()).get("/projects/" + project + "/entries").then().statusCode(200).extract().jsonPath().getList("id");
        }

        /** The ids of the tables of the item the person is offered. */
        @NotNull List<String> offered() {
            return given(person.asked()).get("/projects/" + project + "/items/" + item + "/tables").then().statusCode(200).extract().jsonPath().getList("id");
        }
    }

    /** A purchase the admin recorded into table, sent and approved. */
    private static @NotNull String approvedIn(final @NotNull String project, final @NotNull String item, final @NotNull String table) {
        final @NotNull String entry = Entries.draftIn(project, item, table);
        Entries.submit(project, entry);
        Entries.approve(project, entry);
        return entry;
    }

    @Test(description = "An unticked tables entries are neither listed nor found", testName = "adb7d931-6807-4044-a0e5-3b0bc850e4f7", priority = 1)
    public void anUntickedTablesEntriesAreNeitherListedNorFound() {
        final @NotNull Sighted sighted = Sighted.made();
        assertTrue(sighted.listed().containsAll(List.of(sighted.seenEntry(), sighted.unseenEntry())), "before any unticking the person does not see both");

        sighted.untick(sighted.group());

        assertTrue(sighted.listed().contains(sighted.seenEntry()) && !sighted.listed().contains(sighted.unseenEntry()), "the person's entries: " + sighted.listed());
        final @NotNull String unseen = "/projects/" + sighted.project() + "/entries/" + sighted.unseenEntry();
        given(sighted.person().asked()).get(unseen).then().statusCode(404);
        given(sighted.person().asked()).body(Map.of("description", "x")).put(unseen + "/description").then().statusCode(404);
        given(sighted.person().asked()).delete(unseen).then().statusCode(404);
        given(Api.asAdmin()).get(unseen).then().statusCode(200);
    }

    @Test(description = "The summary the balances and the item report count only the tables one sees", testName = "70aa007f-b61a-4d8c-bc45-6e267ba453e7", priority = 2)
    public void theSummaryTheBalancesAndTheItemReportCountOnlyTheTablesOneSees() {
        final @NotNull Sighted sighted = Sighted.made();
        final @NotNull String project = "/projects/" + sighted.project();
        final double amount = given(Api.asAdmin()).get(project + "/entries/" + sighted.unseenEntry()).then().statusCode(200).extract().jsonPath().getDouble("entry.amount");

        sighted.untick(sighted.group());

        final double managers = given(Api.asAdmin()).get(project + "/summary").then().statusCode(200).extract().jsonPath().getDouble("expenses");
        final double persons = given(sighted.person().asked()).get(project + "/summary").then().statusCode(200).extract().jsonPath().getDouble("expenses");
        assertEquals(managers - persons, amount, 0.001, "the summary's expenses");
        final double managersBalance = given(Api.asAdmin()).get(project + "/accounts").then().statusCode(200).extract().jsonPath().getDouble("[0].balance");
        final double personsBalance = given(sighted.person().asked()).get(project + "/accounts").then().statusCode(200).extract().jsonPath().getDouble("[0].balance");
        assertEquals(personsBalance - managersBalance, amount, 0.001, "the account's balance");
        final @NotNull List<String> tables = given(sighted.person().asked()).get(project + "/reports/items/" + sighted.item()).then().statusCode(200).extract()
                .jsonPath().getList("subgroups.id");
        assertTrue(tables.contains(sighted.general()) && !tables.contains(sighted.group()), "the item report's tables: " + tables);
    }

    @Test(description = "The whole documents are for whoever sees every table", testName = "22682c6a-752c-4489-993e-e171efca968f", priority = 3)
    public void theWholeDocumentsAreForWhoeverSeesEveryTable() {
        final @NotNull Sighted sighted = Sighted.made();
        sighted.untick(sighted.group());

        for (final String path : List.of("/periodic-reports", "/periodic-reports/next", "/vat-statement/xlsx", "/reports/contracts/pdf")) {
            given(sighted.person().asked()).get("/projects/" + sighted.project() + path).then().statusCode(403);
            assertNotEquals(given(Api.asAdmin()).get("/projects/" + sighted.project() + path).statusCode(), 403, path + " refused the manager");
        }
    }

    @Test(description = "A table is offered until unticked and a tick brings it back", testName = "7499d941-7b7f-42df-b17e-2c18ce0b6927", priority = 4)
    public void aTableIsOfferedUntilUntickedAndATickBringsItBack() {
        final @NotNull Sighted sighted = Sighted.made();
        assertTrue(sighted.offered().contains(sighted.group()), "the group is not offered before unticking");

        sighted.untick(sighted.group());

        assertFalse(sighted.offered().contains(sighted.group()), "an unticked group is offered");
        final @NotNull Map<String, Object> body = Entries.purchaseOf(sighted.project(), sighted.item());
        body.put("tableId", sighted.group());
        Entries.record(sighted.person().asked(), sighted.project(), body).then().statusCode(400).body("code", equalTo("unknownTable"));
        sighted.untick(sighted.group());
        sighted.tick(sighted.group());
        assertTrue(sighted.offered().contains(sighted.group()) && sighted.listed().contains(sighted.unseenEntry()), "ticking again did not bring the group back");
        given(Api.asAdmin()).delete("/projects/" + sighted.project() + "/users/" + sighted.person().id() + "/tables/" + UUID.randomUUID()).then().statusCode(404);
    }

    @Test(description = "Only those who see an entrys table are told of it", testName = "35dd35a4-f8ba-4e68-a2aa-e879d8174f35", priority = 5)
    public void onlyThoseWhoSeeAnEntrysTableAreToldOfIt() {
        final @NotNull Sighted sighted = Sighted.made();
        sighted.untick(sighted.group());
        final int told = told(sighted.person());

        final @NotNull String intoGroup = Entries.draftIn(sighted.project(), sighted.item(), sighted.group());
        Entries.submit(sighted.project(), intoGroup);
        assertEquals(told(sighted.person()), told, "the person was told of an entry of a table they do not see");

        final @NotNull String intoGeneral = Entries.draftIn(sighted.project(), sighted.item(), sighted.general());
        Entries.submit(sighted.project(), intoGeneral);
        assertEquals(told(sighted.person()), told + 1, "the person was not told of an entry they see");
    }

    private static int told(final @NotNull Person person) {
        return given(person.asked()).get("/notifications").then().statusCode(200).extract().jsonPath().getList("$").size();
    }

    @Test(description = "A new person starts unticked from what the form left unticked", testName = "edc20de5-1966-4dfc-b285-d0e4d525ec0f", priority = 6)
    public void aNewPersonStartsUntickedFromWhatTheFormLeftUnticked() {
        final @NotNull Sighted sighted = Sighted.made();
        final @NotNull String users = "/projects/" + sighted.project() + "/users";

        given(Api.asAdmin()).body(newPerson(UUID.randomUUID().toString())).post(users).then().statusCode(400).body("code", equalTo("unknownTable"));
        final @NotNull String added = given(Api.asAdmin()).body(newPerson(sighted.group())).post(users).then().statusCode(201).extract().path("id");

        final @NotNull JsonPath listed = given(Api.asAdmin()).get(users).then().statusCode(200).extract().jsonPath();
        assertEquals(listed.getList("find { it.id == '" + added + "' }.projects[0].unseenTables"), List.of(sighted.group()));
        sighted.untick(sighted.group());
        sighted.tick(sighted.group());
        final @NotNull List<String> said = History.said(Api.asAdmin(), sighted.project(), "");
        assertTrue(said.contains("taken tableSight الأعمدة") && said.contains("given tableSight الأعمدة"), "«سجل المشروع»: " + said);
    }

    /** A new person's form, holding RecordEntries and unticked from unseen. */
    private static @NotNull Map<String, Object> newPerson(final @NotNull String unseen) {
        final @NotNull String tag = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        return Map.of("displayName", "Person " + tag, "username", "person" + tag, "mobile", "", "email", "person" + tag + "@redix.test",
                "temporaryPassword", People.TEMPORARY, "permissions", List.of("recordEntries"), "roleName", "", "partnerIds", List.of(),
                "unseenTableIds", List.of(unseen));
    }

    @Test(description = "The tables list holds every table for a manager and the seen ones for anyone else", testName = "7aba3afe-56ff-4e8c-bb3a-46bb92e1c041", priority = 7)
    public void theTablesListHoldsEveryTableForAManagerAndTheSeenOnesForAnyoneElse() {
        final @NotNull Sighted sighted = Sighted.made();
        final @NotNull String project = "/projects/" + sighted.project();
        sighted.untick(sighted.group());
        final @NotNull String manager = given(Api.asAdmin()).get(project + "/users").then().statusCode(200).extract()
                .path("find { it.projects[0].permissions.contains('manageUsers') }.id");

        given(Api.asAdmin()).delete(project + "/users/" + manager + "/tables/" + sighted.group()).then().statusCode(200);

        final @NotNull List<String> persons = given(sighted.person().asked()).get(project + "/tables").then().statusCode(200).extract().jsonPath()
                .getList("collectMany { it.items }.collectMany { it.tables }.id");
        final @NotNull List<String> managers = given(Api.asAdmin()).get(project + "/tables").then().statusCode(200).extract().jsonPath()
                .getList("collectMany { it.items }.collectMany { it.tables }.id");
        assertTrue(persons.contains(sighted.general()) && !persons.contains(sighted.group()), "the person's tables list an unseen group");
        assertTrue(managers.containsAll(List.of(sighted.general(), sighted.group())), "a manager unticked from the group no longer lists it");
        assertEquals(given(sighted.person().asked()).get("/projects").then().statusCode(200).extract()
                .path("find { it.id == '" + sighted.project() + "' }.unseenTables"), List.of(sighted.group()));
    }

    @Test(description = "A contractors payment is its contracts to see", testName = "9f6f7e01-ad49-4499-a118-c3a7798e2045", priority = 8)
    public void aContractorsPaymentIsItsContractsToSee() {
        final @NotNull Sighted sighted = Sighted.made();
        final @NotNull String item = Structure.itemId(sighted.project(), Structure.CONTRACTOR_PAYMENTS);
        final @NotNull String contract = Contracts.add(sighted.project(), item, "Contractor " + UUID.randomUUID().toString().substring(0, 8));
        final @NotNull String payment = Entries.payment(sighted.project(), item, contract, 300, false);
        Entries.submit(sighted.project(), payment);
        Entries.approve(sighted.project(), payment);
        final @NotNull String report = "/projects/" + sighted.project() + "/reports/contracts/" + contract;

        sighted.untick(contract);

        assertFalse(sighted.listed().contains(payment), "the person lists the payment of a contract they do not see");
        given(sighted.person().asked()).get(report).then().statusCode(404);
        sighted.tick(contract);
        assertTrue(sighted.listed().contains(payment), "ticked again, the person does not list the payment");
        given(sighted.person().asked()).get(report).then().statusCode(200);
    }
}
