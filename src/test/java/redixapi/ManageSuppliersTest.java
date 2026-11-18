// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;
import redix.support.Structure;

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

public class ManageSuppliersTest {
    private static @NotNull String suppliers(final @NotNull String projectId) {
        return "/projects/" + projectId + "/suppliers";
    }

    private static @NotNull Map<String, Object> supplier(final @NotNull String name, final @NotNull String vatNumber) {
        final @NotNull Map<String, Object> body = new HashMap<>(Map.of("name", name));
        if (!vatNumber.isEmpty()) {
            body.put("vatNumber", vatNumber);
        }
        return body;
    }

    private static @NotNull Response add(final @NotNull String projectId, final @NotNull Map<String, Object> body) {
        return given(Api.asAdmin()).body(body).post(suppliers(projectId));
    }

    private static @NotNull String added(final @NotNull String projectId, final @NotNull Map<String, Object> body) {
        return add(projectId, body).then().statusCode(201).extract().path("id");
    }

    private static @NotNull Response edit(final @NotNull String projectId, final @NotNull String supplierId, final @NotNull Map<String, Object> body) {
        return given(Api.asAdmin()).body(body).put(suppliers(projectId) + "/" + supplierId);
    }

    private static @NotNull JsonPath listed(final @NotNull String projectId) {
        return given(Api.asAdmin()).get(suppliers(projectId)).then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull JsonPath one(final @NotNull String projectId, final @NotNull String supplierId) {
        return listed(projectId).setRootPath("find { it.id == '" + supplierId + "' }");
    }

    private static @NotNull String general(final @NotNull String projectId) {
        return listed(projectId).getString("[0].id");
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    @Test(description = "Finance adds a supplier with all its details", testName = "a8d4b80a-f035-45c6-949b-0b900b1d685d", priority = 1)
    public void financeAddsASupplierWithAllItsDetails() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String id = add(project, Map.of("name", "  Cement Co  ", "vatNumber", "300000000000003", "mobile", "+966512345678", "defaultItemId", item)).then()
                .statusCode(201).body("name", equalTo("Cement Co")).extract().path("id");
        final @NotNull JsonPath kept = one(project, id);
        assertEquals(kept.getString("vatNumber"), "300000000000003");
        assertEquals(kept.getString("mobile"), "+966512345678");
        assertEquals(kept.getString("defaultItemId"), item);
        assertEquals(kept.getString("defaultItemName"), Structure.PURCHASES);
        assertEquals(kept.getString("status"), "approved");
    }

    @Test(description = "A VAT number is none or any number of at most20 digits", testName = "f7e4cb65-d805-42f0-bcf4-6d6917b0d676", priority = 2)
    public void aVatNumberIsNoneOrAnyNumberOfAtMost20Digits() {
        final @NotNull String project = Projects.approved();
        for (final String vat : List.of("1", "123456789012345", "1".repeat(20))) {
            assertEquals(one(project, added(project, supplier("Supplier " + vat.length() + UUID.randomUUID(), vat))).getString("vatNumber"), vat);
        }
    }

    @Test(description = "A VAT number of more than20 digits or anything but digits is refused", testName = "365c2a43-27cb-4cd7-a330-a65c259071bf", priority = 3)
    public void aVatNumberOfMoreThan20DigitsOrAnythingButDigitsIsRefused() {
        final @NotNull String project = Projects.approved();
        for (final String vat : List.of("1".repeat(21), "30000000000000A", "300-000", "٣٠٠٠", "300 000")) {
            refusedAs(add(project, supplier("Cement Co", vat)), 400, "invalidVatNumber");
        }
        assertEquals(listed(project).getList("name").size(), 1);
    }

    @Test(description = "A VAT number another supplier holds is refused naming that supplier", testName = "6a00c590-6d43-45bb-93e6-fcace0598861", priority = 4)
    public void aVatNumberAnotherSupplierHoldsIsRefusedNamingThatSupplier() {
        final @NotNull String project = Projects.approved();
        final @NotNull String holder = added(project, supplier("Cement Co", "300000000000003"));
        add(project, supplier("Other Co", " 300000000000003 ")).then().statusCode(409).body("code", equalTo("vatNumberTaken")).body("existing.id", equalTo(holder))
                .body("existing.name", equalTo("Cement Co"));
    }

    @Test(description = "An edit to a number another supplier holds is refused", testName = "ca61b03d-5db2-4d3e-add8-a94a9185c7ce", priority = 5)
    public void anEditToANumberAnotherSupplierHoldsIsRefused() {
        final @NotNull String project = Projects.approved();
        added(project, supplier("Cement Co", "300000000000003"));
        final @NotNull String other = added(project, supplier("Other Co", "300000000000004"));
        refusedAs(edit(project, other, supplier("Other Co", "300000000000003")), 409, "vatNumberTaken");
        assertEquals(one(project, other).getString("vatNumber"), "300000000000004");
    }

    @Test(description = "A disabled supplier still holds its VAT number", testName = "15a2bbd7-6813-449c-a764-43aece276b15", priority = 6)
    public void aDisabledSupplierStillHoldsItsVatNumber() {
        final @NotNull String project = Projects.approved();
        final @NotNull String holder = added(project, supplier("Cement Co", "300000000000003"));
        given(Api.asAdmin()).post(suppliers(project) + "/" + holder + "/disable").then().statusCode(200);
        refusedAs(add(project, supplier("Other Co", "300000000000003")), 409, "vatNumberTaken");
    }

    @Test(description = "The database holds a VAT number to one supplier in a project", testName = "f3beeb9f-7ce5-488d-97b9-7c91b57c522f", priority = 7, enabled = false)
    public void theDatabaseHoldsAVatNumberToOneSupplierInAProject() {
        throw new AssertionError("about the table's unique index, inside the server; what a request is answered is the 4th test");
    }

    @Test(description = "One VAT number may be in two projects", testName = "30a25c8f-66ca-4e99-9bc3-05faa30ac841", priority = 8)
    public void oneVatNumberMayBeInTwoProjects() {
        added(Projects.approved(), supplier("Cement Co", "300000000000003"));
        added(Projects.approved(), supplier("Cement Co", "300000000000003"));
    }

    @Test(description = "A supplier with no VAT number is saved with none", testName = "2e7064d5-405d-4da9-99a7-21291ce6c65e", priority = 9)
    public void aSupplierWithNoVatNumberIsSavedWithNone() {
        final @NotNull String project = Projects.approved();
        final @NotNull JsonPath kept = one(project, added(project, supplier("Cement Co", "")));
        assertEquals(kept.getString("vatNumber"), "");
        assertEquals(kept.getString("mobile"), "");
        assertEquals(kept.getString("defaultItemId"), "");
    }

    @Test(description = "Any number of suppliers may have no VAT number", testName = "b6eaf9f0-c1f5-49bc-85c0-be06727b1c35", priority = 10)
    public void anyNumberOfSuppliersMayHaveNoVatNumber() {
        final @NotNull String project = Projects.approved();
        added(project, supplier("Cement Co", ""));
        added(project, supplier("Steel Co", ""));
        added(project, supplier("Sand Co", ""));
        assertEquals(listed(project).getList("findAll { it.vatNumber == '' }").size(), 4);
    }

    @Test(description = "An edit changes a supplier and leaves it its own number", testName = "ca06bbd4-7200-4f61-bbff-4c6d399dce2b", priority = 11)
    public void anEditChangesASupplierAndLeavesItItsOwnNumber() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = added(project, supplier("Cement Co", "300000000000003"));
        edit(project, id, Map.of("name", "Cement Company", "vatNumber", "300000000000003", "mobile", "0512345678")).then().statusCode(200);
        final @NotNull JsonPath kept = one(project, id);
        assertEquals(kept.getString("name"), "Cement Company");
        assertEquals(kept.getString("vatNumber"), "300000000000003");
        assertEquals(kept.getString("mobile"), "0512345678");
    }

    @Test(description = "An edit can take the default item and the VAT number away", testName = "27140631-fa69-47ed-b5b2-7be5b4062aa2", priority = 12)
    public void anEditCanTakeTheDefaultItemAndTheVatNumberAway() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = added(project, Map.of("name", "Cement Co", "vatNumber", "300000000000003", "defaultItemId", Structure.itemId(project, Structure.PURCHASES)));
        edit(project, id, supplier("Cement Co", "")).then().statusCode(200);
        assertEquals(one(project, id).getString("vatNumber"), "");
        assertEquals(one(project, id).getString("defaultItemId"), "");
        added(project, supplier("Other Co", "300000000000003"));
    }

    @Test(description = "A supplier needs a name of at most100 characters", testName = "8f5a138b-be8d-4a9a-a812-879ec19a0fb0", priority = 13)
    public void aSupplierNeedsANameOfAtMost100Characters() {
        final @NotNull String project = Projects.approved();
        refusedAs(add(project, supplier("s".repeat(101), "")), 400, "invalidName");
        refusedAs(add(project, supplier("  ", "")), 400, "invalidName");
        refusedAs(edit(project, added(project, supplier("s".repeat(100), "")), supplier("", "")), 400, "invalidName");
    }

    @Test(description = "A mobile may have at most20 characters", testName = "25714a83-2965-4191-a8cc-214e355611fb", priority = 14)
    public void aMobileMayHaveAtMost20Characters() {
        final @NotNull String project = Projects.approved();
        refusedAs(add(project, Map.of("name", "Cement Co", "mobile", "0".repeat(21))), 400, "invalidMobile");
        assertEquals(one(project, added(project, Map.of("name", "Cement Co", "mobile", "0".repeat(20)))).getString("mobile"), "0".repeat(20));
    }

    @Test(description = "A suppliers mobile is digits on add and on edit", testName = "173d82c9-5858-4418-b32a-9cc5f3d038f6", priority = 15)
    public void aSuppliersMobileIsDigitsOnAddAndOnEdit() {
        final @NotNull String project = Projects.approved();
        refusedAs(add(project, Map.of("name", "Cement Co", "mobile", "05-1234")), 400, "invalidMobile");
        final @NotNull String id = added(project, Map.of("name", "Cement Co", "mobile", "+966512345678"));
        refusedAs(edit(project, id, Map.of("name", "Cement Co", "mobile", "٠٥١٢")), 400, "invalidMobile");
        assertEquals(one(project, id).getString("mobile"), "+966512345678");
    }

    @Test(description = "A default item must be in the project", testName = "4783a9e4-e57e-46ae-b14d-2a7d9fa5fa8d", priority = 16)
    public void aDefaultItemMustBeInTheProject() {
        final @NotNull String project = Projects.approved();
        final @NotNull String theirs = Structure.itemId(Projects.approved(), Structure.PURCHASES);
        refusedAs(add(project, Map.of("name", "Cement Co", "defaultItemId", theirs)), 400, "unknownItem");
        refusedAs(edit(project, added(project, supplier("Steel Co", "")), Map.of("name", "Steel Co", "defaultItemId", theirs)), 400, "unknownItem");
    }

    @Test(description = "A disabled supplier stays in the register and can be enabled", testName = "0bf1d733-1a31-41ba-b6b2-9f72a6baf6b8", priority = 17)
    public void aDisabledSupplierStaysInTheRegisterAndCanBeEnabled() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = added(project, supplier("Cement Co", ""));
        given(Api.asAdmin()).post(suppliers(project) + "/" + id + "/disable").then().statusCode(200);
        assertEquals(one(project, id).getString("isActive"), "false");
        given(Api.asAdmin()).post(suppliers(project) + "/" + id + "/enable").then().statusCode(200);
        assertEquals(one(project, id).getString("isActive"), "true");
        given(Api.asAdmin()).delete(suppliers(project) + "/" + id).then().statusCode(405);
    }

    @Test(description = "The register lists suppliers by name", testName = "c4f14d88-9a36-4c6f-a188-c356d7fb801f", priority = 18)
    public void theRegisterListsSuppliersByName() {
        final @NotNull String project = Projects.approved();
        for (final String name : List.of("Zinc Co", "Alpha Co", "Mid Co")) {
            added(project, supplier(name, ""));
        }
        final @NotNull List<String> names = listed(project).getList("findAll { it.name.endsWith(' Co') }.name");
        assertEquals(names, List.of("Alpha Co", "Mid Co", "Zinc Co"));
    }

    @Test(description = "An approved project starts with the general cash supplier", testName = "5620332a-89e5-4705-a05a-4d76d6d3efee", priority = 19)
    public void anApprovedProjectStartsWithTheGeneralCashSupplier() {
        final @NotNull JsonPath seeded = listed(Projects.approved());
        assertEquals(seeded.getList("$").size(), 1);
        assertTrue(seeded.getBoolean("[0].isActive"));
        assertEquals(seeded.getString("[0].status"), "approved");
    }

    @Test(description = "The general cash supplier cannot be disabled", testName = "aac4b115-e245-4e83-8540-6d23e2c975ce", priority = 20)
    public void theGeneralCashSupplierCannotBeDisabled() {
        final @NotNull String project = Projects.approved();
        refusedAs(given(Api.asAdmin()).post(suppliers(project) + "/" + general(project) + "/disable"), 409, "generalCashSupplier");
        assertEquals(one(project, general(project)).getString("isActive"), "true");
    }

    @Test(description = "The general cash supplier can be renamed and stays the general one", testName = "a48d113b-f36d-4712-a30f-6dbe90b1adcf", priority = 21)
    public void theGeneralCashSupplierCanBeRenamedAndStaysTheGeneralOne() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = general(project);
        edit(project, id, supplier("Cash purchases", "")).then().statusCode(200);
        assertEquals(one(project, id).getString("name"), "Cash purchases");
        refusedAs(given(Api.asAdmin()).post(suppliers(project) + "/" + id + "/disable"), 409, "generalCashSupplier");
    }

    @Test(description = "A supplier named like the general one can be disabled", testName = "af0ca404-7655-47fd-9c96-4b67207aad6d", priority = 22)
    public void aSupplierNamedLikeTheGeneralOneCanBeDisabled() {
        final @NotNull String project = Projects.approved();
        final @NotNull String twin = added(project, supplier(listed(project).getString("[0].name"), ""));
        given(Api.asAdmin()).post(suppliers(project) + "/" + twin + "/disable").then().statusCode(200);
        assertEquals(one(project, twin).getString("isActive"), "false");
    }

    @Test(description = "The migration marks the row the approval named in a project approved before it", testName = "6efbc552-fe07-4ae0-b725-9d2feccd75ea", priority = 23, enabled = false)
    public void theMigrationMarksTheRowTheApprovalNamedInAProjectApprovedBeforeIt() {
        throw new AssertionError("about the migration that set the flag on projects approved before it, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "A copied project starts with only the general cash supplier", testName = "d32a2cb3-9bfd-431f-9414-78d64fb5b13c", priority = 24)
    public void aCopiedProjectStartsWithOnlyTheGeneralCashSupplier() {
        final @NotNull String source = Projects.approved();
        added(source, supplier("Cement Co", "300000000000003"));
        final @NotNull String copy = given(Api.asAdmin()).body(Map.of("name", "Copy " + UUID.randomUUID().toString().substring(0, 8), "startDate", LocalDate.now().toString(),
                "units", List.of("A1"), "partners", List.of(), "structureSourceId", source)).post("/projects").then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + copy + "/approve").then().statusCode(200);
        assertEquals(listed(copy).getList("name"), List.of(listed(Projects.approved()).getString("[0].name")));
    }

    @Test(description = "Everyone with access reads the register", testName = "f4c6e7bb-dfa6-4e94-97d6-016e954f887b", priority = 25)
    public void everyoneWithAccessReadsTheRegister() {
        final @NotNull String project = Projects.approved();
        added(project, supplier("Cement Co", ""));
        given(People.in(project).asked()).get(suppliers(project)).then().statusCode(200).body("size()", equalTo(2));
        given(People.in(Projects.approved()).asked()).get(suppliers(project)).then().statusCode(403);
    }

    @Test(description = "Only finance changes suppliers", testName = "ea09945d-e3cc-4290-861b-acb8d05c445b", priority = 26)
    public void onlyFinanceChangesSuppliers() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = added(project, supplier("Cement Co", ""));
        final @NotNull Person reader = People.in(project, "recordEntries");
        given(reader.asked()).body(supplier("Steel Co", "")).post(suppliers(project)).then().statusCode(403);
        given(reader.asked()).body(supplier("Taken", "")).put(suppliers(project) + "/" + id).then().statusCode(403);
        given(reader.asked()).post(suppliers(project) + "/" + id + "/disable").then().statusCode(403);
        given(People.in(project, "manageSettings").asked()).body(supplier("Taken", "")).put(suppliers(project) + "/" + id).then().statusCode(200);
    }

    @Test(description = "One projects suppliers are never anothers", testName = "66f08d7a-1fc6-42e2-91d2-a867c84283d9", priority = 27)
    public void oneProjectsSuppliersAreNeverAnothers() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull String theirs = added(other, supplier("Cement Co", ""));
        assertFalse(listed(project).getList("id").contains(theirs));
        edit(project, theirs, supplier("Taken", "")).then().statusCode(404);
        given(Api.asAdmin()).post(suppliers(project) + "/" + theirs + "/disable").then().statusCode(404);
        assertEquals(one(other, theirs).getString("name"), "Cement Co");
    }

    @Test(description = "A finished project refuses every change and stays readable", testName = "c5f1823a-aa5b-4b7c-be19-44da10d2e2a9", priority = 28)
    public void aFinishedProjectRefusesEveryChangeAndStaysReadable() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = added(project, supplier("Cement Co", ""));
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);
        refusedAs(add(project, supplier("Steel Co", "")), 409, "projectFinished");
        refusedAs(edit(project, id, supplier("Taken", "")), 409, "projectFinished");
        refusedAs(given(Api.asAdmin()).post(suppliers(project) + "/" + id + "/disable"), 409, "projectFinished");
        assertEquals(listed(project).getList("name").size(), 2);
    }
}
