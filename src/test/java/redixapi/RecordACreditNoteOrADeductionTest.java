// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Entries;
import redix.support.Projects;
import redix.support.Structure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;

public class RecordACreditNoteOrADeductionTest {
    /** A purchase into the template's item of purchases, typed as amount with the entry type named choice, شراء or مرتجع, or none for "". */
    private static @NotNull Map<String, Object> typed(final @NotNull String projectId, final int amount, final @NotNull String choice) {
        final @NotNull String item = Structure.itemId(projectId, Structure.PURCHASES);
        final @NotNull Map<String, Object> body = Entries.purchaseOf(projectId, item);
        final @NotNull Map<String, Object> values = new HashMap<>();
        if (!choice.isEmpty()) {
            values.put("entryType", choiceId(projectId, item, choice));
        }
        body.putAll(Map.of("amount", amount, "columnValues", values));
        return body;
    }

    private static @NotNull String choiceId(final @NotNull String projectId, final @NotNull String itemId, final @NotNull String choice) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/items/" + itemId + "/columns").then().statusCode(200).extract()
                .path("find { it.key == 'entryType' }.choices.find { it.name == '" + choice + "' }.id");
    }

    private static @NotNull Response record(final @NotNull String projectId, final @NotNull Map<String, Object> body) {
        return Entries.record(Api.asAdmin(), projectId, body);
    }

    private static @NotNull String approved(final @NotNull String projectId, final @NotNull Map<String, Object> body) {
        final @NotNull String id = record(projectId, body).then().statusCode(201).extract().path("id");
        Entries.submit(projectId, id);
        Entries.approve(projectId, id);
        return id;
    }

    private static double itemTotal(final @NotNull String projectId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/reports/items/" + Structure.itemId(projectId, Structure.PURCHASES)).then().statusCode(200).extract()
                .jsonPath().getDouble("total");
    }

    private static double amountOf(final @NotNull Response answer) {
        return answer.then().statusCode(201).extract().jsonPath().getDouble("amount");
    }

    @Test(description = "A credit note is a negative total of its own that takes from the item", testName = "c81ad3fa-f897-4e6d-9930-f3be3b670831", priority = 1)
    public void aCreditNoteIsANegativeTotalOfItsOwnThatTakesFromTheItem() {
        final @NotNull String project = Projects.approved();
        approved(project, typed(project, 1000, "شراء"));
        final @NotNull String credit = approved(project, typed(project, 200, "مرتجع"));
        assertEquals(given(Api.asAdmin()).get("/projects/" + project + "/entries/" + credit).then().extract().jsonPath().getDouble("entry.amount"), -200.0);
        assertEquals(itemTotal(project), 800.0);
    }

    @Test(description = "A purchases naw al qayd is a list of shira plus and murtaja minus", testName = "88b1ac1b-2ec2-4518-ab01-c7b8208a1f3e", priority = 2)
    public void aPurchasesNawAlQaydIsAListOfShiraPlusAndMurtajaMinus() {
        final @NotNull String project = Projects.approved();
        final @NotNull List<String> choices = given(Api.asAdmin()).get("/projects/" + project + "/default-columns/purchases").then().statusCode(200).extract()
                .path("find { it.key == 'entryType' }.choices.collect { it.name + ' ' + it.sign }");
        assertEquals(choices, List.of("شراء plus", "مرتجع minus"));
        assertEquals(given(Api.asAdmin()).get("/projects/" + project + "/default-columns/purchases").then().extract().path("find { it.key == 'entryType' }.isCore"), true);
    }

    @Test(description = "The sign of the choice decides the totals direction and no choice counts it as typed", testName = "d50c1bf5-06b2-4288-9ca3-3692675543af", priority = 3)
    public void theSignOfTheChoiceDecidesTheTotalsDirectionAndNoChoiceCountsItAsTyped() {
        final @NotNull String project = Projects.approved();
        assertEquals(amountOf(record(project, typed(project, 200, "شراء"))), 200.0);
        assertEquals(amountOf(record(project, typed(project, 200, "مرتجع"))), -200.0);
        assertEquals(amountOf(record(project, typed(project, 200, ""))), 200.0);
    }

    @Test(description = "A choice not of the list is refused", testName = "f3b67662-2b4d-4ba9-9222-bdda09c8243f", priority = 4)
    public void aChoiceNotOfTheListIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = typed(project, 200, "");
        body.put("columnValues", Map.of("entryType", UUID.randomUUID().toString()));
        record(project, body).then().statusCode(400).body("code", equalTo("invalidColumnValue")).body("columnKey", equalTo("entryType"));
    }

    @Test(description = "Entry type and movement type lists_every entry keeps its figure and takes the choice its sign says", testName = "4305b125-739e-4a78-8c73-38dde8268c4b", priority = 5, enabled = false)
    public void entryTypeAndMovementTypeListsEveryEntryKeepsItsFigureAndTakesTheChoiceItsSignSays() {
        throw new AssertionError("about the migration that gave every purchase kept before the list its choice, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "A credit note needs no bill and is not held to what any bill counted", testName = "798efda8-f756-4e89-b019-16dbfe70846e", priority = 6)
    public void aCreditNoteNeedsNoBillAndIsNotHeldToWhatAnyBillCounted() {
        final @NotNull String project = Projects.approved();
        approved(project, typed(project, 5000, "مرتجع"));
        assertEquals(itemTotal(project), -5000.0);
    }

    @Test(description = "A credit note is approved alone wherever its bill stands", testName = "f4510b08-ff2b-4134-9b41-6d7f59096337", priority = 7)
    public void aCreditNoteIsApprovedAloneWhereverItsBillStands() {
        final @NotNull String project = Projects.approved();
        final @NotNull String bill = record(project, typed(project, 1000, "شراء")).then().statusCode(201).extract().path("id");
        approved(project, typed(project, 300, "مرتجع"));
        assertEquals(given(Api.asAdmin()).get("/projects/" + project + "/entries/" + bill).then().extract().path("entry.status"), "draft");
        assertEquals(itemTotal(project), -300.0);
    }

    @Test(description = "What a credit note could return is no longer asked", testName = "22cb7f96-11c3-4b70-a1ac-ebc5807388fc", priority = 8)
    public void whatACreditNoteCouldReturnIsNoLongerAsked() {
        final @NotNull String project = Projects.approved();
        approved(project, typed(project, 100, "شراء"));
        approved(project, typed(project, 900, "مرتجع"));
        assertEquals(itemTotal(project), -800.0);
    }

    @Test(description = "A credit note is typed as a plain amount", testName = "8c510c74-17f7-41b1-ac6e-8bc6e2ccd0c4", priority = 9)
    public void aCreditNoteIsTypedAsAPlainAmount() {
        final @NotNull String project = Projects.approved();
        assertEquals(amountOf(record(project, typed(project, 250, "مرتجع"))), -250.0);
        record(project, typed(project, -250, "مرتجع")).then().statusCode(400).body("code", equalTo("invalidAmount"));
    }

    @Test(description = "A deduction of136 reduces the item total and says why in its notes", testName = "1de2e5a0-fefa-48d4-8ffa-9891030647eb", priority = 10)
    public void aDeductionOf136ReducesTheItemTotalAndSaysWhyInItsNotes() {
        final @NotNull String project = Projects.approved();
        approved(project, typed(project, 1000, "شراء"));
        final @NotNull Map<String, Object> deduction = typed(project, -136, "");
        deduction.put("description", "");
        record(project, deduction).then().statusCode(400).body("code", equalTo("deductionNeedsDescription"));
        deduction.putAll(Map.of("id", UUID.randomUUID().toString(), "description", "Late delivery penalty"));
        approved(project, deduction);
        assertEquals(itemTotal(project), 864.0);
    }

    @Test(description = "A finished project records no credit note", testName = "5bdbdae0-b979-4458-98d3-6bbcfdcb4cf3", priority = 11)
    public void aFinishedProjectRecordsNoCreditNote() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> credit = typed(project, 200, "مرتجع");
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);
        record(project, credit).then().statusCode(409).body("code", equalTo("projectFinished"));
    }
}
