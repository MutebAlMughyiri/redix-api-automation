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
import redix.support.Structure;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class SendChangesSinceTheLastSyncTest {
    private static final @NotNull List<String> LISTS = List.of("/categories/offered?itemType=purchases", "/categories/offered?itemType=contractorPayments", "/entry-tables",
            "/suppliers", "/suppliers/search?text=", "/contracts", "/accounts", "/accounts/offered", "/payment-methods", "/payment-methods/offered", "/entries/mine");

    private static @NotNull JsonPath sync(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull String since) {
        return given(as).queryParam("since", since).get("/projects/" + projectId + "/sync").then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull String list(final @NotNull JsonPath synced, final @NotNull String address) {
        return synced.getString("lists.'" + address + "'");
    }

    @Test(description = "A first sync answers every list at the address the app reads it at", testName = "1fba9ff3-dd60-43e7-91bc-b8eeea35d7a4", priority = 1)
    public void aFirstSyncAnswersEveryListAtTheAddressTheAppReadsItAt() {
        final @NotNull String project = Projects.approved();
        final @NotNull JsonPath synced = sync(Api.asAdmin(), project, "");
        assertFalse(synced.getString("stamp").isEmpty());
        for (final String address : LISTS) {
            assertTrue(synced.getMap("lists").containsKey("/projects/" + project + address), address + " is not among " + synced.getMap("lists").keySet());
        }
        assertTrue(synced.getMap("lists").containsKey("/projects/" + project + "/items/" + Structure.itemId(project, Structure.PURCHASES) + "/columns"));
    }

    @Test(description = "Each list holds what its own read answers", testName = "ff9f2df3-413c-49e3-8874-e6b96dfb7a67", priority = 2)
    public void eachListHoldsWhatItsOwnReadAnswers() {
        final @NotNull String project = Projects.approved();
        final @NotNull JsonPath synced = sync(Api.asAdmin(), project, "");
        for (final String address : List.of("/suppliers", "/payment-methods", "/accounts", "/entry-tables")) {
            final @NotNull Object read = given(Api.asAdmin()).get("/projects/" + project + address).then().statusCode(200).extract().jsonPath().get("$");
            assertEquals(synced.get("lists.'/projects/" + project + address + "'"), read, address);
        }
    }

    @Test(description = "Nothing changed answers no lists", testName = "5590a573-405a-499c-bab6-042e599a910f", priority = 3)
    public void nothingChangedAnswersNoLists() {
        final @NotNull String project = Projects.approved();
        final @NotNull String stamp = sync(Api.asAdmin(), project, "").getString("stamp");
        final @NotNull JsonPath again = sync(Api.asAdmin(), project, stamp);
        assertEquals(again.getString("stamp"), stamp);
        assertEquals(again.getMap("lists"), Map.of());
    }

    @Test(description = "An item finance added reaches the field at the next sync", testName = "aa748b85-6e4b-4196-9e86-225cc20b777d", priority = 4)
    public void anItemFinanceAddedReachesTheFieldAtTheNextSync() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person field = People.in(project, "recordEntries");
        final @NotNull String stamp = sync(field.asked(), project, "").getString("stamp");
        given(Api.asAdmin()).body(Map.of("name", "Safety", "type", "purchases")).post("/projects/" + project + "/categories/" + Structure.categoryId(project, "الإشراف") + "/items")
                .then().statusCode(201);
        final @NotNull JsonPath next = sync(field.asked(), project, stamp);
        assertNotEquals(next.getString("stamp"), stamp);
        assertTrue(list(next, "/projects/" + project + "/categories/offered?itemType=purchases").contains("Safety"));
        assertTrue(list(next, "/projects/" + project + "/entry-tables").contains("Safety"));
    }

    @Test(description = "A row that left a list changes the stamp too", testName = "0935677e-7478-433a-a09d-adf5925174bd", priority = 5)
    public void aRowThatLeftAListChangesTheStampToo() {
        final @NotNull String project = Projects.approved();
        final @NotNull String stamp = sync(Api.asAdmin(), project, "").getString("stamp");
        given(Api.asAdmin()).post("/projects/" + project + "/items/" + Structure.itemId(project, Structure.PURCHASES) + "/disable").then().statusCode(200);
        final @NotNull JsonPath next = sync(Api.asAdmin(), project, stamp);
        assertNotEquals(next.getString("stamp"), stamp);
        assertFalse(list(next, "/projects/" + project + "/entry-tables").contains(Structure.itemId(project, Structure.PURCHASES)));
    }

    @Test(description = "The field keeps only the accounts they may charge", testName = "8b7904ed-cbdc-440e-b2af-58303d0fc0c1", priority = 6)
    public void theFieldKeepsOnlyTheAccountsTheyMayCharge() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person field = People.in(project, "recordEntries");
        given(Api.asAdmin()).body(Map.of("type", "cashCustody", "name", "Site cash", "custodianId", field.id())).post("/projects/" + project + "/accounts").then().statusCode(200);
        given(Api.asAdmin()).body(Map.of("type", "bank", "name", "Bank A")).post("/projects/" + project + "/accounts").then().statusCode(200);
        final @NotNull JsonPath synced = sync(field.asked(), project, "");
        assertEquals(synced.getList("lists.'/projects/" + project + "/accounts/offered'.name"), List.of("Site cash"));
    }

    @Test(description = "Each person keeps only the status of their own entries", testName = "2a86d2bd-fda7-4293-97c9-d31aa886f4f3", priority = 7)
    public void eachPersonKeepsOnlyTheStatusOfTheirOwnEntries() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person field = People.in(project, "recordEntries", "chargeAnyAccount");
        final @NotNull String theirs = Entries.draft(project, field.asked());
        final @NotNull String mine = Entries.draft(project);
        assertEquals(sync(field.asked(), project, "").getList("lists.'/projects/" + project + "/entries/mine'.id"), List.of(theirs));
        assertEquals(sync(Api.asAdmin(), project, "").getList("lists.'/projects/" + project + "/entries/mine'.id"), List.of(mine));
    }

    @Test(description = "Someone outside the project gets nothing", testName = "3292df03-b222-4edb-8e58-c307cf6db6c7", priority = 8)
    public void someoneOutsideTheProjectGetsNothing() {
        final @NotNull String project = Projects.approved();
        given(People.in(Projects.approved()).asked()).get("/projects/" + project + "/sync").then().statusCode(403);
        given(Api.anonymous()).get("/projects/" + project + "/sync").then().statusCode(401);
    }
}
