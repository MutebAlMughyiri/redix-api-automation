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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class SubmitAnEntryForApprovalTest {
    private static final @NotNull String NOBODY = "00000000-0000-0000-0000-000000000000";

    private static @NotNull Map<String, Object> purchase(final @NotNull String projectId) {
        return Entries.purchaseOf(projectId, Structure.itemId(projectId, Structure.PURCHASES));
    }

    private static @NotNull JsonPath recorded(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull Map<String, Object> body) {
        return Entries.record(as, projectId, body).then().statusCode(201).extract().jsonPath();
    }

    private static @NotNull Response submit(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull String entryId) {
        return given(as).post("/projects/" + projectId + "/entries/" + entryId + "/submit");
    }

    private static void reject(final @NotNull String projectId, final @NotNull String entryId) {
        given(Api.asAdmin()).body(Map.of("reason", "Wrong supplier")).post("/projects/" + projectId + "/entries/" + entryId + "/reject").then().statusCode(200);
    }

    private static @NotNull JsonPath read(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull String entryId) {
        return given(as).get("/projects/" + projectId + "/entries/" + entryId).then().statusCode(200).extract().jsonPath();
    }

    private static int numberOf(final @NotNull String projectId, final @NotNull String entryId) {
        return read(Api.asAdmin(), projectId, entryId).getInt("entry.number");
    }

    private static @NotNull Person field(final @NotNull String projectId) {
        return People.in(projectId, "recordEntries", "chargeAnyAccount");
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    @Test(description = "A draft with its file is sent and is pending", testName = "3d46d196-11cc-4735-a600-4e9e3bcbb6b1", priority = 1)
    public void aDraftWithItsFileIsSentAndIsPending() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        body.put("attachmentId", Files.upload(Api.asAdmin(), "/projects/" + project + "/attachments", "invoice.png", Files.png(2048), "image/png").then().statusCode(201)
                .extract().path("id"));
        final @NotNull String id = recorded(Api.asAdmin(), project, body).getString("id");
        submit(Api.asAdmin(), project, id).then().statusCode(200).body("entry.status", equalTo("pendingApproval"));
    }

    @Test(description = "A draft with no file is sent", testName = "eb29ba5d-f9ce-4e7d-9b7b-5559235bae91", priority = 2)
    public void aDraftWithNoFileIsSent() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = recorded(Api.asAdmin(), project, purchase(project)).getString("id");
        submit(Api.asAdmin(), project, id).then().statusCode(200).body("entry.status", equalTo("pendingApproval")).body("attachments", equalTo(List.of()));
    }

    @Test(description = "Every required column left empty is named", testName = "9aeb1f24-c627-435d-8fd7-d596f4fff837", priority = 3)
    public void everyRequiredColumnLeftEmptyIsNamed() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull String columns = "/projects/" + project + "/items/" + body.get("itemId") + "/columns";
        for (final String name : List.of("Truck number", "Driver")) {
            given(Api.asAdmin()).body(Map.of("name", name, "dataType", "text", "isRequired", true, "showInReports", false, "choices", List.of())).post(columns).then().statusCode(200);
        }
        final @NotNull List<String> keys = given(Api.asAdmin()).get(columns).then().extract().path("findAll { it.isCustom }.key");
        final @NotNull String id = recorded(Api.asAdmin(), project, body).getString("id");
        submit(Api.asAdmin(), project, id).then().statusCode(400).body("code", equalTo("entryIncomplete")).body("missing", hasItems(keys.toArray()));
    }

    @Test(description = "What a draft holds is what answers its columns", testName = "0658d2c4-9eba-4139-9405-21c86290a3dc", priority = 4)
    public void whatADraftHoldsIsWhatAnswersItsColumns() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull String columns = "/projects/" + project + "/items/" + body.get("itemId") + "/columns";
        given(Api.asAdmin()).body(Map.of("name", "Truck number", "dataType", "text", "isRequired", true, "showInReports", false, "choices", List.of())).post(columns).then().statusCode(200);
        final @NotNull String key = given(Api.asAdmin()).get(columns).then().extract().path("find { it.isCustom }.key");
        final @NotNull Map<String, Object> values = new HashMap<>(Map.of("unitPrice", "100", "quantity", "2", key, "Truck 7"));
        body.put("columnValues", values);
        submit(Api.asAdmin(), project, recorded(Api.asAdmin(), project, body).getString("id")).then().statusCode(200);
    }

    @Test(description = "A draft contractor payment is sent with or without its receipt and waits on its contract", testName = "798ad66e-ac39-471e-92a1-d3e36c9d0c0d", priority = 5)
    public void aDraftContractorPaymentIsSentWithOrWithoutItsReceiptAndWaitsOnItsContract() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.CONTRACTOR_PAYMENTS);
        final @NotNull String contract = Contracts.add(project, item, "Contractor Co");
        submit(Api.asAdmin(), project, Entries.payment(project, item, contract, 1500, false)).then().statusCode(200);
        final @NotNull Map<String, Object> withReceipt = Entries.paymentOf(project, item, contract, 500);
        withReceipt.put("attachmentId", Files.upload(Api.asAdmin(), "/projects/" + project + "/attachments", "receipt.png", Files.png(2048), "image/png").then().statusCode(201)
                .extract().path("id"));
        submit(Api.asAdmin(), project, recorded(Api.asAdmin(), project, withReceipt).getString("id")).then().statusCode(200);
        assertEquals(given(Api.asAdmin()).get("/projects/" + project + "/contracts").then().extract().jsonPath().getDouble("find { it.id == '" + contract + "' }.pending"), 2000.0);
    }

    @Test(description = "A rejected entry can be sent again", testName = "dbadbbcd-4507-4191-84b0-d7dceb909680", priority = 6)
    public void aRejectedEntryCanBeSentAgain() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = Entries.waiting(project);
        reject(project, id);
        submit(Api.asAdmin(), project, id).then().statusCode(200).body("entry.status", equalTo("pendingApproval"));
    }

    @Test(description = "An entry already sent cannot be sent again", testName = "807e3fd9-e83d-4976-bf96-c7a67986f926", priority = 7)
    public void anEntryAlreadySentCannotBeSentAgain() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = Entries.waiting(project);
        refusedAs(submit(Api.asAdmin(), project, id), 409, "entryNotSubmittable");
        Entries.approve(project, id);
        refusedAs(submit(Api.asAdmin(), project, id), 409, "entryNotSubmittable");
    }

    @Test(description = "Field sends their own entry and not anothers and finance sends anyones rejected entry but not their draft", testName = "f842e83f-124c-4d00-8c33-db16fd6aa149", priority = 8)
    public void fieldSendsTheirOwnEntryAndNotAnothersAndFinanceSendsAnyonesRejectedEntryButNotTheirDraft() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person author = field(project);
        final @NotNull Person other = field(project);
        final @NotNull Person finance = People.in(project, "recordEntries", "chargeAnyAccount", "manageAnyEntry");
        final @NotNull String draft = recorded(author.asked(), project, purchase(project)).getString("id");
        refusedAs(submit(other.asked(), project, draft), 403, "notYourEntry");
        refusedAs(submit(finance.asked(), project, draft), 403, "notYourEntry");
        submit(author.asked(), project, draft).then().statusCode(200);
        reject(project, draft);
        submit(finance.asked(), project, draft).then().statusCode(200);
    }

    @Test(description = "A draft is its authors alone until it is sent", testName = "70cbec59-672b-45ec-9e66-54474b56e065", priority = 9)
    public void aDraftIsItsAuthorsAloneUntilItIsSent() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person author = field(project);
        final @NotNull Person other = People.in(project, "recordEntries", "chargeAnyAccount", "manageAnyEntry");
        final @NotNull String draft = recorded(author.asked(), project, purchase(project)).getString("id");
        given(other.asked()).get("/projects/" + project + "/entries/" + draft).then().statusCode(404);
        assertTrue(!given(other.asked()).get("/projects/" + project + "/entries").then().statusCode(200).extract().jsonPath().getList("id").contains(draft));
        submit(author.asked(), project, draft).then().statusCode(200);
        read(other.asked(), project, draft);
    }

    @Test(description = "Who holds no record entries cannot send an entry", testName = "7f1ace3a-e338-4e52-8b1e-38dffe48a546", priority = 10)
    public void whoHoldsNoRecordEntriesCannotSendAnEntry() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person author = field(project);
        final @NotNull String draft = recorded(author.asked(), project, purchase(project)).getString("id");
        given(Api.asAdmin()).delete("/projects/" + project + "/users/" + author.id() + "/permissions/recordEntries").then().statusCode(200);
        submit(author.asked(), project, draft).then().statusCode(403);
    }

    @Test(description = "Row_201_an opened entry reads itself by its ID", testName = "41555254-ac3e-4d95-a0a6-fe3a3a6eca39", priority = 11)
    public void row201AnOpenedEntryReadsItselfByItsId() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = Entries.waiting(project);
        given(Api.asAdmin()).get("/projects/" + project + "/entries/" + id + "/row").then().statusCode(200).body("id", equalTo(id)).body("status", equalTo("pendingApproval"));
    }

    @Test(description = "Row_201_someone elses draft and an entry that does not exist are not found", testName = "90080583-c336-45f8-ad02-20c4e4bc34dc", priority = 12)
    public void row201SomeoneElsesDraftAndAnEntryThatDoesNotExistAreNotFound() {
        final @NotNull String project = Projects.approved();
        final @NotNull String draft = recorded(field(project).asked(), project, purchase(project)).getString("id");
        given(field(project).asked()).get("/projects/" + project + "/entries/" + draft + "/row").then().statusCode(404);
        given(Api.asAdmin()).get("/projects/" + project + "/entries/" + UUID.randomUUID() + "/row").then().statusCode(404);
    }

    @Test(description = "An entry of another project is not found and someone outside is refused", testName = "f17feaf7-0c34-4ece-884d-021d3e86cda0", priority = 13)
    public void anEntryOfAnotherProjectIsNotFoundAndSomeoneOutsideIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull String theirs = Entries.draft(Projects.approved());
        submit(Api.asAdmin(), project, theirs).then().statusCode(404);
        submit(People.in(Projects.approved(), "recordEntries").asked(), project, Entries.draft(project)).then().statusCode(403);
    }

    @Test(description = "A finished project sends nothing", testName = "dff237bb-4d27-400c-8803-6985b94ab148", priority = 14)
    public void aFinishedProjectSendsNothing() {
        final @NotNull String project = Projects.approved();
        final @NotNull String draft = Entries.draft(project);
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);
        refusedAs(submit(Api.asAdmin(), project, draft), 409, "projectFinished");
    }

    @Test(description = "Sending is in the history and a refusal is not", testName = "2f4270e5-3675-4d58-b7c8-45d8fd13ed51", priority = 15, enabled = false)
    public void sendingIsInTheHistoryAndARefusalIsNot() {
        throw new AssertionError("History has no route that reads an entry's own lines over HTTP; Go's internal/entries tests prove it");
    }

    @Test(description = "A kept entry has a number and the next one kept in the project has the next", testName = "7e6fac86-b872-49af-9f77-29ae68509c3a", priority = 16)
    public void aKeptEntryHasANumberAndTheNextOneKeptInTheProjectHasTheNext() {
        final @NotNull String project = Projects.approved();
        final int first = numberOf(project, Entries.draft(project));
        assertEquals(numberOf(project, Entries.draft(project)), first + 1);
    }

    @Test(description = "Each project counts its own and takes none of another projects numbers", testName = "9c896fa7-dc90-4f81-828f-211c3c28a14b", priority = 17)
    public void eachProjectCountsItsOwnAndTakesNoneOfAnotherProjectsNumbers() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final int first = numberOf(project, Entries.draft(project));
        final int theirs = numberOf(other, Entries.draft(other));
        assertEquals(theirs, first, "each project counts from its own start");
        assertEquals(numberOf(project, Entries.draft(project)), first + 1);
    }

    @Test(description = "A draft and a pending entry have their number and a deleted draft leaves its number unused", testName = "84b43c85-df9a-43cd-89b1-f0e0e120ebd3", priority = 18)
    public void aDraftAndAPendingEntryHaveTheirNumberAndADeletedDraftLeavesItsNumberUnused() {
        final @NotNull String project = Projects.approved();
        final @NotNull String draft = Entries.draft(project);
        final int number = numberOf(project, draft);
        assertEquals(numberOf(project, Entries.waiting(project)), number + 1);
        given(Api.asAdmin()).delete("/projects/" + project + "/entries/" + draft).then().statusCode(200);
        assertEquals(numberOf(project, Entries.draft(project)), number + 2, "the deleted draft's number stays unused");
    }

    @Test(description = "An upload sent again answers its number and takes no other", testName = "1dd1033b-6e02-4367-9644-4f0dd3395716", priority = 19)
    public void anUploadSentAgainAnswersItsNumberAndTakesNoOther() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull String id = recorded(Api.asAdmin(), project, body).getString("id");
        final int number = numberOf(project, id);
        Entries.record(Api.asAdmin(), project, body).then().statusCode(200).body("number", equalTo(number));
        assertEquals(numberOf(project, Entries.draft(project)), number + 1);
    }

    @Test(description = "Changing moving approving removing and restoring an entry keeps its number", testName = "3a8bc124-fecc-4968-914c-8f9936ce0954", priority = 20)
    public void changingMovingApprovingRemovingAndRestoringAnEntryKeepsItsNumber() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull String id = recorded(Api.asAdmin(), project, body).getString("id");
        final int number = numberOf(project, id);
        final @NotNull Map<String, Object> moved = Entries.purchaseOf(project, Structure.itemId(project, "الخرسانة"));
        moved.putAll(Map.of("id", id, "amount", 350));
        moved.remove("submit");
        given(Api.asAdmin()).body(moved).put("/projects/" + project + "/entries/" + id).then().statusCode(200);
        assertEquals(numberOf(project, id), number);
        Entries.submit(project, id);
        Entries.approve(project, id);
        assertEquals(numberOf(project, id), number);
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + People.admin().userId() + "/permissions/removeApprovedEntries").then().statusCode(200);
        given(Api.asAdmin()).delete("/projects/" + project + "/entries/" + id).then().statusCode(200);
        given(Api.asAdmin()).post("/projects/" + project + "/entries/" + id + "/restore").then().statusCode(200);
        assertEquals(numberOf(project, id), number);
    }

    @Test(description = "Entries arriving at the same moment each take a number of their own", testName = "4978d384-f6ab-4f9f-83dd-d3cde0603d94", priority = 21)
    public void entriesArrivingAtTheSameMomentEachTakeANumberOfTheirOwn() {
        final @NotNull String project = Projects.approved();
        Entries.draft(project);
        final @NotNull List<Integer> numbers = IntStream.range(0, 8).parallel().mapToObj(i -> Entries.record(Api.asAdmin(), project, purchase(project)).then().statusCode(201)
                .extract().jsonPath().getString("id")).map(id -> numberOf(project, id)).toList();
        assertEquals(numbers.stream().distinct().count(), 8L, "numbers " + numbers);
    }

    @Test(description = "The database refuses a second entry of the project with the same number", testName = "a026023e-1cc4-4637-a2b4-bc661078bdac", priority = 22, enabled = false)
    public void theDatabaseRefusesASecondEntryOfTheProjectWithTheSameNumber() {
        throw new AssertionError("about the table's unique index, inside the server; entries arriving at once is the 21st test");
    }

    @Test(description = "The number filter lists that entry alone and a number no entry has lists nothing", testName = "86b411a7-266f-4a1b-8ec8-e02764bf8cea", priority = 23)
    public void theNumberFilterListsThatEntryAloneAndANumberNoEntryHasListsNothing() {
        final @NotNull String project = Projects.approved();
        Entries.draft(project);
        final @NotNull String id = Entries.draft(project);
        final int number = numberOf(project, id);
        Entries.draft(project);
        assertEquals(given(Api.asAdmin()).queryParam("number", number).get("/projects/" + project + "/entries").then().statusCode(200).extract().path("id"), List.of(id));
        assertEquals(given(Api.asAdmin()).queryParam("number", 9999).get("/projects/" + project + "/entries").then().statusCode(200).extract().path("id"), List.of());
    }

    @Test(description = "A number of an entry the person may not see lists nothing as a number no entry has", testName = "bd8a89c3-5db3-4c24-ae13-6fb10766b449", priority = 24)
    public void aNumberOfAnEntryThePersonMayNotSeeListsNothingAsANumberNoEntryHas() {
        final @NotNull String project = Projects.approved();
        final int number = numberOf(project, Entries.draft(project));
        assertEquals(given(field(project).asked()).queryParam("number", number).get("/projects/" + project + "/entries").then().statusCode(200).extract().path("id"), List.of());
    }

    @Test(description = "Entry numbers_every entry is numbered in its project by its date then when it was made and every figure stays", testName = "11b9f541-164e-4ad7-92fa-acede0e327c3", priority = 25, enabled = false)
    public void entryNumbersEveryEntryIsNumberedInItsProjectByItsDateThenWhenItWasMadeAndEveryFigureStays() {
        throw new AssertionError("about the migration that numbered the entries that existed before, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "Reading a draft answers what its kind has and empty for the rest", testName = "a453c652-bc46-4a55-ba1d-72eac58ed696", priority = 26)
    public void readingADraftAnswersWhatItsKindHasAndEmptyForTheRest() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = purchase(project);
        final @NotNull JsonPath draft = read(Api.asAdmin(), project, recorded(Api.asAdmin(), project, body).getString("id"));
        assertEquals(draft.getString("entry.status"), "draft");
        assertEquals(draft.getString("expense.itemId"), body.get("itemId"));
        assertEquals(draft.getString("purchase.supplierId"), NOBODY);
        assertEquals(draft.getString("contractorPayment.contractId"), NOBODY);
        assertEquals(draft.getList("attachments"), List.of());
    }

    @Test(description = "Reading a credit note answers what it is linked to", testName = "9f5ce5be-d721-4257-90cc-2e52effe5d45", priority = 27)
    public void readingACreditNoteAnswersWhatItIsLinkedTo() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull Map<String, Object> credit = Entries.purchaseOf(project, item);
        final @NotNull String returned = given(Api.asAdmin()).get("/projects/" + project + "/items/" + item + "/columns").then().extract()
                .path("find { it.key == 'entryType' }.choices.find { it.name == 'مرتجع' }.id");
        credit.putAll(Map.of("amount", 200, "columnValues", Map.of("entryType", returned)));
        final @NotNull JsonPath read = read(Api.asAdmin(), project, recorded(Api.asAdmin(), project, credit).getString("id"));
        assertEquals(read.getDouble("entry.amount"), -200.0);
        assertEquals(read.getString("expense.itemId"), item);
        assertEquals(read.getString("tableId"), credit.get("tableId"));
    }

    @Test(description = "A rejected revenue entry is sent again and waits once more", testName = "fa04e901-8a56-40f8-99a9-fd1e1821329e", priority = 28)
    public void aRejectedRevenueEntryIsSentAgainAndWaitsOnceMore() {
        final @NotNull String project = Projects.approved();
        given(Api.asAdmin()).body(Map.of("name", "Abdullah")).post("/projects/" + project + "/partners").then().statusCode(200);
        final @NotNull String partner = given(Api.asAdmin()).get("/projects/" + project + "/partners").then().extract().path("[0].id");
        final @NotNull String movement = Entries.capital(project, partner, Api.asAdmin(), false, 5000);
        Entries.submit(project, movement);
        reject(project, movement);
        submit(Api.asAdmin(), project, movement).then().statusCode(200).body("entry.status", equalTo("pendingApproval"));
    }

    @Test(description = "One partners money read by its ID is left to that partner and whoever sees all capital", testName = "7d4ff924-a70b-4759-bcf3-2355c7ff3230", priority = 29)
    public void onePartnersMoneyReadByItsIdIsLeftToThatPartnerAndWhoeverSeesAllCapital() {
        final @NotNull String project = Projects.approved();
        given(Api.asAdmin()).body(Map.of("name", "Abdullah")).post("/projects/" + project + "/partners").then().statusCode(200);
        final @NotNull String partner = given(Api.asAdmin()).get("/projects/" + project + "/partners").then().extract().path("[0].id");
        final @NotNull String movement = Entries.capital(project, partner, Api.asAdmin(), false, 5000);
        Entries.submit(project, movement);
        given(People.in(project).asked()).get("/projects/" + project + "/entries/" + movement).then().statusCode(404);
        final @NotNull Person chosen = People.in(project);
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + chosen.id() + "/partners/" + partner).then().statusCode(200);
        read(chosen.asked(), project, movement);
        read(People.in(project, "seeAllCapital").asked(), project, movement);
    }
}
