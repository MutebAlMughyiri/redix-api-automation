// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Contracts;
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
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RecordAPurchaseExpenseTest {
    private static final @NotNull String NOBODY = "00000000-0000-0000-0000-000000000000";

    private static @NotNull Map<String, Object> purchase(final @NotNull String projectId) {
        return Entries.purchaseOf(projectId, Structure.itemId(projectId, Structure.PURCHASES));
    }

    private static @NotNull Map<String, Object> columns(final @NotNull Map<String, Object> body) {
        final @NotNull Map<String, Object> values = new HashMap<>();
        ((Map<?, ?>) body.get("columnValues")).forEach((key, value) -> values.put((String) key, value));
        body.put("columnValues", values);
        return values;
    }

    private static @NotNull Response record(final @NotNull String projectId, final @NotNull Map<String, Object> body) {
        return Entries.record(Api.asAdmin(), projectId, body);
    }

    private static @NotNull JsonPath read(final @NotNull String projectId, final @NotNull String entryId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/entries/" + entryId).then().statusCode(200).extract().jsonPath();
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    private static void refusedOver(final @NotNull Response answer, final @NotNull String code, final @NotNull String columnKey) {
        answer.then().statusCode(400).body("code", equalTo(code)).body("columnKey", equalTo(columnKey));
    }

    private static @NotNull String column(final @NotNull String projectId, final @NotNull String itemId, final @NotNull String name, final @NotNull String dataType, final boolean required) {
        final @NotNull String columns = "/projects/" + projectId + "/items/" + itemId + "/columns";
        given(Api.asAdmin()).body(Map.of("name", name, "dataType", dataType, "isRequired", required, "showInReports", false, "choices", List.of())).post(columns).then().statusCode(200);
        return given(Api.asAdmin()).get(columns).then().statusCode(200).extract().path("find { it.name == '" + name + "' }.key");
    }

    private static @NotNull String upload(final @NotNull String projectId, final byte @NotNull [] file) {
        return Files.upload(Api.asAdmin(), "/projects/" + projectId + "/attachments", "invoice.png", file, "image/png").then().statusCode(anyOf(equalTo(200), equalTo(201))).extract().path("id");
    }

    private static @NotNull Person custodianIn(final @NotNull String projectId, final @NotNull String... permissions) {
        final @NotNull Person person = People.in(projectId, permissions);
        given(Api.asAdmin()).body(Map.of("type", "cashCustody", "name", "Custody " + person.username(), "custodianId", person.id())).post("/projects/" + projectId + "/accounts")
                .then().statusCode(200);
        return person;
    }

    private static @NotNull String accountOf(final @NotNull String projectId, final @NotNull String name) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/accounts").then().statusCode(200).extract().path("find { it.name == '" + name + "' }.id");
    }

    @Test(description = "An expense is sent with its item supplier date total account and method and no file", testName = "a083c2b2-146b-47ed-b322-625e76e5dede", priority = 1)
    public void anExpenseIsSentWithItsItemSupplierDateTotalAccountAndMethodAndNoFile() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull String supplier = Suppliers.add(project, "Cement Co");
        body.putAll(Map.of("supplierId", supplier, "submit", true));
        final @NotNull String id = record(project, body).then().statusCode(201).body("status", equalTo("pendingApproval")).body("amount", equalTo(200)).extract().path("id");
        final @NotNull JsonPath entry = read(project, id);
        assertEquals(entry.getString("expense.itemId"), body.get("itemId"));
        assertEquals(entry.getString("purchase.supplierId"), supplier);
        assertEquals(entry.getString("expense.paymentMethodId"), body.get("paymentMethodId"));
        assertEquals(entry.getString("entry.accountId"), body.get("accountId"));
        assertEquals(entry.getString("entry.entryDate"), body.get("date"));
        assertEquals(entry.getList("attachments"), List.of());
    }

    @Test(description = "An expense is its total on its one date", testName = "99e6fb57-a4e9-47b5-afb5-2d8412501c3d", priority = 2)
    public void anExpenseIsItsTotalOnItsOneDate() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull JsonPath entry = read(project, record(project, body).then().statusCode(201).extract().path("id"));
        assertEquals(entry.getDouble("entry.amount"), 200.0);
        assertEquals(entry.getString("entry.entryDate"), body.get("date"));
    }

    @Test(description = "A new expense writes no invoice row", testName = "87cf6379-88de-42b1-a6b3-a07315d709ee", priority = 3)
    public void aNewExpenseWritesNoInvoiceRow() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = record(project, purchase(project)).then().statusCode(201).extract().path("id");
        assertEquals(read(project, id).getList("attachments"), List.of());
        given(Api.asAdmin()).get("/projects/" + project + "/entries").then().body("find { it.id == '" + id + "' }.invoiceNumber", equalTo(""));
    }

    @Test(description = "A draft with no file is sent later with no file", testName = "0fba9a3b-180a-4bcd-988b-125303852a79", priority = 4)
    public void aDraftWithNoFileIsSentLaterWithNoFile() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = record(project, purchase(project)).then().statusCode(201).body("status", equalTo("draft")).extract().path("id");
        Entries.submit(project, id);
        assertEquals(read(project, id).getString("entry.status"), "pendingApproval");
        assertEquals(read(project, id).getList("attachments"), List.of());
    }

    @Test(description = "A deduction says why in its notes", testName = "fa7dee0d-04d4-46cd-a469-1c0886694371", priority = 5)
    public void aDeductionSaysWhyInItsNotes() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        body.putAll(Map.of("amount", -50, "description", ""));
        refusedAs(record(project, body), 400, "deductionNeedsDescription");
        body.putAll(Map.of("id", UUID.randomUUID().toString(), "description", "Discount on the cement"));
        record(project, body).then().statusCode(201).body("amount", equalTo(-50));
    }

    @Test(description = "Left without an amount the total is the price times the quantity", testName = "f7a0bf41-8bf9-4d48-8f06-9487f904d7d1", priority = 6)
    public void leftWithoutAnAmountTheTotalIsThePriceTimesTheQuantity() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        columns(body).putAll(Map.of("unitPrice", "125.5", "quantity", "4"));
        assertEquals(record(project, body).then().statusCode(201).extract().jsonPath().getDouble("amount"), 502.0, "the total keeps the scale of what was typed");
    }

    @Test(description = "An amount typed wins over the price and quantity which stay as typed", testName = "06abf97c-79be-4758-a6b8-9105b6516230", priority = 7)
    public void anAmountTypedWinsOverThePriceAndQuantityWhichStayAsTyped() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        columns(body).putAll(Map.of("unitPrice", "100", "quantity", "5"));
        body.put("amount", 450);
        final @NotNull String id = record(project, body).then().statusCode(201).body("amount", equalTo(450)).extract().path("id");
        final @NotNull String kept = read(project, id).getString("entry.columnValues");
        assertTrue(kept.contains("\"unitPrice\":\"100\"") && kept.contains("\"quantity\":\"5\""), kept);
    }

    @Test(description = "The unit price and the quantity are kept with the entry as every other column is", testName = "a192dda7-9f74-42a4-9fd4-f1c9d23d2eff", priority = 8)
    public void theUnitPriceAndTheQuantityAreKeptWithTheEntryAsEveryOtherColumnIs() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull String key = column(project, (String) body.get("itemId"), "Truck number", "text", false);
        columns(body).put(key, "Truck 7");
        final @NotNull String kept = read(project, record(project, body).then().statusCode(201).extract().path("id")).getString("entry.columnValues");
        assertTrue(kept.contains("\"unitPrice\":\"100\"") && kept.contains("\"quantity\":\"2\"") && kept.contains("Truck 7"), kept);
    }

    @Test(description = "A table without the unit price is refused one and takes its amount typed", testName = "ef836b23-a9df-45fd-b88c-83661ccfb3cc", priority = 9)
    public void aTableWithoutTheUnitPriceIsRefusedOneAndTakesItsAmountTyped() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.CONTRACTOR_PAYMENTS);
        final @NotNull String contract = Contracts.add(project, item, "Contractor Co");
        final @NotNull Map<String, Object> body = Entries.purchaseOf(project, Structure.itemId(project, Structure.BOTH));
        body.putAll(Map.of("itemId", item, "tableId", contract, "amount", 3000, "columnValues", Map.of("unitPrice", "100")));
        refusedOver(record(project, body), "unknownColumn", "unitPrice");
        body.putAll(Map.of("id", UUID.randomUUID().toString(), "columnValues", Map.of()));
        record(project, body).then().statusCode(201).body("amount", equalTo(3000));
    }

    @Test(description = "What was typed is refused when it cannot be kept", testName = "ea4d45c7-6d13-4331-8222-c0d7dce1bd9c", priority = 10)
    public void whatWasTypedIsRefusedWhenItCannotBeKept() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        columns(body).put("quantity", "two");
        refusedAs(record(project, body), 400, "invalidAmount");
        body.putAll(Map.of("id", UUID.randomUUID().toString(), "amount", 300));
        refusedOver(record(project, body), "invalidColumnValue", "quantity");
        body.remove("amount");
        body.put("id", UUID.randomUUID().toString());
        columns(body).put("quantity", "2");
        body.put("description", "d".repeat(2001));
        refusedAs(record(project, body), 400, "invalidDescription");
    }

    @Test(description = "Description for every entry_a product and its notes become one description and the columns take the workbooks words", testName = "12308480-a6d3-4dc4-8497-aa701fd63ad2", priority = 11, enabled = false)
    public void descriptionForEveryEntryAProductAndItsNotesBecomeOneDescriptionAndTheColumnsTakeTheWorkbooksWords() {
        throw new AssertionError("about the migration that joined a purchase's product and notes into its description, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "Column values on the entry_every value the product the price and the quantity move to the entry unchanged and the supplier is optional", testName = "fa9cc27b-adf9-4e0d-9337-6a351f4522a8", priority = 12, enabled = false)
    public void columnValuesOnTheEntryEveryValueTheProductThePriceAndTheQuantityMoveToTheEntryUnchangedAndTheSupplierIsOptional() {
        throw new AssertionError("about the migration that moved every value onto the entry, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "The purchases supplier is optional so one with none is sent and holds no supplier row", testName = "f8095045-8df8-407c-8cf9-3f35348f7b48", priority = 13)
    public void thePurchasesSupplierIsOptionalSoOneWithNoneIsSentAndHoldsNoSupplierRow() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        body.put("submit", true);
        final @NotNull String id = record(project, body).then().statusCode(201).body("status", equalTo("pendingApproval")).extract().path("id");
        assertEquals(read(project, id).getString("purchase.supplierId"), NOBODY);
    }

    @Test(description = "A purchase that names a supplier keeps its row", testName = "7bc6eeca-3174-40c9-bd69-72142250beeb", priority = 14)
    public void aPurchaseThatNamesASupplierKeepsItsRow() {
        final @NotNull String project = Projects.approved();
        final @NotNull String supplier = Suppliers.add(project, "Cement Co");
        final @NotNull Map<String, Object> body = purchase(project);
        body.put("supplierId", supplier);
        final @NotNull String id = record(project, body).then().statusCode(201).extract().path("id");
        assertEquals(read(project, id).getString("purchase.supplierId"), supplier);
        given(Api.asAdmin()).get("/projects/" + project + "/entries").then().body("find { it.id == '" + id + "' }.supplierName", equalTo("Cement Co"));
    }

    @Test(description = "A purchase with no supplier is on the VAT statement with no party and no VAT number", testName = "cf4d1bac-5159-4cc3-b466-b115d7185ebd", priority = 15, enabled = false)
    public void aPurchaseWithNoSupplierIsOnTheVatStatementWithNoPartyAndNoVatNumber() {
        throw new AssertionError("read from the VAT statement's workbook, which no JSON route answers; Go's internal/reports tests read it cell by cell");
    }

    @Test(description = "A supplier not in the register is refused", testName = "1a66df66-e9a4-41be-8153-6b78c8d18317", priority = 16)
    public void aSupplierNotInTheRegisterIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        body.put("supplierId", UUID.randomUUID().toString());
        refusedAs(record(project, body), 400, "unknownSupplier");
        body.putAll(Map.of("id", UUID.randomUUID().toString(), "supplierId", Suppliers.add(Projects.approved(), "Elsewhere Co")));
        refusedAs(record(project, body), 400, "unknownSupplier");
    }

    @Test(description = "A disabled supplier is refused", testName = "24a55d1a-016d-4dc0-b7ab-cdf4444ad5ce", priority = 17)
    public void aDisabledSupplierIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull String supplier = Suppliers.add(project, "Cement Co");
        given(Api.asAdmin()).post("/projects/" + project + "/suppliers/" + supplier + "/disable").then().statusCode(200);
        final @NotNull Map<String, Object> body = purchase(project);
        body.put("supplierId", supplier);
        refusedAs(record(project, body), 400, "unknownSupplier");
    }

    @Test(description = "Field uses their own pending supplier and not anothers", testName = "49d4aac0-c22d-4aa5-82fb-631c60ad6704", priority = 18)
    public void fieldUsesTheirOwnPendingSupplierAndNotAnothers() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person field = People.in(project, "recordEntries", "addSupplier", "chargeAnyAccount");
        final @NotNull Person other = People.in(project, "recordEntries", "addSupplier");
        final @NotNull String mine = given(field.asked()).body(Map.of("name", "Mine Co")).post("/projects/" + project + "/suppliers").then().statusCode(201).extract().path("id");
        final @NotNull String theirs = given(other.asked()).body(Map.of("name", "Theirs Co")).post("/projects/" + project + "/suppliers").then().statusCode(201).extract().path("id");
        final @NotNull Map<String, Object> body = purchase(project);
        body.put("supplierId", mine);
        Entries.record(field.asked(), project, body).then().statusCode(201);
        body.putAll(Map.of("id", UUID.randomUUID().toString(), "supplierId", theirs));
        refusedAs(Entries.record(field.asked(), project, body), 400, "unknownSupplier");
    }

    @Test(description = "Open question4_field charges an account they are a custodian of", testName = "577970b9-1a76-4995-bb0e-254e49dd6b3c", priority = 19)
    public void openQuestion4FieldChargesAnAccountTheyAreACustodianOf() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person field = custodianIn(project, "recordEntries");
        final @NotNull Map<String, Object> body = purchase(project);
        body.put("accountId", accountOf(project, "Custody " + field.username()));
        Entries.record(field.asked(), project, body).then().statusCode(201);
    }

    @Test(description = "Open question4_field cannot charge an account they are not a custodian of", testName = "99ca9e77-ed72-4329-8eea-262fbe409eb0", priority = 20)
    public void openQuestion4FieldCannotChargeAnAccountTheyAreNotACustodianOf() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person field = custodianIn(project, "recordEntries");
        final @NotNull Map<String, Object> body = purchase(project);
        body.put("accountId", accountOf(project, "Bank"));
        refusedAs(Entries.record(field.asked(), project, body), 400, "accountNotOffered");
    }

    @Test(description = "Charge any account lets finance charge any active account and not a disabled one", testName = "24729d2d-5a9b-4c5e-8687-617ece8488ad", priority = 21)
    public void chargeAnyAccountLetsFinanceChargeAnyActiveAccountAndNotADisabledOne() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person finance = People.in(project, "recordEntries", "chargeAnyAccount");
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull Person custodian = custodianIn(project, "recordEntries");
        body.put("accountId", accountOf(project, "Custody " + custodian.username()));
        Entries.record(finance.asked(), project, body).then().statusCode(201);
        given(Api.asAdmin()).post("/projects/" + project + "/accounts/" + body.get("accountId") + "/disable").then().statusCode(200);
        body.put("id", UUID.randomUUID().toString());
        refusedAs(Entries.record(finance.asked(), project, body), 400, "accountNotOffered");
    }

    @Test(description = "An item that takes no purchases or is disabled or of another project is refused", testName = "90f5a584-2956-49d2-b461-260159396c3a", priority = 22)
    public void anItemThatTakesNoPurchasesOrIsDisabledOrOfAnotherProjectIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> payments = purchase(project);
        payments.put("itemId", Structure.itemId(project, Structure.CONTRACTOR_PAYMENTS));
        refusedAs(record(project, payments), 400, "unknownTable");
        final @NotNull Map<String, Object> disabled = purchase(project);
        given(Api.asAdmin()).post("/projects/" + project + "/items/" + disabled.get("itemId") + "/disable").then().statusCode(200);
        refusedAs(record(project, disabled), 400, "unknownTable");
        final @NotNull String other = Projects.approved();
        final @NotNull Map<String, Object> theirs = purchase(Projects.approved());
        final @NotNull Map<String, Object> body = Entries.purchaseOf(other, Structure.itemId(other, Structure.BOTH));
        body.putAll(Map.of("itemId", theirs.get("itemId"), "tableId", theirs.get("tableId")));
        refusedAs(record(other, body), 400, "unknownTable");
    }

    @Test(description = "A disabled payment method is refused", testName = "0835866a-c43b-4397-9f71-8edddf8f7ad3", priority = 23)
    public void aDisabledPaymentMethodIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        given(Api.asAdmin()).post("/projects/" + project + "/payment-methods/" + body.get("paymentMethodId") + "/disable").then().statusCode(200);
        refusedAs(record(project, body), 400, "unknownPaymentMethod");
    }

    @Test(description = "A required custom column is asked only of its item when sending", testName = "32c25c4f-082a-4c13-a043-505dc8e21ee1", priority = 24)
    public void aRequiredCustomColumnIsAskedOnlyOfItsItemWhenSending() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull String key = column(project, (String) body.get("itemId"), "Truck number", "text", true);
        body.put("submit", true);
        refusedOver(record(project, body), "requiredColumnMissing", key);
        final @NotNull Map<String, Object> elsewhere = Entries.purchaseOf(project, Structure.itemId(project, "الخرسانة"));
        elsewhere.put("submit", true);
        record(project, elsewhere).then().statusCode(201);
    }

    @Test(description = "A draft can wait for a required column", testName = "2dbe94f0-a27a-4ee1-990c-ea352730b0ad", priority = 25)
    public void aDraftCanWaitForARequiredColumn() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        column(project, (String) body.get("itemId"), "Truck number", "text", true);
        record(project, body).then().statusCode(201).body("status", equalTo("draft"));
    }

    @Test(description = "A built in column made required is asked when sending", testName = "6394fb7e-4d0e-4043-84f4-25b154b1fea0", priority = 26)
    public void aBuiltInColumnMadeRequiredIsAskedWhenSending() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull String columns = "/projects/" + project + "/items/" + body.get("itemId") + "/columns";
        final @NotNull String quantity = given(Api.asAdmin()).get(columns).then().extract().path("find { it.key == 'quantity' }.id");
        given(Api.asAdmin()).body(Map.of("name", "العدد", "isRequired", true, "showInReports", true)).put(columns + "/" + quantity).then().statusCode(200);
        body.putAll(Map.of("submit", true, "amount", 300, "columnValues", Map.of("unitPrice", "100")));
        refusedOver(record(project, body), "requiredColumnMissing", "quantity");
    }

    @Test(description = "Custom values are stored with the entry and an empty one is no value", testName = "b44b32c4-463d-4210-9378-90df171fff25", priority = 27)
    public void customValuesAreStoredWithTheEntryAndAnEmptyOneIsNoValue() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull String truck = column(project, (String) body.get("itemId"), "Truck number", "text", false);
        final @NotNull String driver = column(project, (String) body.get("itemId"), "Driver", "text", false);
        columns(body).putAll(Map.of(truck, "Truck 7", driver, ""));
        final @NotNull String kept = read(project, record(project, body).then().statusCode(201).extract().path("id")).getString("entry.columnValues");
        assertTrue(kept.contains("Truck 7"), kept);
        assertFalse(kept.contains(driver), "an empty value is no value: " + kept);
    }

    @Test(description = "A value that does not fit its column is refused naming it", testName = "1005010f-4214-4959-94e6-a4727a2a895d", priority = 28)
    public void aValueThatDoesNotFitItsColumnIsRefusedNamingIt() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull String weight = column(project, (String) body.get("itemId"), "Weight", "number", false);
        columns(body).put(weight, "heavy");
        refusedOver(record(project, body), "invalidColumnValue", weight);
    }

    @Test(description = "A value that fits its column is kept", testName = "b6e476c4-24ca-454b-96e0-c93b0c19255b", priority = 29)
    public void aValueThatFitsItsColumnIsKept() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull String weight = column(project, (String) body.get("itemId"), "Weight", "number", false);
        final @NotNull String delivered = column(project, (String) body.get("itemId"), "Delivered", "date", false);
        columns(body).putAll(Map.of(weight, "12.5", delivered, "2026-11-01"));
        final @NotNull String kept = read(project, record(project, body).then().statusCode(201).extract().path("id")).getString("entry.columnValues");
        assertTrue(kept.contains("12.5") && kept.contains("2026-11-01"), kept);
    }

    @Test(description = "A value for a column the item does not ask is refused", testName = "bdc86e8e-335c-4561-8316-067d88f756d2", priority = 30)
    public void aValueForAColumnTheItemDoesNotAskIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull String elsewhere = column(project, Structure.itemId(project, "الخرسانة"), "Truck number", "text", false);
        columns(body).put(elsewhere, "Truck 7");
        refusedOver(record(project, body), "unknownColumn", elsewhere);
    }

    @Test(description = "A deleted column is no longer asked", testName = "6207c5de-7285-41e4-9481-4d32646c8e26", priority = 31)
    public void aDeletedColumnIsNoLongerAsked() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull String columns = "/projects/" + project + "/items/" + body.get("itemId") + "/columns";
        final @NotNull String key = column(project, (String) body.get("itemId"), "Truck number", "text", true);
        given(Api.asAdmin()).delete(columns + "/" + given(Api.asAdmin()).get(columns).then().extract().path("find { it.key == '" + key + "' }.id")).then().statusCode(200);
        body.put("submit", true);
        record(project, body).then().statusCode(201);
    }

    @Test(description = "The file of an invoice is linked to its entry as its invoice", testName = "c06ff89a-c9c3-4738-b9b1-8c4db39cc8ac", priority = 32)
    public void theFileOfAnInvoiceIsLinkedToItsEntryAsItsInvoice() {
        final @NotNull String project = Projects.approved();
        final @NotNull String file = upload(project, Files.png(2048));
        final @NotNull Map<String, Object> body = purchase(project);
        body.put("attachmentId", file);
        assertEquals(read(project, record(project, body).then().statusCode(201).extract().path("id")).getList("attachments.attachmentId"), List.of(file));
    }

    @Test(description = "The same file on a second expense is refused", testName = "dfd8d395-c135-4268-9f8d-9fee6e748ce9", priority = 33)
    public void theSameFileOnASecondExpenseIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull String file = upload(project, Files.png(2048));
        final @NotNull Map<String, Object> body = purchase(project);
        body.put("attachmentId", file);
        record(project, body).then().statusCode(201);
        body.put("id", UUID.randomUUID().toString());
        refusedAs(record(project, body), 409, "invoiceAlreadyAttached");
    }

    @Test(description = "The same file sent again is the same file and is refused", testName = "a9378681-7750-4e29-acdf-43551dd13ecd", priority = 34)
    public void theSameFileSentAgainIsTheSameFileAndIsRefused() {
        final @NotNull String project = Projects.approved();
        final byte[] picture = Files.png(2048);
        final @NotNull String file = upload(project, picture);
        final @NotNull Map<String, Object> body = purchase(project);
        body.put("attachmentId", file);
        record(project, body).then().statusCode(201);
        final @NotNull String again = upload(project, picture);
        assertEquals(again, file, "the same bytes from the same person answer the file already kept");
        body.putAll(Map.of("id", UUID.randomUUID().toString(), "attachmentId", again));
        refusedAs(record(project, body), 409, "invoiceAlreadyAttached");
    }

    @Test(description = "A file kept and on no entry is free to use", testName = "33d7d1ad-7b31-4147-8c15-5a65694ef878", priority = 35)
    public void aFileKeptAndOnNoEntryIsFreeToUse() {
        final @NotNull String project = Projects.approved();
        final @NotNull String file = upload(project, Files.png(2048));
        final @NotNull Map<String, Object> body = purchase(project);
        body.putAll(Map.of("attachmentId", file, "submit", true));
        record(project, body).then().statusCode(201);
    }

    @Test(description = "A file of another project is refused on an entry", testName = "0348401c-e8af-4053-9156-5ce6d3bea0ca", priority = 36)
    public void aFileOfAnotherProjectIsRefusedOnAnEntry() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        body.put("attachmentId", upload(Projects.approved(), Files.png(2048)));
        refusedAs(record(project, body), 400, "unknownAttachment");
    }

    @Test(description = "An account of another project is refused", testName = "83b44b56-8ff9-4db5-bb72-b464bbc24af1", priority = 37)
    public void anAccountOfAnotherProjectIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        body.put("accountId", purchase(Projects.approved()).get("accountId"));
        refusedAs(record(project, body), 400, "accountNotOffered");
    }

    @Test(description = "Someone outside the project cannot record in it", testName = "35852cb6-9231-4629-a981-e278dd476fde", priority = 38)
    public void someoneOutsideTheProjectCannotRecordInIt() {
        final @NotNull String project = Projects.approved();
        Entries.record(People.in(Projects.approved(), "recordEntries", "chargeAnyAccount").asked(), project, purchase(project)).then().statusCode(403);
    }

    @Test(description = "An upload sent again lands once and answers the entry", testName = "321e4d90-ba1f-4012-8613-41b1a7dc9b5c", priority = 39)
    public void anUploadSentAgainLandsOnceAndAnswersTheEntry() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull JsonPath first = record(project, body).then().statusCode(201).extract().jsonPath();
        final @NotNull JsonPath again = record(project, body).then().statusCode(200).extract().jsonPath();
        assertEquals(again.getString("id"), first.getString("id"));
        assertEquals(again.getString("status"), first.getString("status"));
        given(Api.asAdmin()).get("/projects/" + project + "/entries").then().body("findAll { it.id == '" + body.get("id") + "' }.size()", equalTo(1));
    }

    @Test(description = "An ID someone else used is refused and tells them nothing of it", testName = "c0004e13-c416-4dfb-988c-41ccc35ce4ac", priority = 40)
    public void anIdSomeoneElseUsedIsRefusedAndTellsThemNothingOfIt() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        record(project, body).then().statusCode(201);
        final @NotNull RequestSpecification someone = People.in(project, "recordEntries", "chargeAnyAccount").asked();
        final @NotNull Response refused = Entries.record(someone, project, body);
        refusedAs(refused, 409, "entryExists");
        assertFalse(refused.asString().contains("Cement") || refused.asString().contains("\"number\""), refused.asString());
    }

    @Test(description = "An expense is in the history with who and its total", testName = "d58fc9f3-4604-496e-a3f9-ad1e210fd319", priority = 41, enabled = false)
    public void anExpenseIsInTheHistoryWithWhoAndItsTotal() {
        throw new AssertionError("History has no route that reads an entry's own lines over HTTP; Go's internal/entries tests prove it");
    }

    @Test(description = "A draft is created in the history rather than submitted", testName = "414a92f8-a22a-465d-880d-0dca8cbbfe96", priority = 42, enabled = false)
    public void aDraftIsCreatedInTheHistoryRatherThanSubmitted() {
        throw new AssertionError("History has no route that reads an entry's own lines over HTTP; Go's internal/entries tests prove it");
    }

    @Test(description = "The search puts what the caller used most first and then by name", testName = "f62a1980-9583-401d-8c1c-9180868c4371", priority = 43)
    public void theSearchPutsWhatTheCallerUsedMostFirstAndThenByName() {
        final @NotNull String project = Projects.approved();
        final @NotNull String used = Suppliers.add(project, "Zinc Co");
        Suppliers.add(project, "Alpha Co");
        for (int i = 0; i < 3; i++) {
            final @NotNull Map<String, Object> body = purchase(project);
            body.put("supplierId", used);
            record(project, body).then().statusCode(201);
        }
        final @NotNull JsonPath found = given(Api.asAdmin()).queryParam("text", "Co").get("/projects/" + project + "/suppliers/search").then().statusCode(200).extract().jsonPath();
        assertEquals(found.getString("[0].name"), "Zinc Co");
        assertEquals(found.getInt("[0].timesUsed"), 3);
    }

    @Test(description = "Most used is the callers own not someone elses", testName = "f77f13f9-79b3-4039-98e9-ce766e7587d7", priority = 44)
    public void mostUsedIsTheCallersOwnNotSomeoneElses() {
        final @NotNull String project = Projects.approved();
        final @NotNull String used = Suppliers.add(project, "Zinc Co");
        Suppliers.add(project, "Alpha Co");
        for (int i = 0; i < 3; i++) {
            final @NotNull Map<String, Object> body = purchase(project);
            body.put("supplierId", used);
            record(project, body).then().statusCode(201);
        }
        final @NotNull List<String> names = given(People.in(project, "recordEntries").asked()).get("/projects/" + project + "/suppliers/search").then().statusCode(200).extract().path("name");
        assertTrue(names.indexOf("Alpha Co") < names.indexOf("Zinc Co"), "someone else's purchases do not rank " + names);
    }
}
