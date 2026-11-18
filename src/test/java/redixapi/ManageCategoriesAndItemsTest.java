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
import redix.support.History;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;
import redix.support.Structure;
import redix.support.Suppliers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ManageCategoriesAndItemsTest {
    private static @NotNull Response asAdmin(final @NotNull String method, final @NotNull String path, final @NotNull Object body) {
        return given(Api.asAdmin()).body(body).request(method, path);
    }

    private static @NotNull Response changeType(final @NotNull String projectId, final @NotNull String itemId, final @NotNull String type) {
        return asAdmin("PUT", "/projects/" + projectId + "/items/" + itemId + "/type", Map.of("type", type));
    }

    private static @NotNull String addItem(final @NotNull String projectId, final @NotNull String categoryId, final @NotNull String name, final @NotNull String type) {
        return asAdmin("POST", "/projects/" + projectId + "/categories/" + categoryId + "/items", Map.of("name", name, "type", type)).then().statusCode(201).extract().path("id");
    }

    private static @NotNull String addCategory(final @NotNull String projectId, final @NotNull String name) {
        return asAdmin("POST", "/projects/" + projectId + "/categories", Map.of("name", name)).then().statusCode(201).extract().path("id");
    }

    private static @NotNull JsonPath entryTables(final @NotNull RequestSpecification as, final @NotNull String projectId) {
        return given(as).get("/projects/" + projectId + "/entry-tables").then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull List<String> offeredItemNames(final @NotNull String projectId, final @NotNull String itemType) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/categories/offered?itemType=" + itemType).then().statusCode(200).extract().path("collectMany { it.items }.name");
    }

    private static @NotNull List<String> enteredItemIds(final @NotNull RequestSpecification as, final @NotNull String projectId) {
        return entryTables(as, projectId).getList("collectMany { it.items }.id");
    }

    private static @NotNull String generalOf(final @NotNull String projectId, final @NotNull String itemId) {
        return Structure.tablesOf(projectId, itemId).getString("find { it.kind == 'general' }.id");
    }

    private static @NotNull List<String> columnKeys(final @NotNull String projectId, final @NotNull String itemId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/items/" + itemId + "/columns").then().statusCode(200).extract().path("key");
    }

    private static @NotNull String startedFrom(final @NotNull String sourceId) {
        final @NotNull String id = asAdmin("POST", "/projects", Map.of("name", "Copy " + UUID.randomUUID().toString().substring(0, 8), "startDate", LocalDate.now().toString(),
                "units", List.of("A1"), "partners", List.of(), "structureSourceId", sourceId)).then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + id + "/approve").then().statusCode(200);
        return id;
    }

    private static @NotNull String projectWithPartners(final @NotNull String... partners) {
        final @NotNull String id = asAdmin("POST", "/projects", Map.of("name", "Partners " + UUID.randomUUID().toString().substring(0, 8), "startDate", LocalDate.now().toString(),
                "units", List.of("A1"), "partners", List.of(partners))).then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + id + "/approve").then().statusCode(200);
        return id;
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    @Test(description = "An item a contract names keeps its type", testName = "4a606dd6-be2e-403e-8487-749fe3d3b7d2", priority = 1)
    public void anItemAContractNamesKeepsItsType() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.CONTRACTOR_PAYMENTS);
        Contracts.add(project, item, "Contractor Co");
        refusedAs(changeType(project, item, "purchases"), 409, "itemHasContract");
        assertEquals(Structure.item(project, Structure.CONTRACTOR_PAYMENTS).get("type"), "contractorPayments");
    }

    @Test(description = "A capital item stays one while partners are its tables and takes no sub table", testName = "7b212b86-c05a-448e-a888-d51b2da70898", priority = 2)
    public void aCapitalItemStaysOneWhilePartnersAreItsTablesAndTakesNoSubTable() {
        final @NotNull String project = projectWithPartners("Partner One");
        final @NotNull String capital = Structure.itemId(project, Structure.CAPITAL);
        refusedAs(changeType(project, capital, "sale"), 409, "itemHasPartners");
        refusedAs(asAdmin("POST", "/projects/" + project + "/items/" + capital + "/subgroups", Map.of("name", "Extra")), 400, "itemTakesNoPurchases");
        assertEquals(Structure.tablesOf(project, capital).getList("kind"), List.of("partner"));
    }

    @Test(description = "Every project starts with ras al mal a category of its own first among the revenue whose sheet gives each partners share", testName = "34c2ece3-c74b-4478-8c57-29ccb2ea52de", priority = 3)
    public void everyProjectStartsWithRasAlMalACategoryOfItsOwnFirstAmongTheRevenueWhoseSheetGivesEachPartnersShare() {
        final @NotNull JsonPath structure = Structure.of(Projects.approved());
        final @NotNull List<String> types = structure.getList("collect { it.items[0].type }");
        final int capital = types.indexOf("capital");
        assertTrue(types.subList(0, capital).stream().allMatch(type -> List.of("purchases", "contractorPayments", "both").contains(type)), "expenses come first: " + types);
        assertTrue(types.subList(capital + 1, types.size()).stream().allMatch(type -> List.of("sale", "vatRefund", "otherRevenue").contains(type)), "revenue after: " + types);
        assertEquals(structure.getString("[" + capital + "].name"), Structure.CAPITAL);
        assertEquals(structure.getList("[" + capital + "].items").size(), 1);
        assertTrue(structure.getBoolean("[" + capital + "].items[0].showsTablePercentages"), "its sheet gives each partner's share");
    }

    @Test(description = "The template holds the categories and items of the brs", testName = "65e29df2-3e27-473d-863f-a303ebfc4be9", priority = 4)
    public void theTemplateHoldsTheCategoriesAndItemsOfTheBrs() {
        final @NotNull JsonPath structure = Structure.of(Projects.approved());
        assertEquals(structure.getList("name"), Structure.TEMPLATE_CATEGORIES);
        assertEquals(structure.getList("collect { it.items.size() }"), List.of(1, 6, 9, 14, 2, 1, 1, 1, 1));
    }

    @Test(description = "A project has one capital item so no second one shows one partners balance", testName = "20d4ff05-759e-4edc-a19d-038179dbf464", priority = 5)
    public void aProjectHasOneCapitalItemSoNoSecondOneShowsOnePartnersBalance() {
        final @NotNull String project = Projects.approved();
        refusedAs(asAdmin("POST", "/projects/" + project + "/categories/" + Structure.categoryId(project, "الإشراف") + "/items", Map.of("name", "Second capital", "type", "capital")),
                409, "capitalItemExists");
        refusedAs(changeType(project, Structure.itemId(project, Structure.PURCHASES), "capital"), 409, "capitalItemExists");
        assertEquals(Structure.of(project).getList("collectMany { it.items }.findAll { it.type == 'capital' }").size(), 1);
    }

    @Test(description = "Ras al mals tables show every partner to whoever sees all capital their own to a partner and none to anyone else", testName = "3ce63de0-c0fb-4f7c-b453-be5e08e5ae70", priority = 6)
    public void rasAlMalsTablesShowEveryPartnerToWhoeverSeesAllCapitalTheirOwnToAPartnerAndNoneToAnyoneElse() {
        final @NotNull String project = projectWithPartners("Partner One", "Partner Two");
        final @NotNull String capital = Structure.itemId(project, Structure.CAPITAL);
        final @NotNull Person all = People.in(project, "seeAllCapital");
        final @NotNull Person partner = People.in(project);
        final @NotNull Person reader = People.in(project);
        final @NotNull String partnerOne = Structure.tablesOf(project, capital).getString("find { it.name == 'Partner One' }.id");
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + partner.id() + "/partners/" + partnerOne).then().statusCode(200);
        final @NotNull String tables = "/projects/" + project + "/items/" + capital + "/tables";
        assertEquals(given(all.asked()).get(tables).then().statusCode(200).extract().path("name"), List.of("Partner One", "Partner Two"));
        assertEquals(given(partner.asked()).get(tables).then().statusCode(200).extract().path("name"), List.of("Partner One"));
        assertEquals(given(reader.asked()).get(tables).then().statusCode(200).extract().path("name"), List.of());
    }

    @Test(description = "Revenue goes into every revenue item but ras al mal and a partner into nothing else", testName = "6c3f80a3-e421-4a2c-98ae-b1652a26ff6e", priority = 7)
    public void revenueGoesIntoEveryRevenueItemButRasAlMalAndAPartnerIntoNothingElse() {
        final @NotNull String project = projectWithPartners("Partner One");
        final @NotNull List<String> revenue = offeredItemNames(project, "sale");
        assertTrue(revenue.containsAll(List.of("البيع", "استرجاع الضرائب", "إيرادات أخرى")) && !revenue.contains(Structure.CAPITAL), "revenue offers " + revenue);
        final @NotNull JsonPath entered = entryTables(Api.asAdmin(), project);
        assertEquals(entered.getList("collectMany { it.items }.find { it.name == '" + Structure.CAPITAL + "' }.tables.kind"), List.of("partner"));
        assertFalse(entered.getList("collectMany { it.items }.findAll { it.name != '" + Structure.CAPITAL + "' }.collectMany { it.tables }.kind").contains("partner"));
    }

    @Test(description = "A registration that chose an earlier project keeps it in a row with its history and one that chose the template keeps none", testName = "e82a27e2-1c3a-4481-b68c-d1ef3b279507", priority = 8, enabled = false)
    public void aRegistrationThatChoseAnEarlierProjectKeepsItInARowWithItsHistoryAndOneThatChoseTheTemplateKeepsNone() {
        throw new AssertionError("about the row and History line that keep the choice, inside the server; what the choice copies is the 38th test");
    }

    @Test(description = "The database refuses a structure source naming no project", testName = "7a9ba77e-c920-4091-8ce3-2d44f376e068", priority = 9, enabled = false)
    public void theDatabaseRefusesAStructureSourceNamingNoProject() {
        throw new AssertionError("about the table's foreign key, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "Land cost can be recorded because its category holds one item of both types", testName = "c268fdce-dccb-4e3d-b317-73e0e58be5c3", priority = 10)
    public void landCostCanBeRecordedBecauseItsCategoryHoldsOneItemOfBothTypes() {
        final @NotNull String project = Projects.approved();
        final @NotNull JsonPath structure = Structure.of(project);
        assertEquals(structure.getList("find { it.name == 'تكلفة الأراضي' }.items.collect { it.name + ' ' + it.type }"), List.of("تكلفة الأرض both"));
        final @NotNull String land = structure.getString("find { it.name == 'تكلفة الأراضي' }.items[0].id");
        Entries.draftIn(project, land, generalOf(project, land));
    }

    @Test(description = "A subgroup no entry uses is removed", testName = "8d419d17-129c-4f0c-a7d5-5c6e927ae22b", priority = 11)
    public void aSubgroupNoEntryUsesIsRemoved() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        final @NotNull String subgroup = Structure.addSubgroup(project, item, "Villa 1");
        given(Api.asAdmin()).delete("/projects/" + project + "/items/" + item + "/subgroups/" + subgroup).then().statusCode(200);
        assertEquals(Structure.item(project, Structure.BOTH).get("subgroups").toString(), "[{id=" + Structure.tablesOf(project, item).getString("[0].id") + ", name=}]");
    }

    @Test(description = "A subgroup added after a removal still goes last", testName = "1f9676c4-cf82-4761-a8a6-04d2afbe4b17", priority = 12)
    public void aSubgroupAddedAfterARemovalStillGoesLast() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        final @NotNull String first = Structure.addSubgroup(project, item, "Villa 1");
        Structure.addSubgroup(project, item, "Villa 2");
        given(Api.asAdmin()).delete("/projects/" + project + "/items/" + item + "/subgroups/" + first).then().statusCode(200);
        Structure.addSubgroup(project, item, "Villa 1");
        assertEquals(Structure.tablesOf(project, item).getList("name"), List.of(Structure.BOTH, "Villa 2", "Villa 1"));
    }

    @Test(description = "A subgroup an entry uses is not removed", testName = "379252f5-42ad-4e5b-a45c-9f72c25b4f47", priority = 13)
    public void aSubgroupAnEntryUsesIsNotRemoved() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        final @NotNull String subgroup = Structure.addSubgroup(project, item, "Villa 1");
        Entries.draftIn(project, item, subgroup);
        refusedAs(given(Api.asAdmin()).delete("/projects/" + project + "/items/" + item + "/subgroups/" + subgroup), 409, "subgroupInUse");
    }

    @Test(description = "The general subgroup is never removed", testName = "3e30255c-034f-46cb-9624-07d2c77e7fa1", priority = 14)
    public void theGeneralSubgroupIsNeverRemoved() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        refusedAs(given(Api.asAdmin()).delete("/projects/" + project + "/items/" + item + "/subgroups/" + generalOf(project, item)), 409, "generalSubgroup");
    }

    @Test(description = "An items tables are reordered as one list the general one too", testName = "41750933-8228-4de6-a2e6-1382d4953abe", priority = 15)
    public void anItemsTablesAreReorderedAsOneListTheGeneralOneToo() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        final @NotNull String general = generalOf(project, item);
        final @NotNull String first = Structure.addSubgroup(project, item, "Villa 1");
        final @NotNull String contract = Contracts.add(project, item, "Contractor Co");
        final @NotNull List<String> order = List.of(contract, general, first);
        asAdmin("POST", "/projects/" + project + "/items/" + item + "/tables/reorder", Map.of("ids", order)).then().statusCode(200);
        assertEquals(Structure.tablesOf(project, item).getList("id"), order);
    }

    @Test(description = "An item lists its general table each subgroup and each contract with their entries and a new table goes last", testName = "afa28b04-88ab-4e14-9e51-7bb2149efc0d", priority = 16)
    public void anItemListsItsGeneralTableEachSubgroupAndEachContractWithTheirEntriesAndANewTableGoesLast() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        Structure.addSubgroup(project, item, "Villa 1");
        Contracts.add(project, item, "Contractor Co");
        Structure.addSubgroup(project, item, "Villa 2");
        final @NotNull JsonPath tables = Structure.tablesOf(project, item);
        assertEquals(tables.getList("name"), List.of(Structure.BOTH, "Villa 1", "Contractor Co", "Villa 2"));
        assertEquals(tables.getList("kind").getFirst(), "general");
        assertTrue(tables.getList("count").stream().allMatch(count -> ((Integer) count) == 0));
    }

    @Test(description = "An order of tables names every table exactly once", testName = "40e66a45-b514-4497-8538-f4940b25690e", priority = 17)
    public void anOrderOfTablesNamesEveryTableExactlyOnce() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        final @NotNull String general = generalOf(project, item);
        final @NotNull String first = Structure.addSubgroup(project, item, "Villa 1");
        final @NotNull String reorder = "/projects/" + project + "/items/" + item + "/tables/reorder";
        refusedAs(asAdmin("POST", reorder, Map.of("ids", List.of(general))), 400, "invalidOrder");
        refusedAs(asAdmin("POST", reorder, Map.of("ids", List.of(general, first, first))), 400, "invalidOrder");
        refusedAs(asAdmin("POST", reorder, Map.of("ids", List.of(general, UUID.randomUUID().toString()))), 400, "invalidOrder");
        assertEquals(Structure.tablesOf(project, item).getList("id"), List.of(general, first));
    }

    @Test(description = "One projects routes never reach anothers subgroups", testName = "52371f77-163c-46a2-bb0c-956aaa3c7497", priority = 18)
    public void oneProjectsRoutesNeverReachAnothersSubgroups() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        final @NotNull String theirs = Structure.addSubgroup(other, Structure.itemId(other, Structure.BOTH), "Villa 1");
        asAdmin("PUT", "/projects/" + project + "/items/" + item + "/subgroups/" + theirs, Map.of("name", "Taken")).then().statusCode(404);
        given(Api.asAdmin()).delete("/projects/" + project + "/items/" + item + "/subgroups/" + theirs).then().statusCode(404);
        assertEquals(Structure.tablesOf(other, Structure.itemId(other, Structure.BOTH)).getList("name"), List.of(Structure.BOTH, "Villa 1"));
    }

    @Test(description = "Sub table_an item that takes no purchases has no sub table", testName = "6976695a-8d17-47b3-a786-5266ecaad691", priority = 19)
    public void subTableAnItemThatTakesNoPurchasesHasNoSubTable() {
        final @NotNull String project = Projects.approved();
        refusedAs(asAdmin("POST", "/projects/" + project + "/items/" + Structure.itemId(project, Structure.CONTRACTOR_PAYMENTS) + "/subgroups", Map.of("name", "Villa 1")), 400,
                "itemTakesNoPurchases");
        refusedAs(asAdmin("POST", "/projects/" + project + "/items/" + Structure.itemId(project, Structure.SALE) + "/subgroups", Map.of("name", "Villa 1")), 400, "itemTakesNoPurchases");
    }

    @Test(description = "Sub table_a sub table added is offered and takes a purchase", testName = "45534bbe-7eb9-4c2a-9680-deaa52d75c84", priority = 20)
    public void subTableASubTableAddedIsOfferedAndTakesAPurchase() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String subgroup = Structure.addSubgroup(project, item, "Villa 1");
        assertTrue(entryTables(Api.asAdmin(), project).getList("collectMany { it.items }.find { it.id == '" + item + "' }.tables.id").contains(subgroup));
        Entries.draftIn(project, item, subgroup);
    }

    @Test(description = "Sub table_an item of contractor payments with a contract may become both", testName = "b4715913-2ed9-4173-aa7c-cb799cc51238", priority = 21)
    public void subTableAnItemOfContractorPaymentsWithAContractMayBecomeBoth() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.CONTRACTOR_PAYMENTS);
        Contracts.add(project, item, "Contractor Co");
        changeType(project, item, "both").then().statusCode(200);
        assertEquals(Structure.item(project, Structure.CONTRACTOR_PAYMENTS).get("type"), "both");
    }

    @Test(description = "Sub table_an item of purchases with an entry may become both", testName = "ef522e84-237b-4a20-a8b9-c6f3a9d9e343", priority = 22)
    public void subTableAnItemOfPurchasesWithAnEntryMayBecomeBoth() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        Entries.draftIn(project, item, generalOf(project, item));
        changeType(project, item, "both").then().statusCode(200);
        assertEquals(Structure.item(project, Structure.PURCHASES).get("type"), "both");
    }

    @Test(description = "An item offers an expense its general table each named one and each active contract in its sheets order", testName = "952c622a-ccbd-45da-a0f6-40aba46380a9", priority = 23)
    public void anItemOffersAnExpenseItsGeneralTableEachNamedOneAndEachActiveContractInItsSheetsOrder() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        final @NotNull String subgroup = Structure.addSubgroup(project, item, "Villa 1");
        final @NotNull String active = Contracts.add(project, item, "Contractor Co");
        final @NotNull String disabled = Contracts.add(project, item, "Gone Co");
        given(Api.asAdmin()).post("/projects/" + project + "/contracts/" + disabled + "/disable").then().statusCode(200);
        assertEquals(entryTables(Api.asAdmin(), project).getList("collectMany { it.items }.find { it.id == '" + item + "' }.tables.id"),
                List.of(generalOf(project, item), subgroup, active));
    }

    @Test(description = "No disabled item or category and no item without a table is offered", testName = "5825d823-7bbc-46e6-b48d-a8a5bdf71a13", priority = 24)
    public void noDisabledItemOrCategoryAndNoItemWithoutATableIsOffered() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String category = Structure.categoryId(project, "الإشراف");
        final @NotNull List<String> categoryItems = Structure.of(project).getList("find { it.name == 'الإشراف' }.items.id");
        given(Api.asAdmin()).post("/projects/" + project + "/items/" + item + "/disable").then().statusCode(200);
        given(Api.asAdmin()).post("/projects/" + project + "/categories/" + category + "/disable").then().statusCode(200);
        final @NotNull List<String> offered = enteredItemIds(Api.asAdmin(), project);
        assertFalse(offered.contains(item));
        assertTrue(categoryItems.stream().noneMatch(offered::contains));
        assertFalse(offered.contains(Structure.itemId(project, Structure.CONTRACTOR_PAYMENTS)), "an item of contractor payments with no contract has no table");
        final @NotNull List<String> revenue = List.of(Structure.itemId(project, Structure.SALE));
        assertTrue(enteredItemIds(People.in(project, "recordEntries").asked(), project).stream().noneMatch(revenue::contains));
        assertTrue(enteredItemIds(People.in(project, "recordEntries", "moveMoney").asked(), project).containsAll(revenue));
    }

    @Test(description = "An items type changes while it has no entries and is offered as its new type is", testName = "bf528b3a-49f0-4818-a28b-c70d38c2aee0", priority = 25)
    public void anItemsTypeChangesWhileItHasNoEntriesAndIsOfferedAsItsNewTypeIs() {
        final @NotNull String project = Projects.approved();
        changeType(project, Structure.itemId(project, Structure.PURCHASES), "contractorPayments").then().statusCode(200);
        assertTrue(offeredItemNames(project, "contractorPayments").contains(Structure.PURCHASES));
        assertFalse(offeredItemNames(project, "purchases").contains(Structure.PURCHASES));
    }

    @Test(description = "A changed type is in the history old and new", testName = "20a3d49e-d6ab-43b8-a927-527e9c947728", priority = 26)
    public void aChangedTypeIsInTheHistoryOldAndNew() {
        final @NotNull String project = Projects.approved();
        final @NotNull String name = "Item " + UUID.randomUUID().toString().substring(0, 6);
        final @NotNull String item = addItem(project, addCategory(project, "Category " + UUID.randomUUID().toString().substring(0, 6)), name, "purchases");
        changeType(project, item, "both").then().statusCode(200);
        assertEquals(History.said(Api.asAdmin(), project, "?area=structure").get(0), "changed item " + name);
    }

    @Test(description = "An item with an entry keeps its type", testName = "c942df83-48d7-4e0f-860c-baf0f5f7e9e9", priority = 27)
    public void anItemWithAnEntryKeepsItsType() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        Entries.draftIn(project, item, generalOf(project, item));
        refusedAs(changeType(project, item, "contractorPayments"), 409, "itemHasEntries");
        assertEquals(Structure.item(project, Structure.PURCHASES).get("type"), "purchases");
    }

    @Test(description = "Only finance changes a type and never another projects", testName = "ceb94af0-59d0-4bdf-9b36-2a0828e38895", priority = 28)
    public void onlyFinanceChangesATypeAndNeverAnotherProjects() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        given(People.in(project, "recordEntries").asked()).body(Map.of("type", "both")).put("/projects/" + project + "/items/" + item + "/type").then().statusCode(403);
        changeType(Projects.approved(), item, "both").then().statusCode(404);
        assertEquals(Structure.item(project, Structure.PURCHASES).get("type"), "purchases");
    }

    @Test(description = "A changed type asks the columns of the new type and keeps what a person added", testName = "cd0184d9-7bda-43fe-8756-5bd44bc7f0b8", priority = 29)
    public void aChangedTypeAsksTheColumnsOfTheNewTypeAndKeepsWhatAPersonAdded() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        asAdmin("POST", "/projects/" + project + "/items/" + item + "/columns", Map.of("name", "Truck number", "dataType", "text", "isRequired", false, "showInReports", false,
                "choices", List.of())).then().statusCode(200);
        changeType(project, item, "contractorPayments").then().statusCode(200);
        final @NotNull List<String> contractorColumns = columnKeys(project, Structure.itemId(project, Structure.CONTRACTOR_PAYMENTS));
        final @NotNull List<String> changed = columnKeys(project, item);
        assertTrue(changed.containsAll(contractorColumns), changed + " asks every column of contractor payments " + contractorColumns);
        assertEquals(given(Api.asAdmin()).get("/projects/" + project + "/items/" + item + "/columns").then().extract().path("findAll { it.isCustom }.name"), List.of("Truck number"));
    }

    @Test(description = "Changing a type there and back leaves one of each column", testName = "47f4620a-4403-49fb-8da4-e92ff1bebe34", priority = 30)
    public void changingATypeThereAndBackLeavesOneOfEachColumn() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull List<String> before = columnKeys(project, item);
        changeType(project, item, "contractorPayments").then().statusCode(200);
        changeType(project, item, "purchases").then().statusCode(200);
        final @NotNull List<String> after = columnKeys(project, item);
        assertEquals(after.stream().distinct().toList(), after, "a column asked twice");
        assertTrue(after.containsAll(before), after + " lost one of " + before);
    }

    @Test(description = "An item is added on its side and revenue entry offers every revenue item and no expense one", testName = "36f4c864-2dde-44b6-83d1-41155d6149f9", priority = 31)
    public void anItemIsAddedOnItsSideAndRevenueEntryOffersEveryRevenueItemAndNoExpenseOne() {
        final @NotNull String project = Projects.approved();
        addItem(project, Structure.categoryId(project, "إيرادات أخرى"), "Rent", "otherRevenue");
        final @NotNull List<String> revenue = offeredItemNames(project, "sale");
        assertTrue(revenue.contains("Rent"));
        final @NotNull List<String> expenses = Structure.of(project).getList("collectMany { it.items }.findAll { it.type in ['purchases', 'contractorPayments', 'both'] }.name");
        assertTrue(revenue.stream().noneMatch(expenses::contains), "revenue offers an expense item: " + revenue);
    }

    @Test(description = "A revenue item asks no columns", testName = "5cb4f785-7ff6-4c7e-829c-31a64848c712", priority = 32)
    public void aRevenueItemAsksNoColumns() {
        final @NotNull String project = Projects.approved();
        refusedAs(asAdmin("POST", "/projects/" + project + "/items/" + Structure.itemId(project, Structure.SALE) + "/columns", Map.of("name", "Buyer", "dataType", "text",
                "isRequired", false, "showInReports", false, "choices", List.of())), 400, "revenueTakesNoColumns");
    }

    @Test(description = "Every item has a general subgroup", testName = "7f766d99-eb66-44f0-9100-0b390a64e2a4", priority = 33)
    public void everyItemHasAGeneralSubgroup() {
        final @NotNull List<List<String>> subgroupNames = Structure.of(Projects.approved()).getList("collectMany { it.items }.collect { it.subgroups.name }");
        assertTrue(subgroupNames.stream().allMatch(names -> names.equals(List.of(""))), "items' subgroups " + subgroupNames);
    }

    @Test(description = "An item is added with its type at the end of its category with its general group", testName = "07022213-5818-49b4-bd3f-add2f759ec88", priority = 34)
    public void anItemIsAddedWithItsTypeAtTheEndOfItsCategoryWithItsGeneralGroup() {
        final @NotNull String project = Projects.approved();
        final @NotNull String category = Structure.categoryId(project, "الإشراف");
        asAdmin("POST", "/projects/" + project + "/categories/" + category + "/items", Map.of("name", "  Safety  ", "type", "purchases")).then().statusCode(201)
                .body("name", equalTo("Safety")).body("type", equalTo("purchases")).body("subgroups.name", equalTo(List.of("")));
        assertEquals(Structure.of(project).getList("find { it.name == 'الإشراف' }.items.name").getLast(), "Safety");
    }

    @Test(description = "Approving copies the template into rows of the projects own", testName = "fbe95af5-512a-4bb0-87fa-a7be4531cb22", priority = 35)
    public void approvingCopiesTheTemplateIntoRowsOfTheProjectsOwn() {
        final @NotNull JsonPath first = Structure.of(Projects.approved());
        final @NotNull JsonPath second = Structure.of(Projects.approved());
        assertEquals(first.getList("name"), second.getList("name"));
        final @NotNull List<String> shared = new ArrayList<>(first.getList("collectMany { it.items }.id"));
        shared.retainAll(second.getList("collectMany { it.items }.id"));
        assertEquals(shared, List.of(), "two projects share an item row");
    }

    @Test(description = "The public registration starts from the template when approved", testName = "18de358b-df7d-426d-ad25-00430a8c2589", priority = 36)
    public void thePublicRegistrationStartsFromTheTemplateWhenApproved() {
        final @NotNull Projects.Registered registered = Projects.registered();
        assertEquals(given(registered.asked()).get("/projects/" + registered.id() + "/categories").then().statusCode(200).extract().path("name"), Structure.TEMPLATE_CATEGORIES);
    }

    @Test(description = "A waiting or rejected project has no structure", testName = "ca7e32cc-2e58-416a-a7c9-c6520a3917b3", priority = 37)
    public void aWaitingOrRejectedProjectHasNoStructure() {
        final @NotNull String waiting = Projects.pending();
        final @NotNull String rejected = Projects.pending();
        asAdmin("POST", "/projects/" + rejected + "/reject", Map.of("reason", "No land deed")).then().statusCode(200);
        given(Api.asAdmin()).get("/projects/" + waiting + "/categories").then().statusCode(403);
        given(Api.asAdmin()).get("/projects/" + rejected + "/categories").then().statusCode(403);
        given(Api.asAdmin()).post("/projects/" + waiting + "/approve").then().statusCode(200);
        assertEquals(Structure.of(waiting).getList("name"), Structure.TEMPLATE_CATEGORIES, "the structure is copied at approval");
    }

    @Test(description = "A project copied from an earlier one takes its structure at approval", testName = "41b3ed1b-9a9c-404a-bd34-e78cc0b25fbd", priority = 38)
    public void aProjectCopiedFromAnEarlierOneTakesItsStructureAtApproval() {
        final @NotNull String source = Projects.approved();
        final @NotNull String custom = addCategory(source, "Custom works");
        addItem(source, custom, "Pergola", "both");
        final @NotNull String copy = startedFrom(source);
        final @NotNull JsonPath copied = Structure.of(copy);
        assertEquals(copied.getList("name"), Structure.of(source).getList("name"));
        assertEquals(copied.getList("find { it.name == 'Custom works' }.items.name"), List.of("Pergola"));
        Suppliers.add(source, "Cement Co");
        final @NotNull List<String> copiedSuppliers = given(Api.asAdmin()).get("/projects/" + startedFrom(source) + "/suppliers").then().statusCode(200).extract().path("name");
        final @NotNull List<String> newSuppliers = given(Api.asAdmin()).get("/projects/" + Projects.approved() + "/suppliers").then().statusCode(200).extract().path("name");
        assertEquals(copiedSuppliers, newSuppliers, "a supplier is not structure");
    }

    @Test(description = "What was disabled in the earlier project is not copied", testName = "2f3ff862-cb8b-4be6-b082-e3c4f07148f3", priority = 39)
    public void whatWasDisabledInTheEarlierProjectIsNotCopied() {
        final @NotNull String source = Projects.approved();
        given(Api.asAdmin()).post("/projects/" + source + "/categories/" + Structure.categoryId(source, "الإشراف") + "/disable").then().statusCode(200);
        given(Api.asAdmin()).post("/projects/" + source + "/items/" + Structure.itemId(source, Structure.PURCHASES) + "/disable").then().statusCode(200);
        final @NotNull JsonPath copied = Structure.of(startedFrom(source));
        assertFalse(copied.getList("name").contains("الإشراف"));
        assertFalse(copied.getList("collectMany { it.items }.name").contains(Structure.PURCHASES));
    }

    @Test(description = "An earlier project the caller cannot read cannot be copied", testName = "3d1a4af1-b593-4f3f-a647-d4c706c411d5", priority = 40)
    public void anEarlierProjectTheCallerCannotReadCannotBeCopied() {
        final @NotNull Projects.Registered registered = Projects.registered();
        given(registered.asked()).body(Map.of("name", "Copy", "startDate", LocalDate.now().toString(), "units", List.of("A1"), "partners", List.of(),
                "structureSourceId", Projects.approved())).post("/projects").then().statusCode(400).body("code", equalTo("unknownSource"));
    }

    @Test(description = "The template itself cannot be named as an earlier project", testName = "36cd62bb-89ba-49b5-a47e-41efb39c0678", priority = 41)
    public void theTemplateItselfCannotBeNamedAsAnEarlierProject() {
        refusedAs(asAdmin("POST", "/projects", Map.of("name", "Copy", "startDate", LocalDate.now().toString(), "units", List.of("A1"), "partners", List.of(),
                "structureSourceId", Structure.TEMPLATE)), 400, "unknownSource");
    }

    @Test(description = "Nobody sees the template", testName = "a87577e4-3dbe-4386-bda7-eac77dc297fb", priority = 42)
    public void nobodySeesTheTemplate() {
        given(Api.asAdmin()).get("/projects").then().statusCode(200).body("id", not(hasItem(Structure.TEMPLATE)));
        given(Api.asAdmin()).get("/projects/" + Structure.TEMPLATE + "/categories").then().statusCode(403);
    }

    @Test(description = "A change in one project leaves the template and other projects alone", testName = "08e83dbd-eca2-4aa0-8b4a-a8aed569af8d", priority = 43)
    public void aChangeInOneProjectLeavesTheTemplateAndOtherProjectsAlone() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        asAdmin("PUT", "/projects/" + project + "/categories/" + Structure.categoryId(project, "الإشراف"), Map.of("name", "Oversight")).then().statusCode(200);
        addCategory(project, "Custom works");
        assertEquals(Structure.of(other).getList("name"), Structure.TEMPLATE_CATEGORIES);
        assertEquals(Structure.of(Projects.approved()).getList("name"), Structure.TEMPLATE_CATEGORIES);
    }

    @Test(description = "A route of one project never reaches anothers rows", testName = "cddc0cde-8d23-4b95-8c46-b42358521910", priority = 44)
    public void aRouteOfOneProjectNeverReachesAnothersRows() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull String theirCategory = Structure.categoryId(other, "الإشراف");
        final @NotNull String theirItem = Structure.itemId(other, Structure.PURCHASES);
        asAdmin("PUT", "/projects/" + project + "/categories/" + theirCategory, Map.of("name", "Taken")).then().statusCode(404);
        given(Api.asAdmin()).post("/projects/" + project + "/categories/" + theirCategory + "/disable").then().statusCode(404);
        asAdmin("PUT", "/projects/" + project + "/items/" + theirItem, Map.of("name", "Taken")).then().statusCode(404);
        given(Api.asAdmin()).post("/projects/" + project + "/items/" + theirItem + "/disable").then().statusCode(404);
        assertEquals(Structure.of(other).getList("name"), Structure.TEMPLATE_CATEGORIES);
    }

    @Test(description = "The order of categories is the order the report reads", testName = "8a0368a9-77e6-4575-aa6b-1361ea2824ab", priority = 45)
    public void theOrderOfCategoriesIsTheOrderTheReportReads() {
        final @NotNull String project = Projects.approved();
        final @NotNull List<String> ids = new ArrayList<>(Structure.of(project).getList("id"));
        Collections.reverse(ids);
        asAdmin("POST", "/projects/" + project + "/categories/reorder", Map.of("ids", ids)).then().statusCode(200);
        assertEquals(Structure.of(project).getList("id"), ids);
    }

    @Test(description = "The order of items is the order the report reads", testName = "f3609143-e0b3-4fbb-8cc5-7968eb66ed52", priority = 46)
    public void theOrderOfItemsIsTheOrderTheReportReads() {
        final @NotNull String project = Projects.approved();
        final @NotNull String category = Structure.categoryId(project, "العظم");
        final @NotNull List<String> ids = new ArrayList<>(Structure.of(project).getList("find { it.name == 'العظم' }.items.id"));
        Collections.reverse(ids);
        asAdmin("POST", "/projects/" + project + "/categories/" + category + "/items/reorder", Map.of("ids", ids)).then().statusCode(200);
        assertEquals(Structure.of(project).getList("find { it.name == 'العظم' }.items.id"), ids);
    }

    @Test(description = "An order must name every row exactly once", testName = "1268e6dc-f30a-48c8-a4cc-814d4f168f12", priority = 47)
    public void anOrderMustNameEveryRowExactlyOnce() {
        final @NotNull String project = Projects.approved();
        final @NotNull List<String> ids = Structure.of(project).getList("id");
        refusedAs(asAdmin("POST", "/projects/" + project + "/categories/reorder", Map.of("ids", ids.subList(1, ids.size()))), 400, "invalidOrder");
        final @NotNull List<String> twice = new ArrayList<>(ids);
        twice.add(ids.getFirst());
        refusedAs(asAdmin("POST", "/projects/" + project + "/categories/reorder", Map.of("ids", twice)), 400, "invalidOrder");
        final @NotNull String category = Structure.categoryId(project, "الإشراف");
        refusedAs(asAdmin("POST", "/projects/" + project + "/categories/" + category + "/items/reorder", Map.of("ids", List.of(UUID.randomUUID().toString()))), 400, "invalidOrder");
        assertEquals(Structure.of(project).getList("name"), Structure.TEMPLATE_CATEGORIES);
    }

    @Test(description = "A disabled item disappears from entry and stays listed", testName = "39cb2598-ad12-41f0-b475-727b0af2739c", priority = 48)
    public void aDisabledItemDisappearsFromEntryAndStaysListed() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        given(Api.asAdmin()).post("/projects/" + project + "/items/" + item + "/disable").then().statusCode(200);
        assertFalse(enteredItemIds(Api.asAdmin(), project).contains(item));
        assertEquals(Structure.item(project, Structure.PURCHASES).get("isActive"), false);
    }

    @Test(description = "Nothing in the structure can be deleted", testName = "796448d9-5db2-4389-9db2-a15c8f4b0e51", priority = 49)
    public void nothingInTheStructureCanBeDeleted() {
        final @NotNull String project = Projects.approved();
        given(Api.asAdmin()).delete("/projects/" + project + "/categories/" + Structure.categoryId(project, "الإشراف")).then().statusCode(405);
        given(Api.asAdmin()).delete("/projects/" + project + "/items/" + Structure.itemId(project, Structure.PURCHASES)).then().statusCode(405);
        assertEquals(Structure.of(project).getList("name"), Structure.TEMPLATE_CATEGORIES);
    }

    @Test(description = "A disabled category takes its items out of entry and stays listed", testName = "fb5abc46-2bd1-4441-a3c4-95c6c7ba3446", priority = 50)
    public void aDisabledCategoryTakesItsItemsOutOfEntryAndStaysListed() {
        final @NotNull String project = Projects.approved();
        final @NotNull List<String> items = Structure.of(project).getList("find { it.name == 'العظم' }.items.id");
        given(Api.asAdmin()).post("/projects/" + project + "/categories/" + Structure.categoryId(project, "العظم") + "/disable").then().statusCode(200);
        assertTrue(items.stream().noneMatch(enteredItemIds(Api.asAdmin(), project)::contains));
        assertEquals(Structure.of(project).getString("find { it.name == 'العظم' }.isActive"), "false");
    }

    @Test(description = "A disabled item comes back when enabled", testName = "32850aba-f7ec-41d5-8c39-4f23dc17ae82", priority = 51)
    public void aDisabledItemComesBackWhenEnabled() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        given(Api.asAdmin()).post("/projects/" + project + "/items/" + item + "/disable").then().statusCode(200);
        given(Api.asAdmin()).post("/projects/" + project + "/items/" + item + "/enable").then().statusCode(200);
        assertTrue(enteredItemIds(Api.asAdmin(), project).contains(item));
        assertEquals(Structure.item(project, Structure.PURCHASES).get("isActive"), true);
    }

    @Test(description = "A purchases item is not offered for contractor payments", testName = "062c8c7d-f94f-4658-9af1-8c48bba6627c", priority = 52)
    public void aPurchasesItemIsNotOfferedForContractorPayments() {
        final @NotNull String project = Projects.approved();
        assertFalse(offeredItemNames(project, "contractorPayments").contains(Structure.PURCHASES));
        assertTrue(offeredItemNames(project, "purchases").contains(Structure.PURCHASES));
    }

    @Test(description = "A contractor payments item is not offered for purchases", testName = "ab91d5fc-b11d-449c-abce-85f50f3eda0b", priority = 53)
    public void aContractorPaymentsItemIsNotOfferedForPurchases() {
        final @NotNull String project = Projects.approved();
        assertFalse(offeredItemNames(project, "purchases").contains(Structure.CONTRACTOR_PAYMENTS));
        assertTrue(offeredItemNames(project, "contractorPayments").contains(Structure.CONTRACTOR_PAYMENTS));
        given(Api.asAdmin()).get("/projects/" + project + "/categories/offered?itemType=sideways").then().statusCode(400);
    }

    @Test(description = "A new item is offered to field at once", testName = "de7dc3b8-cd9b-4506-b7e2-1879315346f6", priority = 54)
    public void aNewItemIsOfferedToFieldAtOnce() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person field = People.in(project, "recordEntries");
        final @NotNull String item = addItem(project, Structure.categoryId(project, "الإشراف"), "Safety", "purchases");
        assertTrue(enteredItemIds(field.asked(), project).contains(item));
    }

    @Test(description = "A category an item or a subgroup needs a name of at most100 characters", testName = "ee865f9e-e950-40b5-ac2c-f2833899070d", priority = 55)
    public void aCategoryAnItemOrASubgroupNeedsANameOfAtMost100Characters() {
        final @NotNull String project = Projects.approved();
        final @NotNull String category = Structure.categoryId(project, "الإشراف");
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        asAdmin("POST", "/projects/" + project + "/categories", Map.of("name", "  " + "c".repeat(100) + "  ")).then().statusCode(201).body("name", equalTo("c".repeat(100)));
        refusedAs(asAdmin("POST", "/projects/" + project + "/categories", Map.of("name", "c".repeat(101))), 400, "invalidName");
        refusedAs(asAdmin("POST", "/projects/" + project + "/categories", Map.of("name", "   ")), 400, "invalidName");
        refusedAs(asAdmin("POST", "/projects/" + project + "/categories/" + category + "/items", Map.of("name", "i".repeat(101), "type", "both")), 400, "invalidName");
        refusedAs(asAdmin("POST", "/projects/" + project + "/items/" + item + "/subgroups", Map.of("name", "s".repeat(101))), 400, "invalidName");
        refusedAs(asAdmin("PUT", "/projects/" + project + "/items/" + item, Map.of("name", "")), 400, "invalidName");
        Structure.addSubgroup(project, item, "s".repeat(100));
    }

    @Test(description = "The general subgroup has no name to change", testName = "685d6eaf-1830-4583-90da-152991819b06", priority = 56)
    public void theGeneralSubgroupHasNoNameToChange() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        refusedAs(asAdmin("PUT", "/projects/" + project + "/items/" + item + "/subgroups/" + generalOf(project, item), Map.of("name", "General")), 409, "generalSubgroup");
    }

    @Test(description = "Everyone with access reads the structure and only finance changes it", testName = "4c1ca8b1-c36b-4852-a5bb-0c9778d0bf13", priority = 57)
    public void everyoneWithAccessReadsTheStructureAndOnlyFinanceChangesIt() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person reader = People.in(project);
        given(reader.asked()).get("/projects/" + project + "/categories").then().statusCode(200).body("name", equalTo(Structure.TEMPLATE_CATEGORIES));
        given(reader.asked()).body(Map.of("name", "Custom works")).post("/projects/" + project + "/categories").then().statusCode(403);
        given(People.in(project, "manageStructure").asked()).body(Map.of("name", "Custom works")).post("/projects/" + project + "/categories").then().statusCode(201);
        given(People.in(Projects.approved()).asked()).get("/projects/" + project + "/categories").then().statusCode(403);
    }

    @Test(description = "A finished project refuses every change and stays readable", testName = "257818a8-e014-4f01-b909-896671373ccb", priority = 58)
    public void aFinishedProjectRefusesEveryChangeAndStaysReadable() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.BOTH);
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);
        refusedAs(asAdmin("POST", "/projects/" + project + "/categories", Map.of("name", "Custom works")), 409, "projectFinished");
        refusedAs(asAdmin("PUT", "/projects/" + project + "/items/" + item, Map.of("name", "Renamed")), 409, "projectFinished");
        refusedAs(asAdmin("POST", "/projects/" + project + "/items/" + item + "/subgroups", Map.of("name", "Villa 1")), 409, "projectFinished");
        refusedAs(changeType(project, item, "purchases"), 409, "projectFinished");
        assertEquals(Structure.of(project).getList("name"), Structure.TEMPLATE_CATEGORIES);
    }

    @Test(description = "A subgroup revenue is kept on is not removed", testName = "bffdcbd1-9d34-4ce8-a735-a2fcc9f74aa1", priority = 59, enabled = false)
    public void aSubgroupRevenueIsKeptOnIsNotRemoved() {
        throw new AssertionError("a revenue item takes no sub-table (itemTakesNoPurchases), so revenue sits only on its general subgroup, which is never removed; "
                + "an expense on a subgroup is the 13th test");
    }
}
