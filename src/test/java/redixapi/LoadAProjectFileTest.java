// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Entries;
import redix.support.Files;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Structure;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class LoadAProjectFileTest {
    private static final @NotNull String BANK = "حساب المؤسسة";

    /** A project file the .NET API's tests sent, kept under src/test/resources/projectfiles. */
    private static @NotNull String file(final @NotNull String name) {
        try (final InputStream kept = LoadAProjectFileTest.class.getResourceAsStream("/projectfiles/" + name + ".json")) {
            if (kept == null) {
                throw new AssertionError("no project file " + name);
            }
            return new String(kept.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException reading) {
            throw new UncheckedIOException(reading);
        }
    }

    /** An approved project started on 1 Nov 2026, before the files' rows, holding the files' bank, which the admin manages. */
    private static @NotNull String project() {
        final @NotNull String id = given(Api.asAdmin()).body(Map.of("name", "Loaded " + UUID.randomUUID().toString().substring(0, 8), "startDate", "2026-11-01", "units",
                List.of("A1"), "partners", List.of())).post("/projects").then().statusCode(201).extract().path("id");
        given(Api.asAdmin()).post("/projects/" + id + "/approve").then().statusCode(200);
        given(Api.asAdmin()).body(Map.of("type", "bank", "name", BANK)).post("/projects/" + id + "/accounts").then().statusCode(200);
        return id;
    }

    private static @NotNull Response load(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull String text) {
        return Files.upload(as, "/projects/" + projectId + "/project-file", "project.json", text.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    private static @NotNull JsonPath loaded(final @NotNull String projectId, final @NotNull String text) {
        return load(Api.asAdmin(), projectId, text).then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull JsonPath entries(final @NotNull String projectId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/entries").then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull JsonPath reports(final @NotNull String projectId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/periodic-reports").then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull JsonPath refused(final @NotNull String projectId, final @NotNull String text) {
        return load(Api.asAdmin(), projectId, text).then().statusCode(400).body("code", equalTo("rowsRefused")).extract().jsonPath();
    }

    private static void nothingSaved(final @NotNull String projectId) {
        assertEquals(entries(projectId).getList("$"), List.of(), "an entry was saved");
        assertEquals(reports(projectId).getList("$"), List.of(), "a report was issued");
    }

    @Test(description = "A loaded files rows are numbered by their date then the files order not the order of its tables", testName = "836f72ca-a01c-401b-97c9-83de1334878b", priority = 1)
    public void aLoadedFilesRowsAreNumberedByTheirDateThenTheFilesOrderNotTheOrderOfItsTables() {
        final @NotNull String project = project();
        loaded(project, file("full").replace("\"description\":\"\\u0635\\u0646\\u0641\",\"supplier\":\"supplier-1\",\"date\":\"2026-11-02\"",
                "\"description\":\"\\u0635\\u0646\\u0641\",\"supplier\":\"supplier-1\",\"date\":\"2026-11-01\""));
        final @NotNull JsonPath listed = entries(project);
        final int first = listed.getInt("min { it.number }.number");
        assertEquals(listed.getString("find { it.number == " + first + " }.description"), "صنف", "the earliest date is numbered first");
        assertEquals(listed.getString("find { it.number == " + (first + 1) + " }.description"), "بلاط أرضي", "then the file's order");
        assertEquals(listed.getString("find { it.number == " + (first + 2) + " }.description"), "بلاط مرتجع");
    }

    @Test(description = "A file that is not a redix project file is refused and nothing is read", testName = "e6458fd6-3c88-44a8-afe0-9b9bae40ffda", priority = 2)
    public void aFileThatIsNotARedixProjectFileIsRefusedAndNothingIsRead() {
        final @NotNull String project = project();
        for (final String text : List.of("{ \"format\": \"another.app\", \"version\": 1 }", "not a file at all", file("full").replace("\"categories\":[", "\"categories\":{"))) {
            load(Api.asAdmin(), project, text).then().statusCode(400).body("code", equalTo("notAProjectFile"));
        }
        nothingSaved(project);
    }

    @Test(description = "A file of a newer version is refused before its shape is read", testName = "1da235f7-ec5a-462a-a3fb-c26543672f0e", priority = 3)
    public void aFileOfANewerVersionIsRefusedBeforeItsShapeIsRead() {
        final @NotNull String project = project();
        load(Api.asAdmin(), project, "{ \"format\": \"redix.project\", \"version\": 2, \"categories\": \"nothing like a list\" }").then().statusCode(400)
                .body("code", equalTo("newerVersion"));
    }

    @Test(description = "A project file is read with its report its actual balances its lists and its tables", testName = "44ccc79a-933c-42f2-b756-00483a537f05", priority = 4)
    public void aProjectFileIsReadWithItsReportItsActualBalancesItsListsAndItsTables() {
        final @NotNull JsonPath answer = loaded(project(), file("full"));
        assertEquals(answer.getInt("entries"), 8);
        assertEquals(answer.getInt("tables"), 6);
        assertEquals(answer.getInt("report"), 7);
    }

    @Test(description = "A file with no report no accounts and no rows is read as its structure alone", testName = "4c861dd7-bd79-4d20-a43c-4fe8ae9116db", priority = 5)
    public void aFileWithNoReportNoAccountsAndNoRowsIsReadAsItsStructureAlone() {
        final @NotNull JsonPath answer = loaded(project(), file("structure-only"));
        assertEquals(answer.getInt("entries"), 0);
        assertEquals(answer.getInt("tables"), 0);
        assertEquals(answer.getInt("report"), 0);
    }

    @Test(description = "An entry recorded for a project file passes the checks of add entry", testName = "adb9da71-cec1-4013-9927-eb116c53e652", priority = 6)
    public void anEntryRecordedForAProjectFilePassesTheChecksOfAddEntry() {
        final @NotNull String project = project();
        final @NotNull JsonPath rows = refused(project, file("rows-refused"));
        assertTrue(rows.getList("rows.findAll { it.owner == 'entry' }.code").contains("invalidDate"), "Add entry's own refusal: " + rows.prettify());
        nothingSaved(project);
    }

    @Test(description = "An entry recorded for a project file tells no decider while add entry tells them", testName = "fc905ba7-c73e-4ae5-9e49-a8c8668c9c69", priority = 7)
    public void anEntryRecordedForAProjectFileTellsNoDeciderWhileAddEntryTellsThem() {
        final @NotNull String project = project();
        final @NotNull Person decider = People.in(project, "approveEntries");
        loaded(project, file("full"));
        assertEquals(given(decider.asked()).get("/notifications").then().statusCode(200).extract().jsonPath().getList("$"), List.of());
        Entries.waiting(project);
        assertEquals(given(decider.asked()).get("/notifications").then().statusCode(200).extract().jsonPath().getList("kind"), List.of("entrySubmitted"));
    }

    @Test(description = "A report is issued with the number and the time it is given and the next one follows it", testName = "adb2fc1e-eca9-4470-b314-a6a993ecbf51", priority = 8)
    public void aReportIsIssuedWithTheNumberAndTheTimeItIsGivenAndTheNextOneFollowsIt() {
        final @NotNull String project = project();
        loaded(project, file("full"));
        assertEquals(reports(project).getInt("[0].number"), 7);
        assertEquals(reports(project).getString("[0].issuedAt"), "2026-11-10T06:30:00Z", "09:30 in Saudi Arabia");
        assertEquals(given(Api.asAdmin()).get("/projects/" + project + "/periodic-reports/next").then().statusCode(200).extract().jsonPath().getInt("number"), 8);
    }

    @Test(description = "A file loads its missing structure and every row approved in the managers name telling no decider", testName = "0cb82292-9a3b-469c-bf21-c79c1e63916a", priority = 9)
    public void aFileLoadsItsMissingStructureAndEveryRowApprovedInTheManagersNameTellingNoDecider() {
        final @NotNull String project = project();
        final @NotNull Person decider = People.in(project, "approveEntries");
        loaded(project, file("full"));
        final @NotNull JsonPath structure = Structure.of(project);
        assertEquals(structure.getList("find { it.name == 'فئة الاختبار' }.items.name"), List.of("بند الاختبار"));
        assertEquals(Structure.tablesOf(project, Structure.itemId(project, "بلاط")).getList("name"), List.of("بلاط", "مواد الاختبار", "مقاول الاختبار"));
        assertEquals(given(Api.asAdmin()).get("/projects/" + project + "/partners").then().extract().path("name"), List.of("شريك الاختبار"));
        assertTrue(given(Api.asAdmin()).get("/projects/" + project + "/suppliers").then().extract().jsonPath().getList("name").containsAll(List.of("شركة النخيل", "مورد الاختبار")));
        assertTrue(given(Api.asAdmin()).get("/projects/" + project + "/payment-methods").then().extract().jsonPath().getList("name").contains("طريقة الاختبار"));
        final @NotNull JsonPath listed = entries(project);
        assertEquals(listed.getList("status").stream().distinct().toList(), List.of("approved"));
        assertEquals(listed.getList("authorName").stream().distinct().toList(), List.of(People.admin().username()));
        assertEquals(given(decider.asked()).get("/notifications").then().extract().jsonPath().getList("$"), List.of());
    }

    @Test(description = "After the load the summary its revenue its expenses and its surplus equal the files", testName = "0a97bdbd-3630-43ba-85ff-bb691c036288", priority = 10)
    public void afterTheLoadTheSummaryItsRevenueItsExpensesAndItsSurplusEqualTheFiles() {
        final @NotNull String project = project();
        loaded(project, file("full"));
        final @NotNull JsonPath summary = given(Api.asAdmin()).get("/projects/" + project + "/summary").then().statusCode(200).extract().jsonPath();
        assertEquals(summary.getDouble("revenue"), 815.0);
        assertEquals(summary.getDouble("expenses"), 469.75);
        assertEquals(summary.getDouble("surplus"), 345.25);
    }

    @Test(description = "The files report is issued with its number and time and its actual balances are the latest reconciliation", testName = "a3772988-42fd-4972-9c9c-5e701377cbc7", priority = 11)
    public void theFilesReportIsIssuedWithItsNumberAndTimeAndItsActualBalancesAreTheLatestReconciliation() {
        final @NotNull String project = project();
        loaded(project, file("full"));
        assertEquals(reports(project).getDouble("[0].expenses"), 469.75);
        final @NotNull JsonPath latest = given(Api.asAdmin()).get("/projects/" + project + "/reconciliations/latest").then().statusCode(200).extract().jsonPath();
        assertEquals(latest.getString("balances[0].accountName"), BANK);
        assertEquals(latest.getDouble("balances[0].actualBalance"), 345.25);
        assertEquals(latest.getDouble("difference"), 0.0);
    }

    @Test(description = "A file with no report no accounts and no rows loads its structure alone and issues nothing", testName = "52569409-ddd7-478b-a527-c64137237e0d", priority = 12)
    public void aFileWithNoReportNoAccountsAndNoRowsLoadsItsStructureAloneAndIssuesNothing() {
        final @NotNull String project = project();
        final @NotNull List<String> before = Structure.of(project).getList("name");
        loaded(project, file("structure-only"));
        assertTrue(Structure.of(project).getList("name").size() >= before.size());
        nothingSaved(project);
    }

    @Test(description = "No loaded entry shows as new while one approved after the load does", testName = "2173a068-0a9e-4184-9f15-0f771fe93abb", priority = 13)
    public void noLoadedEntryShowsAsNewWhileOneApprovedAfterTheLoadDoes() {
        final @NotNull String project = project();
        loaded(project, file("full"));
        final @NotNull String tiles = Structure.itemId(project, "بلاط");
        final @NotNull String report = "/projects/" + project + "/reports/items/" + tiles;
        assertFalse(given(Api.asAdmin()).get(report).then().statusCode(200).extract().jsonPath().getList("subgroups.rows.flatten().isNew").contains(true));
        final @NotNull String later = Entries.draftIn(project, tiles, Structure.tablesOf(project, tiles).getString("find { it.name == 'بلاط' }.id"));
        Entries.submit(project, later);
        Entries.approve(project, later);
        assertTrue(given(Api.asAdmin()).get(report).then().statusCode(200).extract().jsonPath().getList("subgroups.rows.flatten().isNew").contains(true));
    }

    @Test(description = "Without manage project the route answers403 and nothing is saved", testName = "db601258-72c7-4eff-aefc-56923ea4a155", priority = 14)
    public void withoutManageProjectTheRouteAnswers403AndNothingIsSaved() {
        final @NotNull String project = project();
        load(People.in(project, "manageSettings", "manageStructure").asked(), project, file("full")).then().statusCode(403);
        nothingSaved(project);
    }

    @Test(description = "A project holding an entry says so and refuses the file with project has entries", testName = "4641e6ea-080a-4dc3-b487-9d673ceb0911", priority = 15)
    public void aProjectHoldingAnEntrySaysSoAndRefusesTheFileWithProjectHasEntries() {
        final @NotNull String project = project();
        given(Api.asAdmin()).get("/projects").then().body("find { it.id == '" + project + "' }.holdsEntries", equalTo(false));
        Entries.draft(project);
        given(Api.asAdmin()).get("/projects").then().body("find { it.id == '" + project + "' }.holdsEntries", equalTo(true));
        load(Api.asAdmin(), project, file("full")).then().statusCode(409).body("code", equalTo("projectHasEntries"));
    }

    @Test(description = "A row add entry refuses keeps its code every refused row is listed and nothing is saved", testName = "4800a69c-ff04-45c6-82da-a1a399b80d59", priority = 16)
    public void aRowAddEntryRefusesKeepsItsCodeEveryRefusedRowIsListedAndNothingIsSaved() {
        final @NotNull String project = project();
        final @NotNull JsonPath rows = refused(project, file("rows-refused"));
        assertTrue(rows.getList("rows").size() > 1, "every refused row is listed: " + rows.prettify());
        assertTrue(rows.getList("rows.code").containsAll(List.of("invalidDate", "attachmentNotInFile")), rows.prettify());
        assertFalse(rows.getString("rows[0].table").isEmpty() || rows.getString("rows[0].item").isEmpty());
        nothingSaved(project);
    }

    @Test(description = "An option the files lists do not hold or a choice the tables list does not have is an unknown choice naming its column", testName = "b5834a62-fca6-4cbe-b555-ff32d4d28300", priority = 17)
    public void anOptionTheFilesListsDoNotHoldOrAChoiceTheTablesListDoesNotHaveIsAnUnknownChoiceNamingItsColumn() {
        final @NotNull String project = project();
        final @NotNull JsonPath rows = refused(project, file("unknown-choice"));
        assertEquals(rows.getList("rows.code").stream().distinct().toList(), List.of("unknownChoice"));
        assertEquals(rows.getString("rows[0].columnKey"), "entryType");
        assertEquals(rows.getString("rows[0].value"), "entryType-9");
        nothingSaved(project);
    }

    @Test(description = "A part its owner refuses is listed with its owners code and nothing is saved", testName = "1b5cba16-a912-4e98-83fa-981318d1d4de", priority = 18)
    public void aPartItsOwnerRefusesIsListedWithItsOwnersCodeAndNothingIsSaved() {
        final @NotNull String project = project();
        final @NotNull JsonPath rows = refused(project, file("second-capital"));
        assertEquals(rows.getString("rows[0].owner"), "structure");
        assertEquals(rows.getString("rows[0].code"), "capitalItemExists");
        nothingSaved(project);
        assertFalse(Structure.of(project).getList("collectMany { it.items }.name").contains("رأس مال آخر"));
    }

    @Test(description = "An account of the actual balances the project lacks is refused and nothing is saved", testName = "22357970-0fa6-4811-bd33-16966d5161e1", priority = 19)
    public void anAccountOfTheActualBalancesTheProjectLacksIsRefusedAndNothingIsSaved() {
        final @NotNull String project = project();
        final @NotNull JsonPath rows = refused(project, file("account-missing"));
        assertEquals(rows.getString("rows[0].code"), "accountNotInProject");
        assertEquals(rows.getString("rows[0].value"), "حساب غير موجود");
        nothingSaved(project);
    }

    @Test(description = "The route refuses a file that is not a project file or is newer and nothing changes", testName = "79dfc286-6200-446a-bfb6-516cb98e4aa2", priority = 20)
    public void theRouteRefusesAFileThatIsNotAProjectFileOrIsNewerAndNothingChanges() {
        final @NotNull String project = project();
        final @NotNull List<String> structure = Structure.of(project).getList("collectMany { it.items }.name");
        load(Api.asAdmin(), project, "{ \"format\": \"another.app\", \"version\": 1 }").then().statusCode(400).body("code", equalTo("notAProjectFile"));
        load(Api.asAdmin(), project, "{ \"format\": \"redix.project\", \"version\": 2 }").then().statusCode(400).body("code", equalTo("newerVersion"));
        assertEquals(Structure.of(project).getList("collectMany { it.items }.name"), structure);
        nothingSaved(project);
    }

    @Test(description = "A file whose report the project already issued is refused before anything is saved", testName = "457fea48-dd1b-4d96-a1ce-e370920133a1", priority = 21)
    public void aFileWhoseReportTheProjectAlreadyIssuedIsRefusedBeforeAnythingIsSaved() {
        final @NotNull String project = project();
        given(Api.asAdmin()).post("/projects/" + project + "/periodic-reports").then().statusCode(200);
        final @NotNull JsonPath rows = refused(project, file("full").replace("\"report\":{\"number\":7", "\"report\":{\"number\":1"));
        assertEquals(rows.getString("rows[0].code"), "reportAlreadyIssued");
        assertEquals(rows.getString("rows[0].value"), "1");
        assertEquals(entries(project).getList("$"), List.of());
        assertEquals(reports(project).getList("number"), List.of(1));
    }

    @Test(description = "A file whose report number the project has not issued loads beside the reports before it", testName = "1cbb82a4-02f2-46df-bc20-d162b3c12d78", priority = 22)
    public void aFileWhoseReportNumberTheProjectHasNotIssuedLoadsBesideTheReportsBeforeIt() {
        final @NotNull String project = project();
        given(Api.asAdmin()).post("/projects/" + project + "/periodic-reports").then().statusCode(200);
        loaded(project, file("full"));
        assertTrue(reports(project).getList("number").containsAll(List.of(1, 7)), "reports " + reports(project).prettify());
    }
}
