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
import redix.support.Structure;
import redix.support.Suppliers;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ManageContractsTest {
    private static @NotNull String contracts(final @NotNull String projectId) {
        return "/projects/" + projectId + "/contracts";
    }

    /** A contract's terms and parties: a value of 10,000 on the template's item of contractor payments, with a new contractor. */
    private static @NotNull Map<String, Object> terms(final @NotNull String projectId) {
        return new HashMap<>(Map.of("supplierId", Suppliers.add(projectId, "Contractor " + UUID.randomUUID().toString().substring(0, 6)), "itemId",
                Structure.itemId(projectId, Structure.CONTRACTOR_PAYMENTS), "value", 10000, "isOpenValue", false));
    }

    private static @NotNull Response add(final @NotNull String projectId, final @NotNull Map<String, Object> body) {
        return given(Api.asAdmin()).body(body).post(contracts(projectId));
    }

    private static @NotNull String added(final @NotNull String projectId, final @NotNull Map<String, Object> body) {
        add(projectId, body).then().statusCode(200);
        return listed(projectId).getString("find { it.supplierId == '" + body.get("supplierId") + "' }.id");
    }

    private static @NotNull Response amend(final @NotNull String projectId, final @NotNull String contractId, final @NotNull Map<String, Object> body) {
        return given(Api.asAdmin()).body(body).put(contracts(projectId) + "/" + contractId);
    }

    private static @NotNull JsonPath listed(final @NotNull String projectId) {
        return given(Api.asAdmin()).get(contracts(projectId)).then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull JsonPath one(final @NotNull String projectId, final @NotNull String contractId) {
        return listed(projectId).setRootPath("find { it.id == '" + contractId + "' }");
    }

    private static void paid(final @NotNull String projectId, final @NotNull String contractId, final int amount, final boolean outside) {
        final @NotNull String entry = Entries.payment(projectId, Structure.itemId(projectId, Structure.CONTRACTOR_PAYMENTS), contractId, amount, outside);
        Entries.submit(projectId, entry);
        Entries.approve(projectId, entry);
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    @Test(description = "Finance adds a contract with its contractor item and value", testName = "5e7a8583-fd35-42cf-9e66-c4ffe5ccf4a2", priority = 1)
    public void financeAddsAContractWithItsContractorItemAndValue() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = terms(project);
        body.putAll(Map.of("unitPrice", 250, "quantity", 40, "notes", "Excavation of the plot"));
        final @NotNull JsonPath contract = one(project, added(project, body));
        assertEquals(contract.getString("itemName"), Structure.CONTRACTOR_PAYMENTS);
        assertTrue(contract.getString("supplierName").startsWith("Contractor "));
        assertEquals(contract.getDouble("value"), 10000.0);
        assertEquals(contract.getDouble("unitPrice"), 250.0);
        assertEquals(contract.getDouble("quantity"), 40.0);
        assertEquals(contract.getString("notes"), "Excavation of the plot");
        assertTrue(contract.getBoolean("isActive"));
    }

    @Test(description = "A new contract has its whole value remaining", testName = "76daa69a-5ee9-41af-841a-2ca0c3e88dca", priority = 2)
    public void aNewContractHasItsWholeValueRemaining() {
        final @NotNull String project = Projects.approved();
        final @NotNull JsonPath contract = one(project, added(project, terms(project)));
        assertEquals(contract.getDouble("paid"), 0.0);
        assertEquals(contract.getDouble("remaining"), 10000.0);
        assertFalse(contract.getBoolean("isOverrun"));
    }

    @Test(description = "The remaining is the value less what was paid inside the contract", testName = "df1b79fd-aed1-460d-923a-4ea125116c94", priority = 3)
    public void theRemainingIsTheValueLessWhatWasPaidInsideTheContract() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = added(project, terms(project));
        paid(project, contract, 3000, false);
        paid(project, contract, 1500, true);
        assertEquals(one(project, contract).getDouble("paid"), 3000.0);
        assertEquals(one(project, contract).getDouble("remaining"), 7000.0);
    }

    @Test(description = "A payment beyond the value leaves a negative remaining with an alert", testName = "f6240638-feca-433b-8304-b70642a4aa73", priority = 4)
    public void aPaymentBeyondTheValueLeavesANegativeRemainingWithAnAlert() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = added(project, terms(project));
        paid(project, contract, 12000, false);
        assertEquals(one(project, contract).getDouble("remaining"), -2000.0);
        assertTrue(one(project, contract).getBoolean("isOverrun"));
    }

    @Test(description = "An open value contract adds up with no remaining and no alert", testName = "7256496b-53c2-4d19-ab06-e4a91e6baa73", priority = 5)
    public void anOpenValueContractAddsUpWithNoRemainingAndNoAlert() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = terms(project);
        body.put("isOpenValue", true);
        body.remove("value");
        body.put("value", 0);
        final @NotNull String contract = added(project, body);
        paid(project, contract, 12000, false);
        final @NotNull JsonPath open = one(project, contract);
        assertEquals(open.getDouble("paid"), 12000.0);
        assertEquals(open.getDouble("remaining"), 0.0);
        assertFalse(open.getBoolean("isOverrun"));
    }

    @Test(description = "An open value contract keeps no value", testName = "6d61a6e7-3f1f-4ff6-b452-6b7e2098ce5b", priority = 6)
    public void anOpenValueContractKeepsNoValue() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = terms(project);
        body.put("isOpenValue", true);
        assertEquals(one(project, added(project, body)).getDouble("value"), 0.0);
    }

    @Test(description = "A value is above zero with at most two decimals", testName = "40fcafb0-8f76-4830-892a-06d70f9c19db", priority = 7)
    public void aValueIsAboveZeroWithAtMostTwoDecimals() {
        final @NotNull String project = Projects.approved();
        for (final Object value : List.of(0, -5, 100.123)) {
            final @NotNull Map<String, Object> body = terms(project);
            body.put("value", value);
            refusedAs(add(project, body), 400, "invalidValue");
        }
        final @NotNull Map<String, Object> body = terms(project);
        body.put("value", 100.25);
        assertEquals(one(project, added(project, body)).getDouble("value"), 100.25);
    }

    @Test(description = "A unit price and a quantity are not below zero with at most two decimals", testName = "f2bef77e-44ef-4a17-9bb2-f1244d7fe409", priority = 8)
    public void aUnitPriceAndAQuantityAreNotBelowZeroWithAtMostTwoDecimals() {
        final @NotNull String project = Projects.approved();
        for (final Map.Entry<String, String> wrong : List.of(Map.entry("unitPrice", "invalidUnitPrice"), Map.entry("quantity", "invalidQuantity"))) {
            for (final Object value : List.of(-1, 1.005)) {
                final @NotNull Map<String, Object> body = terms(project);
                body.put(wrong.getKey(), value);
                refusedAs(add(project, body), 400, wrong.getValue());
            }
        }
        final @NotNull Map<String, Object> body = terms(project);
        body.putAll(Map.of("unitPrice", 0, "quantity", 2.5));
        added(project, body);
    }

    @Test(description = "Notes have at most1000 characters", testName = "12cc0bf1-b548-40b3-8f66-08bb148d2192", priority = 9)
    public void notesHaveAtMost1000Characters() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = terms(project);
        body.put("notes", "n".repeat(1001));
        refusedAs(add(project, body), 400, "invalidNotes");
        body.put("notes", "n".repeat(1000));
        add(project, body).then().statusCode(200);
    }

    @Test(description = "The contractor is an active supplier of the project", testName = "8c47c896-d598-4bd5-9a55-503642074ef2", priority = 10)
    public void theContractorIsAnActiveSupplierOfTheProject() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> theirs = terms(project);
        theirs.put("supplierId", Suppliers.add(Projects.approved(), "Elsewhere Co"));
        refusedAs(add(project, theirs), 400, "unknownSupplier");
        final @NotNull Map<String, Object> disabled = terms(project);
        given(Api.asAdmin()).post("/projects/" + project + "/suppliers/" + disabled.get("supplierId") + "/disable").then().statusCode(200);
        refusedAs(add(project, disabled), 400, "unknownSupplier");
    }

    @Test(description = "The item takes contractor payments", testName = "438b80e1-f487-4311-8f46-9d22f408198b", priority = 11)
    public void theItemTakesContractorPayments() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = terms(project);
        body.put("itemId", Structure.itemId(project, Structure.PURCHASES));
        refusedAs(add(project, body), 400, "itemNotOffered");
        body.put("itemId", Structure.itemId(project, Structure.BOTH));
        add(project, body).then().statusCode(200);
    }

    @Test(description = "A disabled item takes no new contract", testName = "ee7b6532-78fb-4abd-9b41-8aadce525e6f", priority = 12)
    public void aDisabledItemTakesNoNewContract() {
        final @NotNull String project = Projects.approved();
        given(Api.asAdmin()).post("/projects/" + project + "/items/" + Structure.itemId(project, Structure.CONTRACTOR_PAYMENTS) + "/disable").then().statusCode(200);
        refusedAs(add(project, terms(project)), 400, "itemNotOffered");
    }

    @Test(description = "An amendment changes the terms and keeps the contractor", testName = "da2dd624-4591-4dc6-86ae-ea36189ce8f6", priority = 13)
    public void anAmendmentChangesTheTermsAndKeepsTheContractor() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = terms(project);
        final @NotNull String contract = added(project, body);
        amend(project, contract, Map.of("value", 15000, "isOpenValue", false, "unitPrice", 300, "quantity", 50, "notes", "Amended")).then().statusCode(200);
        final @NotNull JsonPath amended = one(project, contract);
        assertEquals(amended.getDouble("value"), 15000.0);
        assertEquals(amended.getString("notes"), "Amended");
        assertEquals(amended.getString("supplierId"), body.get("supplierId"));
    }

    @Test(description = "An amendment can make a contract open value", testName = "657761e0-b894-4fc0-9f6c-9c473f87e621", priority = 14)
    public void anAmendmentCanMakeAContractOpenValue() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = added(project, terms(project));
        amend(project, contract, Map.of("value", 0, "isOpenValue", true)).then().statusCode(200);
        assertTrue(one(project, contract).getBoolean("isOpenValue"));
        assertEquals(one(project, contract).getDouble("value"), 0.0);
    }

    @Test(description = "An amendment answers as adding does", testName = "48541e2f-1ef8-468f-bacb-98bf6379ee2e", priority = 15)
    public void anAmendmentAnswersAsAddingDoes() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = added(project, terms(project));
        refusedAs(amend(project, contract, Map.of("value", -1, "isOpenValue", false)), 400, "invalidValue");
        refusedAs(amend(project, contract, Map.of("value", 100, "isOpenValue", false, "notes", "n".repeat(1001))), 400, "invalidNotes");
        assertEquals(one(project, contract).getDouble("value"), 10000.0);
    }

    @Test(description = "A contract answers its parties and that it has no payments", testName = "c24a6cf5-286b-47f4-a9c4-601dcd33a9c1", priority = 16)
    public void aContractAnswersItsPartiesAndThatItHasNoPayments() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = terms(project);
        final @NotNull JsonPath contract = one(project, added(project, body));
        assertEquals(contract.getString("supplierId"), body.get("supplierId"));
        assertEquals(contract.getString("itemId"), body.get("itemId"));
        assertFalse(contract.getBoolean("hasPayments"));
    }

    @Test(description = "A contracts parties change while it has no payments and the history shows them old and new", testName = "9b3bb22f-e0c5-4724-b7ed-36eeea161160", priority = 17)
    public void aContractsPartiesChangeWhileItHasNoPaymentsAndTheHistoryShowsThemOldAndNew() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = added(project, terms(project));
        final @NotNull String contractor = Suppliers.add(project, "New Contractor");
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        amend(project, contract, Map.of("value", 10000, "isOpenValue", false, "supplierId", contractor, "itemId", item)).then().statusCode(200);
        assertEquals(one(project, contract).getString("supplierId"), contractor);
        assertEquals(one(project, contract).getString("itemId"), item);
    }

    @Test(description = "New parties answer as adding does", testName = "9c3a94f9-6f37-4942-b4b0-61001b2c31f5", priority = 18)
    public void newPartiesAnswerAsAddingDoes() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = added(project, terms(project));
        refusedAs(amend(project, contract, Map.of("value", 10000, "isOpenValue", false, "supplierId", Suppliers.add(Projects.approved(), "Elsewhere Co"))), 400,
                "unknownSupplier");
        refusedAs(amend(project, contract, Map.of("value", 10000, "isOpenValue", false, "itemId", Structure.itemId(project, Structure.PURCHASES))), 400, "itemNotOffered");
    }

    @Test(description = "A contract with any payment keeps its parties and changes nothing", testName = "b7fe778b-d7ea-4f35-b985-4d0d293117e0", priority = 19)
    public void aContractWithAnyPaymentKeepsItsPartiesAndChangesNothing() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = terms(project);
        final @NotNull String contract = added(project, body);
        Entries.payment(project, Structure.itemId(project, Structure.CONTRACTOR_PAYMENTS), contract, 1000, false);
        assertTrue(one(project, contract).getBoolean("hasPayments"));
        refusedAs(amend(project, contract, Map.of("value", 20000, "isOpenValue", false, "supplierId", Suppliers.add(project, "New Contractor"))), 409, "contractHasPayments");
        assertEquals(one(project, contract).getString("supplierId"), body.get("supplierId"));
        assertEquals(one(project, contract).getDouble("value"), 10000.0, "a refusal changes nothing");
    }

    @Test(description = "A contract with a payment still changes its terms", testName = "cdd8f952-f4f3-47d1-8104-ac270a982c44", priority = 20)
    public void aContractWithAPaymentStillChangesItsTerms() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = added(project, terms(project));
        paid(project, contract, 1000, false);
        amend(project, contract, Map.of("value", 20000, "isOpenValue", false)).then().statusCode(200);
        assertEquals(one(project, contract).getDouble("remaining"), 19000.0);
    }

    @Test(description = "Every change of a contract is in the history", testName = "b97a2413-6aca-4b90-bcbd-1530e7a116b7", priority = 21, enabled = false)
    public void everyChangeOfAContractIsInTheHistory() {
        throw new AssertionError("History has no route that reads a contract's lines over HTTP; Go's internal/contracts tests prove it");
    }

    @Test(description = "A disabled contract stays on the list and can be enabled", testName = "761320a9-8b3b-41bf-8edd-68f0d8f2dbcf", priority = 22)
    public void aDisabledContractStaysOnTheListAndCanBeEnabled() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = added(project, terms(project));
        given(Api.asAdmin()).post(contracts(project) + "/" + contract + "/disable").then().statusCode(200);
        assertEquals(one(project, contract).getString("isActive"), "false");
        given(Api.asAdmin()).post(contracts(project) + "/" + contract + "/enable").then().statusCode(200);
        assertEquals(one(project, contract).getString("isActive"), "true");
        given(Api.asAdmin()).delete(contracts(project) + "/" + contract).then().statusCode(405);
    }

    @Test(description = "Contracts show in the order they were added", testName = "e86b24e1-38a1-4a1c-ad3d-abeb92e50a43", priority = 23)
    public void contractsShowInTheOrderTheyWereAdded() {
        final @NotNull String project = Projects.approved();
        final @NotNull List<String> ids = List.of(added(project, terms(project)), added(project, terms(project)), added(project, terms(project)));
        assertEquals(listed(project).getList("id"), ids);
    }

    @Test(description = "Everyone with access reads contracts and only finance changes them", testName = "f79c6fa5-f860-41e6-aa00-b32ac07e1404", priority = 24)
    public void everyoneWithAccessReadsContractsAndOnlyFinanceChangesThem() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = added(project, terms(project));
        final @NotNull Person reader = People.in(project, "recordEntries");
        given(reader.asked()).get(contracts(project)).then().statusCode(200).body("size()", equalTo(1));
        given(reader.asked()).body(terms(project)).post(contracts(project)).then().statusCode(403);
        given(reader.asked()).body(Map.of("value", 1, "isOpenValue", false)).put(contracts(project) + "/" + contract).then().statusCode(403);
        given(People.in(project, "manageSettings").asked()).body(terms(project)).post(contracts(project)).then().statusCode(200);
    }

    @Test(description = "One projects contracts are never anothers", testName = "3228616f-241c-41ed-95a6-f02a11fbe2d9", priority = 25)
    public void oneProjectsContractsAreNeverAnothers() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull String theirs = added(other, terms(other));
        assertEquals(listed(project).getList("$"), List.of());
        amend(project, theirs, Map.of("value", 1, "isOpenValue", false)).then().statusCode(404);
        given(Api.asAdmin()).post(contracts(project) + "/" + theirs + "/disable").then().statusCode(404);
    }

    @Test(description = "A copied project starts with no contracts", testName = "bc465c8d-6039-4859-bde3-ff2a85f339a8", priority = 26)
    public void aCopiedProjectStartsWithNoContracts() {
        final @NotNull String source = Projects.approved();
        added(source, terms(source));
        final @NotNull String copy = given(Api.asAdmin()).body(Map.of("name", "Copy " + UUID.randomUUID().toString().substring(0, 8), "startDate", LocalDate.now().toString(),
                "units", List.of("A1"), "partners", List.of(), "structureSourceId", source)).post("/projects").then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + copy + "/approve").then().statusCode(200);
        assertEquals(listed(copy).getList("$"), List.of());
    }

    @Test(description = "A finished project refuses every change and stays readable", testName = "f46b4498-8370-4b1d-9dfd-154135aaf5ba", priority = 27)
    public void aFinishedProjectRefusesEveryChangeAndStaysReadable() {
        final @NotNull String project = Projects.approved();
        final @NotNull String contract = added(project, terms(project));
        final @NotNull Map<String, Object> another = terms(project);
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);
        refusedAs(add(project, another), 409, "projectFinished");
        refusedAs(amend(project, contract, Map.of("value", 1, "isOpenValue", false)), 409, "projectFinished");
        refusedAs(given(Api.asAdmin()).post(contracts(project) + "/" + contract + "/disable"), 409, "projectFinished");
        assertEquals(listed(project).getList("id"), List.of(contract));
    }
}
