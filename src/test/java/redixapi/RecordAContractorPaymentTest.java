// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Entries;
import redix.support.Files;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;
import redix.support.Structure;
import redix.support.Suppliers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class RecordAContractorPaymentTest {
    private static @NotNull String item(final @NotNull String projectId) {
        return Structure.itemId(projectId, Structure.CONTRACTOR_PAYMENTS);
    }

    /** A contract of value on the template's item of contractor payments, or an open one for a value of 0. */
    private static @NotNull String contract(final @NotNull String projectId, final @NotNull String itemId, final int value) {
        final @NotNull String supplier = Suppliers.add(projectId, "Contractor " + UUID.randomUUID().toString().substring(0, 6));
        given(Api.asAdmin()).body(Map.of("itemId", itemId, "supplierId", supplier, "value", value, "isOpenValue", value == 0)).post("/projects/" + projectId + "/contracts").then()
                .statusCode(200);
        return given(Api.asAdmin()).get("/projects/" + projectId + "/contracts").then().statusCode(200).extract().path("find { it.supplierId == '" + supplier + "' }.id");
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

    private static @NotNull String waiting(final @NotNull String projectId, final @NotNull Map<String, Object> body) {
        final @NotNull String id = record(projectId, body).then().statusCode(201).extract().path("id");
        Entries.submit(projectId, id);
        return id;
    }

    private static @NotNull JsonPath contractOf(final @NotNull String projectId, final @NotNull String contractId) {
        return new JsonPath(given(Api.asAdmin()).get("/projects/" + projectId + "/contracts").then().statusCode(200).extract().asString())
                .setRootPath("find { it.id == '" + contractId + "' }");
    }

    private static @NotNull JsonPath read(final @NotNull String projectId, final @NotNull String entryId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/entries/" + entryId).then().statusCode(200).extract().jsonPath();
    }

    private static double itemTotal(final @NotNull String projectId, final @NotNull String itemId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/reports/items/" + itemId).then().statusCode(200).extract().jsonPath().getDouble("total");
    }

    private static @NotNull String generalSubgroupOf(final @NotNull String projectId, final @NotNull String itemId) {
        return Structure.of(projectId).getString("collectMany { it.items }.find { it.id == '" + itemId + "' }.subgroups.find { it.name == '' }.id");
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    @Test(description = "A payment goes under its items general subgroup even when the item has named ones", testName = "e0f45690-43cc-4a7a-aa66-881d9d64b7f6", priority = 1)
    public void aPaymentGoesUnderItsItemsGeneralSubgroupEvenWhenTheItemHasNamedOnes() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        Structure.addSubgroup(project, item, "Villa 1");
        final @NotNull String payment = record(project, Entries.paymentOf(project, item, contract(project, item, 10000), 1000)).then().statusCode(201).extract().path("id");
        assertEquals(read(project, payment).getString("expense.subgroupId"), generalSubgroupOf(project, item));
    }

    @Test(description = "The payment takes its item and unit from the contract in the items general subgroup", testName = "022c0c7f-771f-46b8-b4ee-fff0b7235f97", priority = 2)
    public void thePaymentTakesItsItemAndUnitFromTheContractInTheItemsGeneralSubgroup() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = contract(project, item(project), 10000);
        final @NotNull JsonPath entry = read(project, record(project, Entries.paymentOf(project, item(project), contract, 1000)).then().statusCode(201).extract().path("id"));
        assertEquals(entry.getString("expense.itemId"), item(project));
        assertEquals(entry.getString("contractorPayment.contractId"), contract);
        assertEquals(entry.getString("expense.subgroupId"), generalSubgroupOf(project, item(project)));
    }

    @Test(description = "The payments of A34000 contract leave its remaining and one more overruns it with an alert and is saved", testName = "15ef742f-c0dd-4813-a350-465eba9fb80f", priority = 3)
    public void thePaymentsOfA34000ContractLeaveItsRemainingAndOneMoreOverrunsItWithAnAlertAndIsSaved() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = contract(project, item(project), 34000);
        approved(project, Entries.paymentOf(project, item(project), contract, 30000));
        assertEquals(contractOf(project, contract).getDouble("remaining"), 4000.0);
        approved(project, Entries.paymentOf(project, item(project), contract, 5000));
        assertEquals(contractOf(project, contract).getDouble("remaining"), -1000.0);
        assertTrue(contractOf(project, contract).getBoolean("isOverrun"));
    }

    @Test(description = "An open value contract adds up with no remaining and no alert", testName = "066600e8-84c7-4566-85cf-d318465fbafc", priority = 4)
    public void anOpenValueContractAddsUpWithNoRemainingAndNoAlert() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = contract(project, item(project), 0);
        approved(project, Entries.paymentOf(project, item(project), contract, 30000));
        approved(project, Entries.paymentOf(project, item(project), contract, 5000));
        assertEquals(contractOf(project, contract).getDouble("paid"), 35000.0);
        assertEquals(contractOf(project, contract).getDouble("remaining"), 0.0);
        assertEquals(contractOf(project, contract).getBoolean("isOverrun"), false);
    }

    @Test(description = "Only an approved payment is paid and a pending one waits beside it", testName = "68411d5a-683f-4a3a-aba9-5a6f37c43320", priority = 5)
    public void onlyAnApprovedPaymentIsPaidAndAPendingOneWaitsBesideIt() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = contract(project, item(project), 10000);
        approved(project, Entries.paymentOf(project, item(project), contract, 1000));
        waiting(project, Entries.paymentOf(project, item(project), contract, 2000));
        assertEquals(contractOf(project, contract).getDouble("paid"), 1000.0);
        assertEquals(contractOf(project, contract).getDouble("pending"), 2000.0);
        assertEquals(contractOf(project, contract).getDouble("remaining"), 9000.0);
    }

    @Test(description = "A draft and a rejected payment count for nothing", testName = "8c514f1f-d501-4834-bf04-80a7e5921157", priority = 6)
    public void aDraftAndARejectedPaymentCountForNothing() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = contract(project, item(project), 10000);
        record(project, Entries.paymentOf(project, item(project), contract, 1000)).then().statusCode(201);
        final @NotNull String rejected = waiting(project, Entries.paymentOf(project, item(project), contract, 2000));
        given(Api.asAdmin()).body(Map.of("reason", "Wrong contract")).post("/projects/" + project + "/entries/" + rejected + "/reject").then().statusCode(200);
        assertEquals(contractOf(project, contract).getDouble("paid"), 0.0);
        assertEquals(contractOf(project, contract).getDouble("pending"), 0.0);
        assertEquals(contractOf(project, contract).getDouble("remaining"), 10000.0);
    }

    @Test(description = "The alert while entering counts what waits so two pending payments cannot overrun unseen", testName = "791e7bf0-bdb0-4dd3-b084-b8ab5417b259", priority = 7)
    public void theAlertWhileEnteringCountsWhatWaitsSoTwoPendingPaymentsCannotOverrunUnseen() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = contract(project, item(project), 10000);
        waiting(project, Entries.paymentOf(project, item(project), contract, 6000));
        waiting(project, Entries.paymentOf(project, item(project), contract, 6000));
        final @NotNull JsonPath seen = contractOf(project, contract);
        assertEquals(seen.getDouble("pending"), 12000.0);
        assertTrue(seen.getDouble("remaining") - seen.getDouble("pending") < 0, "what waits goes beyond what remains: " + seen.prettify());
    }

    @Test(description = "A payment outside the contract enters the item total and leaves the remaining alone", testName = "72ee73b5-eadd-462d-8286-3929471b1720", priority = 8)
    public void aPaymentOutsideTheContractEntersTheItemTotalAndLeavesTheRemainingAlone() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = contract(project, item(project), 10000);
        final @NotNull Map<String, Object> outside = Entries.paymentOf(project, item(project), contract, 1500);
        outside.put("isOutsideContract", true);
        approved(project, outside);
        assertEquals(contractOf(project, contract).getDouble("remaining"), 10000.0);
        assertEquals(itemTotal(project, item(project)), 1500.0);
    }

    @Test(description = "A deduction of136 reduces the item total and the paid on the contract", testName = "9faf26bf-491a-4793-82f4-8fb9f206d9ba", priority = 9)
    public void aDeductionOf136ReducesTheItemTotalAndThePaidOnTheContract() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = contract(project, item(project), 10000);
        approved(project, Entries.paymentOf(project, item(project), contract, 1000));
        final @NotNull Map<String, Object> deduction = Entries.paymentOf(project, item(project), contract, -136);
        deduction.put("description", "Late delivery penalty");
        approved(project, deduction);
        assertEquals(contractOf(project, contract).getDouble("paid"), 864.0);
        assertEquals(itemTotal(project, item(project)), 864.0);
    }

    @Test(description = "A deduction says why in its notes", testName = "ff16bd59-1f5c-4423-ad22-6c99d3fd525d", priority = 10)
    public void aDeductionSaysWhyInItsNotes() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> deduction = Entries.paymentOf(project, item(project), contract(project, item(project), 10000), -136);
        deduction.put("description", "");
        refusedAs(record(project, deduction), 400, "deductionNeedsDescription");
    }

    @Test(description = "A payment is sent with no receipt", testName = "dc6608eb-c31e-4699-bfcf-b7e4f31c52db", priority = 11)
    public void aPaymentIsSentWithNoReceipt() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = Entries.paymentOf(project, item(project), contract(project, item(project), 10000), 1000);
        body.put("submit", true);
        record(project, body).then().statusCode(201).body("status", equalTo("pendingApproval"));
    }

    @Test(description = "A payment writes no invoice row", testName = "67d325c4-f464-4acc-a47f-fc1b1f4b5e7b", priority = 12)
    public void aPaymentWritesNoInvoiceRow() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = record(project, Entries.paymentOf(project, item(project), contract(project, item(project), 10000), 1000)).then().statusCode(201).extract()
                .path("id");
        assertEquals(read(project, id).getList("attachments"), List.of());
    }

    @Test(description = "The receipt is linked as a receipt and a file already on an entry is refused", testName = "79c5bda2-9a04-404f-b954-883e29fcda08", priority = 13)
    public void theReceiptIsLinkedAsAReceiptAndAFileAlreadyOnAnEntryIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = contract(project, item(project), 10000);
        final @NotNull String file = Files.upload(Api.asAdmin(), "/projects/" + project + "/attachments", "receipt.png", Files.png(2048), "image/png").then().statusCode(201)
                .extract().path("id");
        final @NotNull Map<String, Object> body = Entries.paymentOf(project, item(project), contract, 1000);
        body.put("attachmentId", file);
        final @NotNull JsonPath entry = read(project, record(project, body).then().statusCode(201).extract().path("id"));
        assertEquals(entry.getList("attachments.attachmentId"), List.of(file));
        assertEquals(entry.getString("attachments[0].role"), "receipt");
        body.put("id", UUID.randomUUID().toString());
        refusedAs(record(project, body), 409, "invoiceAlreadyAttached");
    }

    @Test(description = "A contract whose item is disabled takes no payment", testName = "bab70bb8-d386-4657-ae0f-fb635981bebe", priority = 14)
    public void aContractWhoseItemIsDisabledTakesNoPayment() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = Entries.paymentOf(project, item(project), contract(project, item(project), 10000), 1000);
        given(Api.asAdmin()).post("/projects/" + project + "/items/" + item(project) + "/disable").then().statusCode(200);
        refusedAs(record(project, body), 400, "unknownTable");
    }

    @Test(description = "Open question4_field charges an account they hold and no other", testName = "3deaf546-756c-45e0-9afa-d7f6a0fa7ac0", priority = 15)
    public void openQuestion4FieldChargesAnAccountTheyHoldAndNoOther() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person field = People.in(project, "recordEntries");
        final @NotNull Map<String, Object> body = Entries.paymentOf(project, item(project), contract(project, item(project), 10000), 1000);
        given(Api.asAdmin()).body(Map.of("type", "cashCustody", "name", "Site cash", "custodianId", field.id())).post("/projects/" + project + "/accounts").then().statusCode(200);
        refusedAs(Entries.record(field.asked(), project, body), 400, "accountNotOffered");
        body.putAll(Map.of("id", UUID.randomUUID().toString(), "accountId", given(Api.asAdmin()).get("/projects/" + project + "/accounts").then().extract()
                .path("find { it.name == 'Site cash' }.id")));
        Entries.record(field.asked(), project, body).then().statusCode(201);
    }

    @Test(description = "A required custom column is asked of a payment when sending", testName = "6d60f54e-ad74-4c59-8371-74f77020d6d8", priority = 16)
    public void aRequiredCustomColumnIsAskedOfAPaymentWhenSending() {
        final @NotNull String project = Projects.approved();
        final @NotNull String columns = "/projects/" + project + "/items/" + item(project) + "/columns";
        given(Api.asAdmin()).body(Map.of("name", "Stage", "dataType", "text", "isRequired", true, "showInReports", false, "choices", List.of())).post(columns).then().statusCode(200);
        final @NotNull String key = given(Api.asAdmin()).get(columns).then().extract().path("find { it.name == 'Stage' }.key");
        final @NotNull Map<String, Object> body = Entries.paymentOf(project, item(project), contract(project, item(project), 10000), 1000);
        body.put("submit", true);
        record(project, body).then().statusCode(400).body("code", equalTo("requiredColumnMissing")).body("columnKey", equalTo(key));
        body.putAll(Map.of("id", UUID.randomUUID().toString(), "columnValues", Map.of(key, "Foundations")));
        record(project, body).then().statusCode(201);
    }

    @Test(description = "What only a purchase holds is never asked of a payment", testName = "baa865a7-3560-4611-8176-925308194c57", priority = 17)
    public void whatOnlyAPurchaseHoldsIsNeverAskedOfAPayment() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        final @NotNull String columns = "/projects/" + project + "/items/" + item + "/columns";
        final @NotNull String quantity = given(Api.asAdmin()).get(columns).then().extract().path("find { it.key == 'quantity' }.id");
        given(Api.asAdmin()).body(Map.of("name", "العدد", "isRequired", true, "showInReports", true)).put(columns + "/" + quantity).then().statusCode(200);
        final @NotNull Map<String, Object> body = Entries.paymentOf(project, item, contract(project, item, 10000), 1000);
        body.put("submit", true);
        record(project, body).then().statusCode(201);
    }

    @Test(description = "What was typed is refused when it cannot be kept", testName = "5906c5f7-789c-4d7d-8e58-9eb338e81908", priority = 18)
    public void whatWasTypedIsRefusedWhenItCannotBeKept() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = contract(project, item(project), 10000);
        refusedAs(record(project, Entries.paymentOf(project, item(project), contract, 0)), 400, "invalidAmount");
        final @NotNull Map<String, Object> body = Entries.paymentOf(project, item(project), contract, 1000);
        body.put("description", "d".repeat(2001));
        refusedAs(record(project, body), 400, "invalidDescription");
    }

    @Test(description = "A disabled contract or one of another project is refused", testName = "4cec6292-3af8-4c38-a88a-aa1951f9a61d", priority = 19)
    public void aDisabledContractOrOneOfAnotherProjectIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = contract(project, item(project), 10000);
        given(Api.asAdmin()).post("/projects/" + project + "/contracts/" + contract + "/disable").then().statusCode(200);
        refusedAs(record(project, Entries.paymentOf(project, item(project), contract, 1000)), 400, "unknownTable");
        final @NotNull String other = Projects.approved();
        refusedAs(record(project, Entries.paymentOf(project, item(project), contract(other, item(other), 10000), 1000)), 400, "unknownTable");
    }

    @Test(description = "An empty ID is refused", testName = "e8ceaf0b-3bca-4fce-88aa-caffaa7e4fba", priority = 20)
    public void anEmptyIdIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = Entries.paymentOf(project, item(project), contract(project, item(project), 10000), 1000);
        body.put("id", "00000000-0000-0000-0000-000000000000");
        refusedAs(record(project, body), 400, "invalidEntryId");
    }

    @Test(description = "A finished project records no payment", testName = "a28736f0-73f9-4f44-a214-cc844868aa13", priority = 21)
    public void aFinishedProjectRecordsNoPayment() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = Entries.paymentOf(project, item(project), contract(project, item(project), 10000), 1000);
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);
        refusedAs(record(project, body), 409, "projectFinished");
    }

    @Test(description = "A payment is in the history", testName = "7a55a2ba-b655-4236-ab61-63cc15062e09", priority = 22, enabled = false)
    public void aPaymentIsInTheHistory() {
        throw new AssertionError("History has no route that reads an entry's own lines over HTTP; Go's internal/entries tests prove it");
    }

    @Test(description = "A payment sent again lands once", testName = "2341617c-8ce5-4de5-a6ba-a64ea6fbd42b", priority = 23)
    public void aPaymentSentAgainLandsOnce() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = Entries.paymentOf(project, item(project), contract(project, item(project), 10000), 1000);
        record(project, body).then().statusCode(201);
        record(project, body).then().statusCode(200).body("id", equalTo(body.get("id")));
        given(Api.asAdmin()).get("/projects/" + project + "/entries").then().body("findAll { it.id == '" + body.get("id") + "' }.size()", equalTo(1));
    }

    @Test(description = "Editing a waiting payment counts it once keeps the others and answers ok", testName = "047efbe2-df69-496b-8b9d-77817d3792ec", priority = 24)
    public void editingAWaitingPaymentCountsItOnceKeepsTheOthersAndAnswersOk() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = contract(project, item(project), 10000);
        waiting(project, Entries.paymentOf(project, item(project), contract, 1000));
        final @NotNull Map<String, Object> edited = Entries.paymentOf(project, item(project), contract, 2000);
        final @NotNull String id = waiting(project, edited);
        final @NotNull Map<String, Object> change = new HashMap<>(edited);
        change.put("amount", 2500);
        change.remove("submit");
        given(Api.asAdmin()).body(change).put("/projects/" + project + "/entries/" + id).then().statusCode(200);
        assertEquals(contractOf(project, contract).getDouble("pending"), 3500.0);
    }
}
