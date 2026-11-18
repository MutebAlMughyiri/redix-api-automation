// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Contracts;
import redix.support.Entries;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;
import redix.support.Structure;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ManageItemColumnsTest {
    private static final @NotNull List<String> PURCHASE_KEYS = List.of("description", "supplier", "date", "unitPrice", "quantity", "entryType", "account", "paymentMethod",
            "attachment", "total");

    private static final @NotNull List<String> PAYMENT_KEYS = List.of("description", "date", "outsideContract", "account", "paymentMethod", "supplier", "attachment", "contract",
            "amount");

    private static final @NotNull List<String> BOTH_KEYS = List.of("description", "supplier", "date", "outsideContract", "unitPrice", "quantity", "entryType", "account",
            "paymentMethod", "attachment", "contract", "amount", "total");

    private static final @NotNull List<String> REVENUE_KEYS = List.of("description", "date", "account", "attachment", "amount");

    private static @NotNull Map<String, Object> column(final @NotNull String name, final @NotNull String dataType, final @NotNull List<Map<String, String>> choices) {
        return new HashMap<>(Map.of("name", name, "dataType", dataType, "isRequired", false, "showInReports", false, "choices", choices));
    }

    private static @NotNull String items(final @NotNull String projectId, final @NotNull String itemId) {
        return "/projects/" + projectId + "/items/" + itemId + "/columns";
    }

    private static @NotNull String template(final @NotNull String projectId, final @NotNull String type) {
        return "/projects/" + projectId + "/default-columns/" + type;
    }

    private static @NotNull JsonPath columnsOf(final @NotNull String path) {
        return given(Api.asAdmin()).get(path).then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull Response send(final @NotNull String method, final @NotNull String path, final @NotNull Object body) {
        return given(Api.asAdmin()).body(body).request(method, path);
    }

    /** Adds a column to the item and answers its id, read back by its name. */
    private static @NotNull String add(final @NotNull String projectId, final @NotNull String itemId, final @NotNull Map<String, Object> column) {
        send("POST", items(projectId, itemId), column).then().statusCode(200);
        return columnsOf(items(projectId, itemId)).getString("find { it.name == '" + column.get("name") + "' }.id");
    }

    private static @NotNull Map<String, Object> templateColumn(final @NotNull String name, final boolean applyToAllItems) {
        final @NotNull Map<String, Object> body = column(name, "text", List.of());
        body.put("applyToAllItems", applyToAllItems);
        return body;
    }

    private static @NotNull String generalOf(final @NotNull String projectId, final @NotNull String itemId) {
        return Structure.tablesOf(projectId, itemId).getString("find { it.kind == 'general' }.id");
    }

    private static @NotNull String columnValuesOf(final @NotNull String projectId, final @NotNull String entryId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/entries/" + entryId).then().statusCode(200).extract().path("entry.columnValues");
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    @Test(description = "A new item inherits the default columns of its type", testName = "b9bd9b81-dc05-4c5d-afa1-4478a0db9c80", priority = 1)
    public void aNewItemInheritsTheDefaultColumnsOfItsType() {
        final @NotNull String project = Projects.approved();
        for (final String type : List.of("purchases", "contractorPayments", "both")) {
            final @NotNull String item = send("POST", "/projects/" + project + "/categories/" + Structure.categoryId(project, "الإشراف") + "/items", Map.of("name", "New " + type,
                    "type", type)).then().statusCode(201).extract().path("id");
            assertEquals(columnsOf(items(project, item)).getList("key"), columnsOf(template(project, type)).getList("key"), type);
        }
    }

    @Test(description = "A template item is copied with the columns of its type", testName = "84587ad5-203a-4502-85b6-112d61d7e2e6", priority = 2)
    public void aTemplateItemIsCopiedWithTheColumnsOfItsType() {
        final @NotNull String project = Projects.approved();
        assertEquals(columnsOf(items(project, Structure.itemId(project, Structure.PURCHASES))).getList("key"), PURCHASE_KEYS);
        assertEquals(columnsOf(items(project, Structure.itemId(project, Structure.CONTRACTOR_PAYMENTS))).getList("key"), PAYMENT_KEYS);
        assertEquals(columnsOf(items(project, Structure.itemId(project, Structure.BOTH))).getList("key"), BOTH_KEYS);
    }

    @Test(description = "The core columns of a purchase are the five left of br20 and naw al qayd which decides its sign", testName = "388c96ce-412e-4b28-ac4d-5fc49abcaf91", priority = 3)
    public void theCoreColumnsOfAPurchaseAreTheFiveLeftOfBr20AndNawAlQaydWhichDecidesItsSign() {
        final @NotNull JsonPath purchases = columnsOf(template(Projects.approved(), "purchases"));
        assertEquals(purchases.getList("findAll { it.isCore }.key"), List.of("description", "supplier", "date", "entryType", "account", "attachment", "total"));
        assertEquals(purchases.getList("find { it.key == 'entryType' }.choices.collect { it.name + ' ' + it.sign }"), List.of("شراء plus", "مرتجع minus"));
        assertEquals(purchases.getString("find { it.key == 'entryType' }.name"), "نوع القيد");
    }

    @Test(description = "No template holds the notes or the product and every one holds the description", testName = "34df6f25-214f-45f0-b802-7f4f80f53ae8", priority = 4)
    public void noTemplateHoldsTheNotesOrTheProductAndEveryOneHoldsTheDescription() {
        final @NotNull String project = Projects.approved();
        for (final String type : List.of("purchases", "contractorPayments", "both", "capital", "sale", "vatRefund", "otherRevenue")) {
            final @NotNull List<String> names = columnsOf(template(project, type)).getList("name");
            assertTrue(names.contains("الوصف") && !names.contains("ملاحظات") && !names.contains("الصنف"), type + " holds " + names);
        }
    }

    @Test(description = "Each column is named with the approved workbooks word", testName = "2ff7403a-7995-43d8-8dab-a18882c49f9f", priority = 5)
    public void eachColumnIsNamedWithTheApprovedWorkbooksWord() {
        final @NotNull JsonPath both = columnsOf(template(Projects.approved(), "both"));
        assertEquals(both.getList("name"), List.of("الوصف", "المورد", "التاريخ", "الاتفاق", "سعر الحبة", "العدد", "نوع القيد", "الحساب", "طريقة الدفع", "المرفق", "العقد", "الدفعة",
                "التكلفة"));
    }

    @Test(description = "An item of both types holds the purchase columns with what only a contractor payment has after what it follows", testName = "1c2ec0d0-3bd8-4dee-be27-61e0241184a6", priority = 6)
    public void anItemOfBothTypesHoldsThePurchaseColumnsWithWhatOnlyAContractorPaymentHasAfterWhatItFollows() {
        final @NotNull List<String> both = columnsOf(template(Projects.approved(), "both")).getList("key");
        assertTrue(both.containsAll(PURCHASE_KEYS) && both.containsAll(PAYMENT_KEYS));
        assertEquals(both.indexOf("outsideContract"), both.indexOf("date") + 1, "the agreement follows the date, as in a payment");
        assertTrue(both.indexOf("contract") > both.indexOf("attachment") && both.indexOf("amount") > both.indexOf("contract"));
        assertEquals(both.stream().filter(PURCHASE_KEYS::contains).toList(), PURCHASE_KEYS, "the purchase columns keep their order");
    }

    @Test(description = "Each template holds its columns in the order of the approved workbook", testName = "62419c5c-221f-4135-b5f5-b2d58693e7d0", priority = 7)
    public void eachTemplateHoldsItsColumnsInTheOrderOfTheApprovedWorkbook() {
        final @NotNull String project = Projects.approved();
        assertEquals(columnsOf(template(project, "purchases")).getList("key"), PURCHASE_KEYS);
        assertEquals(columnsOf(template(project, "contractorPayments")).getList("key"), PAYMENT_KEYS);
        assertEquals(columnsOf(template(project, "both")).getList("key"), BOTH_KEYS);
        assertEquals(columnsOf(template(project, "capital")).getList("key"), List.of("movementType", "description", "date", "account", "attachment", "amount"));
        for (final String type : List.of("sale", "vatRefund", "otherRevenue")) {
            assertEquals(columnsOf(template(project, type)).getList("key"), REVENUE_KEYS, type);
        }
        given(Api.asAdmin()).get(template(project, "sideways")).then().statusCode(400);
    }

    @Test(description = "The purchase template prints the supplier the account the method and the file but not the notes", testName = "e2a26dbe-2425-4812-bf71-cf6eca4238e7", priority = 8)
    public void thePurchaseTemplatePrintsTheSupplierTheAccountTheMethodAndTheFileButNotTheNotes() {
        final @NotNull JsonPath purchases = columnsOf(template(Projects.approved(), "purchases"));
        for (final String key : List.of("supplier", "account", "paymentMethod", "attachment")) {
            assertTrue(purchases.getBoolean("find { it.key == '" + key + "' }.showInReports"), key + " prints");
        }
        assertFalse(purchases.getList("key").contains("notes"));
    }

    @Test(description = "A person still adds no column to a revenue item", testName = "83e05d6c-eae8-40bc-90e3-329a41b7a898", priority = 9)
    public void aPersonStillAddsNoColumnToARevenueItem() {
        final @NotNull String project = Projects.approved();
        for (final String item : List.of(Structure.SALE, Structure.CAPITAL)) {
            refusedAs(send("POST", items(project, Structure.itemId(project, item)), column("Buyer", "text", List.of())), 400, "revenueTakesNoColumns");
        }
    }

    @Test(description = "Ras al mal asks naw al haraka whose choices put in and take out", testName = "9da14e71-3206-40c5-b10e-37c47eb8a71b", priority = 10)
    public void rasAlMalAsksNawAlHarakaWhoseChoicesPutInAndTakeOut() {
        final @NotNull String project = Projects.approved();
        final @NotNull JsonPath capital = columnsOf(items(project, Structure.itemId(project, Structure.CAPITAL)));
        assertEquals(capital.getString("find { it.key == 'movementType' }.name"), "نوع الحركة");
        assertEquals(capital.getList("find { it.key == 'movementType' }.choices.collect { it.name + ' ' + it.sign }"), List.of("إضافة مبلغ plus", "سحب مبلغ minus"));
    }

    @Test(description = "A payment is asked nothing only a purchase has and a purchase nothing only a payment has", testName = "418556c5-86e2-41c5-9357-e317915f966c", priority = 11)
    public void aPaymentIsAskedNothingOnlyAPurchaseHasAndAPurchaseNothingOnlyAPaymentHas() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        final @NotNull String contract = Contracts.add(project, item, "Contractor Co");
        final @NotNull JsonPath offered = given(Api.asAdmin()).get("/projects/" + project + "/entry-tables").then().statusCode(200).extract().jsonPath();
        final @NotNull String tables = "collectMany { it.items }.find { it.id == '" + item + "' }.tables";
        final @NotNull List<String> purchase = offered.getList(tables + ".find { it.kind == 'general' }.columnKeys");
        final @NotNull List<String> payment = offered.getList(tables + ".find { it.id == '" + contract + "' }.columnKeys");
        assertTrue(payment.stream().noneMatch(List.of("supplier", "unitPrice", "quantity", "entryType", "total")::contains), "a payment asks " + payment);
        assertTrue(purchase.stream().noneMatch(List.of("contract", "amount", "outsideContract")::contains), "a purchase asks " + purchase);
    }

    @Test(description = "A unit price required of the purchases is not asked of a payment on the same item", testName = "7c934fb1-00f4-4c32-9afe-12f220837530", priority = 12)
    public void aUnitPriceRequiredOfThePurchasesIsNotAskedOfAPaymentOnTheSameItem() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        final @NotNull String unitPrice = columnsOf(items(project, item)).getString("find { it.key == 'unitPrice' }.id");
        send("PUT", items(project, item) + "/" + unitPrice, Map.of("name", "سعر الحبة", "isRequired", true, "showInReports", true)).then().statusCode(200);
        final @NotNull String contract = Contracts.add(project, item, "Contractor Co");
        final @NotNull List<String> payment = given(Api.asAdmin()).get("/projects/" + project + "/entry-tables").then().statusCode(200).extract()
                .path("collectMany { it.items }.find { it.id == '" + item + "' }.tables.find { it.id == '" + contract + "' }.columnKeys");
        assertFalse(payment.contains("unitPrice"));
    }

    @Test(description = "Entry type and movement type lists_every entry keeps its figure and takes the choice its sign says", testName = "a94cfc98-20d2-425d-b4f7-09bce62d506b", priority = 13, enabled = false)
    public void entryTypeAndMovementTypeListsEveryEntryKeepsItsFigureAndTakesTheChoiceItsSignSays() {
        throw new AssertionError("about the migration that gave every item and template the new columns, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "A column added to one item is asked in that item only", testName = "c2053866-7091-48d1-be0e-7d982c92d34e", priority = 14)
    public void aColumnAddedToOneItemIsAskedInThatItemOnly() {
        final @NotNull String project = Projects.approved();
        add(project, Structure.itemId(project, Structure.PURCHASES), column("Truck number", "text", List.of()));
        assertTrue(columnsOf(items(project, Structure.itemId(project, Structure.PURCHASES))).getList("name").contains("Truck number"));
        assertFalse(columnsOf(items(project, Structure.itemId(project, "الخرسانة"))).getList("name").contains("Truck number"));
        assertFalse(columnsOf(template(project, "purchases")).getList("name").contains("Truck number"));
    }

    @Test(description = "A column needs a name of at most100 characters", testName = "6c6bf101-8fc9-403f-8838-48fa00ec3e46", priority = 15)
    public void aColumnNeedsANameOfAtMost100Characters() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        refusedAs(send("POST", items(project, item), column("n".repeat(101), "text", List.of())), 400, "invalidName");
        refusedAs(send("POST", items(project, item), column("  ", "text", List.of())), 400, "invalidName");
        add(project, item, column("n".repeat(100), "text", List.of()));
    }

    @Test(description = "A pick list gains choices and keeps its own in their order", testName = "7f8c5ea8-2e5c-4200-a1ec-96c959e6c9b3", priority = 16)
    public void aPickListGainsChoicesAndKeepsItsOwnInTheirOrder() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String list = add(project, item, column("Grade", "list", List.of(Map.of("name", "B"), Map.of("name", "A"))));
        send("PUT", items(project, item) + "/" + list, Map.of("name", "Grade", "isRequired", false, "showInReports", false, "newChoices", List.of(Map.of("name", "C"))))
                .then().statusCode(200);
        assertEquals(columnsOf(items(project, item)).getList("find { it.id == '" + list + "' }.choices.name"), List.of("B", "A", "C"));
    }

    @Test(description = "An edit that gives no new choice changes no choice", testName = "175a3ad7-0a67-4e00-9da0-76a186fddb3e", priority = 17)
    public void anEditThatGivesNoNewChoiceChangesNoChoice() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String list = add(project, item, column("Grade", "list", List.of(Map.of("name", "A"))));
        final @NotNull List<String> ids = columnsOf(items(project, item)).getList("find { it.id == '" + list + "' }.choices.id");
        send("PUT", items(project, item) + "/" + list, Map.of("name", "Steel grade", "isRequired", false, "showInReports", false)).then().statusCode(200);
        assertEquals(columnsOf(items(project, item)).getList("find { it.id == '" + list + "' }.choices.id"), ids);
    }

    @Test(description = "A new choice is within the limit of a name", testName = "84bdddcf-bb57-4078-a565-868b9a9f1146", priority = 18)
    public void aNewChoiceIsWithinTheLimitOfAName() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String list = add(project, item, column("Grade", "list", List.of(Map.of("name", "A"))));
        refusedAs(send("PUT", items(project, item) + "/" + list, Map.of("name", "Grade", "isRequired", false, "showInReports", false, "newChoices",
                List.of(Map.of("name", "c".repeat(101))))), 400, "invalidOptions");
        assertEquals(columnsOf(items(project, item)).getList("find { it.id == '" + list + "' }.choices.name"), List.of("A"));
    }

    @Test(description = "Only a pick list a person added gains choices", testName = "22b6b23d-c5a5-4e6d-9df3-3f38bfe30f08", priority = 19)
    public void onlyAPickListAPersonAddedGainsChoices() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String text = add(project, item, column("Truck number", "text", List.of()));
        refusedAs(send("PUT", items(project, item) + "/" + text, Map.of("name", "Truck number", "isRequired", false, "showInReports", false, "newChoices",
                List.of(Map.of("name", "C")))), 400, "invalidOptions");
        final @NotNull String entryType = columnsOf(items(project, item)).getString("find { it.key == 'entryType' }.id");
        refusedAs(send("PUT", items(project, item) + "/" + entryType, Map.of("name", "نوع القيد", "isRequired", false, "showInReports", true, "newChoices",
                List.of(Map.of("name", "خصم", "sign", "minus")))), 400, "invalidOptions");
        assertEquals(columnsOf(items(project, item)).getList("find { it.key == 'entryType' }.choices.name"), List.of("شراء", "مرتجع"));
    }

    @Test(description = "An entry can hold the choice a list gained", testName = "62455902-2ceb-4460-ac48-88bfb8357e5c", priority = 20)
    public void anEntryCanHoldTheChoiceAListGained() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String list = add(project, item, column("Grade", "list", List.of(Map.of("name", "A"))));
        send("PUT", items(project, item) + "/" + list, Map.of("name", "Grade", "isRequired", false, "showInReports", false, "newChoices", List.of(Map.of("name", "C"))))
                .then().statusCode(200);
        final @NotNull JsonPath columns = columnsOf(items(project, item));
        final @NotNull String gained = columns.getString("find { it.id == '" + list + "' }.choices.find { it.name == 'C' }.id");
        final @NotNull String entry = Entries.draftIn(project, item, generalOf(project, item), Map.of(columns.getString("find { it.id == '" + list + "' }.key"), gained))
                .then().statusCode(201).extract().path("id");
        assertTrue(columnValuesOf(project, entry).contains(gained));
    }

    @Test(description = "A choice the template gains reaches items only when applied", testName = "e609d136-844b-49e0-8eb1-d70c9eea14c9", priority = 21)
    public void aChoiceTheTemplateGainsReachesItemsOnlyWhenApplied() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull Map<String, Object> body = column("Grade", "list", List.of(Map.of("name", "A")));
        body.put("applyToAllItems", true);
        send("POST", template(project, "purchases"), body).then().statusCode(200);
        final @NotNull String list = columnsOf(template(project, "purchases")).getString("find { it.name == 'Grade' }.id");
        final @NotNull Map<String, Object> edit = new HashMap<>(Map.of("name", "Grade", "isRequired", false, "showInReports", false, "newChoices", List.of(Map.of("name", "B")),
                "applyToAllItems", false));
        send("PUT", template(project, "purchases") + "/" + list, edit).then().statusCode(200);
        assertEquals(columnsOf(items(project, item)).getList("find { it.name == 'Grade' }.choices.name"), List.of("A"));
        edit.put("newChoices", List.of(Map.of("name", "C")));
        edit.put("applyToAllItems", true);
        send("PUT", template(project, "purchases") + "/" + list, edit).then().statusCode(200);
        assertTrue(columnsOf(items(project, item)).getList("find { it.name == 'Grade' }.choices.name").contains("C"));
    }

    @Test(description = "A pick list needs a choice", testName = "5b5d2661-0be2-427b-ad5b-0587f4176b0e", priority = 22)
    public void aPickListNeedsAChoice() {
        final @NotNull String project = Projects.approved();
        refusedAs(send("POST", items(project, Structure.itemId(project, Structure.PURCHASES)), column("Grade", "list", List.of())), 400, "invalidOptions");
    }

    @Test(description = "A pick list keeps its choices and another type none", testName = "121a38b3-1589-49ff-88e1-bf0f181a5f2c", priority = 23)
    public void aPickListKeepsItsChoicesAndAnotherTypeNone() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        send("POST", items(project, item), column("Truck number", "text", List.of(Map.of("name", "A")))).then().statusCode(200);
        assertEquals(columnsOf(items(project, item)).getList("find { it.name == 'Truck number' }.choices"), List.of());
        final @NotNull String list = add(project, item, column("Grade", "list", List.of(Map.of("name", "A"), Map.of("name", "B"))));
        assertEquals(columnsOf(items(project, item)).getList("find { it.id == '" + list + "' }.choices.name"), List.of("A", "B"));
    }

    @Test(description = "A lists choices each have an ID and a sign of their own", testName = "d1b5369f-a629-402f-be7c-7fde810012b4", priority = 24)
    public void aListsChoicesEachHaveAnIdAndASignOfTheirOwn() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        final @NotNull String list = add(project, item, column("Grade", "list", List.of(Map.of("name", "A"), Map.of("name", "B"))));
        final @NotNull JsonPath columns = columnsOf(items(project, item));
        final @NotNull List<String> ids = columns.getList("find { it.id == '" + list + "' }.choices.id");
        assertEquals(ids.stream().distinct().count(), 2L);
        assertEquals(columns.getList("find { it.id == '" + list + "' }.choices.sign"), List.of("none", "none"));
    }

    @Test(description = "An item takes one list whose choices give a sign so the amounts direction has one answer", testName = "02d3096b-8fc1-4a30-b7e6-4d4c8c5431bc", priority = 25)
    public void anItemTakesOneListWhoseChoicesGiveASignSoTheAmountsDirectionHasOneAnswer() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        refusedAs(send("POST", items(project, item), column("Direction", "list", List.of(Map.of("name", "In", "sign", "plus")))), 400, "signedListExists");
        final @NotNull String list = add(project, item, column("Grade", "list", List.of(Map.of("name", "A"))));
        refusedAs(send("PUT", items(project, item) + "/" + list, Map.of("name", "Grade", "isRequired", false, "showInReports", false, "newChoices",
                List.of(Map.of("name", "Out", "sign", "minus")))), 400, "signedListExists");
    }

    @Test(description = "An entry keeps the ID of the choice chosen and is refused its name", testName = "ba46916f-67d8-48f5-b0d8-c10867f4df18", priority = 26)
    public void anEntryKeepsTheIdOfTheChoiceChosenAndIsRefusedItsName() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String returned = columnsOf(items(project, item)).getString("find { it.key == 'entryType' }.choices.find { it.name == 'مرتجع' }.id");
        final @NotNull String entry = Entries.draftIn(project, item, generalOf(project, item), Map.of("entryType", returned)).then().statusCode(201).extract().path("id");
        assertTrue(columnValuesOf(project, entry).contains(returned));
        Entries.draftIn(project, item, generalOf(project, item), Map.of("entryType", "مرتجع")).then().statusCode(400).body("code", equalTo("invalidColumnValue"))
                .body("columnKey", equalTo("entryType"));
    }

    @Test(description = "A value that fits its column is kept", testName = "4521345b-3c37-4969-8774-f9e8406bb73c", priority = 27)
    public void aValueThatFitsItsColumnIsKept() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String weight = add(project, item, column("Weight", "number", List.of()));
        final @NotNull String key = columnsOf(items(project, item)).getString("find { it.id == '" + weight + "' }.key");
        final @NotNull String entry = Entries.draftIn(project, item, generalOf(project, item), Map.of(key, "12.5")).then().statusCode(201).extract().path("id");
        assertTrue(columnValuesOf(project, entry).contains("12.5"));
        Entries.draftIn(project, item, generalOf(project, item), Map.of(key, "heavy")).then().statusCode(400).body("code", equalTo("invalidColumnValue"));
    }

    @Test(description = "List choices with signs_every choice gets an ID and no sign and every value naming one holds its ID", testName = "f4554ce3-a1e5-4a1f-82f1-046c08e824b3", priority = 28, enabled = false)
    public void listChoicesWithSignsEveryChoiceGetsAnIdAndNoSignAndEveryValueNamingOneHoldsItsId() {
        throw new AssertionError("about the migration that gave every kept choice an id, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "A person cannot add a file column", testName = "b9f6ad98-5b88-4c31-bfde-ef6f6c951d88", priority = 29)
    public void aPersonCannotAddAFileColumn() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        refusedAs(send("POST", items(project, item), column("Photo", "file", List.of())), 400, "invalidDataType");
        for (final String type : List.of("text", "number", "amount", "date")) {
            add(project, item, column("A " + type, type, List.of()));
        }
        add(project, item, column("A list", "list", List.of(Map.of("name", "A"))));
    }

    @Test(description = "A core column is renamed and reordered", testName = "a54f7072-ecd9-4bf2-bdad-6205e64afd35", priority = 30)
    public void aCoreColumnIsRenamedAndReordered() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String date = columnsOf(items(project, item)).getString("find { it.key == 'date' }.id");
        send("PUT", items(project, item) + "/" + date, Map.of("name", "تاريخ الفاتورة", "isRequired", true, "showInReports", true)).then().statusCode(200);
        final @NotNull List<String> ids = new ArrayList<>(columnsOf(items(project, item)).getList("id"));
        Collections.reverse(ids);
        send("POST", items(project, item) + "/reorder", Map.of("ids", ids)).then().statusCode(200);
        assertEquals(columnsOf(items(project, item)).getList("id"), ids);
        assertEquals(columnsOf(items(project, item)).getString("find { it.key == 'date' }.name"), "تاريخ الفاتورة");
    }

    @Test(description = "A core column changes nothing but its name", testName = "13d74bf6-3fc1-4c5a-9513-65757b0d25ec", priority = 31)
    public void aCoreColumnChangesNothingButItsName() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String date = columnsOf(items(project, item)).getString("find { it.key == 'date' }.id");
        refusedAs(send("PUT", items(project, item) + "/" + date, Map.of("name", "اليوم", "isRequired", false, "showInReports", false)), 409, "coreColumn");
        final @NotNull JsonPath columns = columnsOf(items(project, item));
        assertTrue(columns.getBoolean("find { it.key == 'date' }.isRequired") && columns.getBoolean("find { it.key == 'date' }.showInReports"));
        assertEquals(columns.getString("find { it.key == 'date' }.dataType"), "date");
    }

    @Test(description = "A column that is not core changes what entry asks and what prints", testName = "3adc1c73-a051-468d-a33f-cb972109d299", priority = 32)
    public void aColumnThatIsNotCoreChangesWhatEntryAsksAndWhatPrints() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String weight = add(project, item, column("Weight", "number", List.of()));
        send("PUT", items(project, item) + "/" + weight, Map.of("name", "Weight", "isRequired", true, "showInReports", true)).then().statusCode(200);
        final @NotNull JsonPath columns = columnsOf(items(project, item));
        assertTrue(columns.getBoolean("find { it.id == '" + weight + "' }.isRequired") && columns.getBoolean("find { it.id == '" + weight + "' }.showInReports"));
        final @NotNull String entry = Entries.draftIn(project, item, generalOf(project, item));
        given(Api.asAdmin()).post("/projects/" + project + "/entries/" + entry + "/submit").then().statusCode(400).body("code", equalTo("entryIncomplete"))
                .body("missing", hasItem(columns.getString("find { it.id == '" + weight + "' }.key")));
    }

    @Test(description = "A core column is never deleted", testName = "950411eb-30e1-4391-b094-a434778634ca", priority = 33)
    public void aCoreColumnIsNeverDeleted() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        refusedAs(given(Api.asAdmin()).delete(items(project, item) + "/" + columnsOf(items(project, item)).getString("find { it.key == 'total' }.id")), 409, "coreColumn");
        refusedAs(given(Api.asAdmin()).delete(template(project, "purchases") + "/" + columnsOf(template(project, "purchases")).getString("find { it.key == 'total' }.id")
                + "?applyToAllItems=false"), 409, "coreColumn");
    }

    @Test(description = "A deleted column leaves entry and its row stays", testName = "51ed72a5-a22c-4965-8c2c-073057b44175", priority = 34)
    public void aDeletedColumnLeavesEntryAndItsRowStays() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String weight = add(project, item, column("Weight", "number", List.of()));
        final @NotNull String key = columnsOf(items(project, item)).getString("find { it.id == '" + weight + "' }.key");
        final @NotNull String entry = Entries.draftIn(project, item, generalOf(project, item), Map.of(key, "12.5")).then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).delete(items(project, item) + "/" + weight).then().statusCode(200);
        assertFalse(columnsOf(items(project, item)).getList("id").contains(weight));
        assertTrue(columnValuesOf(project, entry).contains("12.5"), "its value stays on the entry");
    }

    @Test(description = "A non core column of the template is deleted on an item", testName = "4d033846-91bb-4a63-a938-decc6b70ae38", priority = 35)
    public void aNonCoreColumnOfTheTemplateIsDeletedOnAnItem() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        given(Api.asAdmin()).delete(items(project, item) + "/" + columnsOf(items(project, item)).getString("find { it.key == 'quantity' }.id")).then().statusCode(200);
        assertFalse(columnsOf(items(project, item)).getList("key").contains("quantity"));
        assertTrue(columnsOf(template(project, "purchases")).getList("key").contains("quantity"));
    }

    @Test(description = "A deleted column can be deleted or changed no more", testName = "79268d64-6b0d-4067-84bb-ff22ae064c2c", priority = 36)
    public void aDeletedColumnCanBeDeletedOrChangedNoMore() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String weight = add(project, item, column("Weight", "number", List.of()));
        given(Api.asAdmin()).delete(items(project, item) + "/" + weight).then().statusCode(200);
        given(Api.asAdmin()).delete(items(project, item) + "/" + weight).then().statusCode(404);
        send("PUT", items(project, item) + "/" + weight, Map.of("name", "Weight", "isRequired", false, "showInReports", false)).then().statusCode(404);
    }

    @Test(description = "A type never changes", testName = "8a365586-0b2d-498d-8bd9-c42560b35262", priority = 37)
    public void aTypeNeverChanges() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String weight = add(project, item, column("Weight", "number", List.of()));
        send("PUT", items(project, item) + "/" + weight, Map.of("name", "Weight", "isRequired", false, "showInReports", false, "dataType", "text")).then().statusCode(200);
        assertEquals(columnsOf(items(project, item)).getString("find { it.id == '" + weight + "' }.dataType"), "number");
    }

    @Test(description = "An order must name every visible column exactly once", testName = "9c6a08e1-1937-4b61-86e3-e291e8439902", priority = 38)
    public void anOrderMustNameEveryVisibleColumnExactlyOnce() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull List<String> ids = columnsOf(items(project, item)).getList("id");
        refusedAs(send("POST", items(project, item) + "/reorder", Map.of("ids", ids.subList(1, ids.size()))), 400, "invalidOrder");
        final @NotNull List<String> twice = new ArrayList<>(ids);
        twice.add(ids.getFirst());
        refusedAs(send("POST", items(project, item) + "/reorder", Map.of("ids", twice)), 400, "invalidOrder");
        assertEquals(columnsOf(items(project, item)).getList("id"), ids);
    }

    @Test(description = "A template change leaves existing items alone unless applied", testName = "034bf108-c1c6-4c3a-b489-1d8a77f899d4", priority = 39)
    public void aTemplateChangeLeavesExistingItemsAloneUnlessApplied() {
        final @NotNull String project = Projects.approved();
        send("POST", template(project, "purchases"), templateColumn("Truck number", false)).then().statusCode(200);
        assertFalse(columnsOf(items(project, Structure.itemId(project, Structure.PURCHASES))).getList("name").contains("Truck number"));
        send("POST", template(project, "purchases"), templateColumn("Driver", true)).then().statusCode(200);
        assertTrue(columnsOf(items(project, Structure.itemId(project, Structure.PURCHASES))).getList("name").contains("Driver"));
    }

    @Test(description = "A new item starts from the template as it is now", testName = "3702dcd2-88d2-4c42-adb9-7e62743fe3ac", priority = 40)
    public void aNewItemStartsFromTheTemplateAsItIsNow() {
        final @NotNull String project = Projects.approved();
        send("POST", template(project, "purchases"), templateColumn("Truck number", false)).then().statusCode(200);
        final @NotNull String item = send("POST", "/projects/" + project + "/categories/" + Structure.categoryId(project, "الإشراف") + "/items",
                Map.of("name", "Safety", "type", "purchases")).then().statusCode(201).extract().path("id");
        assertEquals(columnsOf(items(project, item)).getList("name"), columnsOf(template(project, "purchases")).getList("name"));
    }

    @Test(description = "A column applied to all reaches every item of that type and no other", testName = "0425c0b9-cfbd-4b7c-889c-6caa8164b171", priority = 41)
    public void aColumnAppliedToAllReachesEveryItemOfThatTypeAndNoOther() {
        final @NotNull String project = Projects.approved();
        final @NotNull String disabled = Structure.itemId(project, "الخرسانة");
        given(Api.asAdmin()).post("/projects/" + project + "/items/" + disabled + "/disable").then().statusCode(200);
        send("POST", template(project, "purchases"), templateColumn("Driver", true)).then().statusCode(200);
        for (final Object purchases : Structure.of(project).getList("collectMany { it.items }.findAll { it.type == 'purchases' }.id")) {
            assertTrue(columnsOf(items(project, (String) purchases)).getList("name").contains("Driver"), purchases + " lacks it");
        }
        assertFalse(columnsOf(items(project, Structure.itemId(project, Structure.BOTH))).getList("name").contains("Driver"));
    }

    @Test(description = "A rename applied to all reaches every item of that type", testName = "378287c7-7e06-43ea-a528-58da45b6cb56", priority = 42)
    public void aRenameAppliedToAllReachesEveryItemOfThatType() {
        final @NotNull String project = Projects.approved();
        send("POST", template(project, "purchases"), templateColumn("Driver", true)).then().statusCode(200);
        final @NotNull String driver = columnsOf(template(project, "purchases")).getString("find { it.name == 'Driver' }.id");
        send("PUT", template(project, "purchases") + "/" + driver, Map.of("name", "Driver name", "isRequired", false, "showInReports", false, "applyToAllItems", true))
                .then().statusCode(200);
        assertTrue(columnsOf(items(project, Structure.itemId(project, Structure.PURCHASES))).getList("name").contains("Driver name"));
        assertTrue(columnsOf(items(project, Structure.itemId(project, "الخرسانة"))).getList("name").contains("Driver name"));
    }

    @Test(description = "A delete applied to all hides it on every item of that type", testName = "1cb899bc-ff0e-4cf1-b50b-996377fb15fe", priority = 43)
    public void aDeleteAppliedToAllHidesItOnEveryItemOfThatType() {
        final @NotNull String project = Projects.approved();
        final @NotNull String quantity = columnsOf(template(project, "purchases")).getString("find { it.key == 'quantity' }.id");
        given(Api.asAdmin()).delete(template(project, "purchases") + "/" + quantity + "?applyToAllItems=true").then().statusCode(200);
        assertFalse(columnsOf(items(project, Structure.itemId(project, Structure.PURCHASES))).getList("key").contains("quantity"));
        assertFalse(columnsOf(items(project, Structure.itemId(project, "الخرسانة"))).getList("key").contains("quantity"));
        assertTrue(columnsOf(items(project, Structure.itemId(project, Structure.BOTH))).getList("key").contains("quantity"));
    }

    @Test(description = "An order applied to all keeps what only that item has after the rest", testName = "f1104e0a-d71b-4272-9539-3f118de107e8", priority = 44)
    public void anOrderAppliedToAllKeepsWhatOnlyThatItemHasAfterTheRest() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        add(project, item, column("Truck number", "text", List.of()));
        final @NotNull List<String> order = new ArrayList<>(columnsOf(template(project, "purchases")).getList("id"));
        Collections.reverse(order);
        send("POST", template(project, "purchases") + "/reorder", Map.of("ids", order, "applyToAllItems", true)).then().statusCode(200);
        final @NotNull List<String> keys = columnsOf(items(project, item)).getList("name");
        final @NotNull List<String> reversed = new ArrayList<>(columnsOf(template(project, "purchases")).getList("name"));
        assertEquals(keys.subList(0, reversed.size()), reversed);
        assertEquals(keys.getLast(), "Truck number");
    }

    @Test(description = "One projects template changes no other project", testName = "92774ecc-e3a8-4235-8b17-71cb45f47f9c", priority = 45)
    public void oneProjectsTemplateChangesNoOtherProject() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        send("POST", template(project, "purchases"), templateColumn("Driver", true)).then().statusCode(200);
        assertFalse(columnsOf(template(other, "purchases")).getList("name").contains("Driver"));
        assertFalse(columnsOf(items(other, Structure.itemId(other, Structure.PURCHASES))).getList("name").contains("Driver"));
        assertEquals(columnsOf(template(Projects.approved(), "purchases")).getList("key"), PURCHASE_KEYS);
    }

    @Test(description = "An earlier projects columns are copied without what it deleted", testName = "a1f8f8a2-3d94-41cd-8d9f-63d216304397", priority = 46)
    public void anEarlierProjectsColumnsAreCopiedWithoutWhatItDeleted() {
        final @NotNull String source = Projects.approved();
        final @NotNull String item = Structure.itemId(source, Structure.PURCHASES);
        add(source, item, column("Truck number", "text", List.of()));
        given(Api.asAdmin()).delete(items(source, item) + "/" + columnsOf(items(source, item)).getString("find { it.key == 'quantity' }.id")).then().statusCode(200);
        final @NotNull String copy = given(Api.asAdmin()).body(Map.of("name", "Copy " + UUID.randomUUID().toString().substring(0, 8), "startDate", LocalDate.now().toString(),
                "units", List.of("A1"), "partners", List.of(), "structureSourceId", source)).post("/projects").then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + copy + "/approve").then().statusCode(200);
        final @NotNull List<String> copied = columnsOf(items(copy, Structure.itemId(copy, Structure.PURCHASES))).getList("name");
        assertTrue(copied.contains("Truck number") && !columnsOf(items(copy, Structure.itemId(copy, Structure.PURCHASES))).getList("key").contains("quantity"), "copied " + copied);
    }

    @Test(description = "Everyone with access reads columns and only finance changes them", testName = "8c3f63cf-3860-44e1-a28f-1b70784dc02a", priority = 47)
    public void everyoneWithAccessReadsColumnsAndOnlyFinanceChangesThem() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull Person reader = People.in(project);
        given(reader.asked()).get(items(project, item)).then().statusCode(200).body("key", equalTo(PURCHASE_KEYS));
        given(reader.asked()).get(template(project, "purchases")).then().statusCode(200);
        given(reader.asked()).body(column("Weight", "number", List.of())).post(items(project, item)).then().statusCode(403);
        given(reader.asked()).body(templateColumn("Weight", false)).post(template(project, "purchases")).then().statusCode(403);
        given(People.in(project, "manageStructure").asked()).body(column("Weight", "number", List.of())).post(items(project, item)).then().statusCode(200);
    }

    @Test(description = "A route of one project never reaches anothers columns", testName = "8ffd3d9d-a9fe-4929-a8c2-863b9327b08d", priority = 48)
    public void aRouteOfOneProjectNeverReachesAnothersColumns() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull String theirItem = Structure.itemId(other, Structure.PURCHASES);
        final @NotNull String theirs = add(other, theirItem, column("Weight", "number", List.of()));
        final @NotNull String ours = Structure.itemId(project, Structure.PURCHASES);
        send("PUT", items(project, ours) + "/" + theirs, Map.of("name", "Taken", "isRequired", false, "showInReports", false)).then().statusCode(404);
        given(Api.asAdmin()).delete(items(project, ours) + "/" + theirs).then().statusCode(404);
        given(Api.asAdmin()).get(items(project, theirItem)).then().statusCode(404);
        assertTrue(columnsOf(items(other, theirItem)).getList("name").contains("Weight"));
    }

    @Test(description = "A finished project refuses column changes and stays readable", testName = "e1dcb412-fa4a-42f9-8bcd-8f2dc0041094", priority = 49)
    public void aFinishedProjectRefusesColumnChangesAndStaysReadable() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);
        refusedAs(send("POST", items(project, item), column("Weight", "number", List.of())), 409, "projectFinished");
        refusedAs(send("POST", template(project, "purchases"), templateColumn("Weight", false)), 409, "projectFinished");
        refusedAs(send("POST", items(project, item) + "/reorder", Map.of("ids", columnsOf(items(project, item)).getList("id"))), 409, "projectFinished");
        assertEquals(columnsOf(items(project, item)).getList("key"), PURCHASE_KEYS);
    }
}
