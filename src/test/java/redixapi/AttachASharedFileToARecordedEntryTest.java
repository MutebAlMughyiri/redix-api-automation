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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class AttachASharedFileToARecordedEntryTest {
    private static @NotNull String file(final @NotNull String projectId) {
        return Files.upload(Api.asAdmin(), "/projects/" + projectId + "/attachments", "shared.png", Files.png(2048), "image/png").then().statusCode(201).extract().path("id");
    }

    private static @NotNull Response attach(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull String entryId, final @NotNull String fileId) {
        return given(as).body(Map.of("attachmentId", fileId)).post("/projects/" + projectId + "/entries/" + entryId + "/invoice");
    }

    private static @NotNull List<String> attachable(final @NotNull RequestSpecification as, final @NotNull String projectId) {
        return given(as).queryParam("attachable", true).get("/projects/" + projectId + "/entries").then().statusCode(200).extract().path("id");
    }

    private static @NotNull JsonPath read(final @NotNull String projectId, final @NotNull String entryId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/entries/" + entryId).then().statusCode(200).extract().jsonPath();
    }

    private static @NotNull String purchaseWith(final @NotNull String projectId, final @NotNull Map<String, Object> changes) {
        final @NotNull Map<String, Object> body = Entries.purchaseOf(projectId, Structure.itemId(projectId, Structure.PURCHASES));
        body.putAll(changes);
        return Entries.record(Api.asAdmin(), projectId, body).then().statusCode(201).extract().path("id");
    }

    private static @NotNull String payment(final @NotNull String projectId) {
        final @NotNull String item = Structure.itemId(projectId, Structure.CONTRACTOR_PAYMENTS);
        return Entries.payment(projectId, item, Contracts.add(projectId, item, "Contractor " + System.nanoTime()), 1000, false);
    }

    private static void refusedAs(final @NotNull Response answer, final int status, final @NotNull String code) {
        answer.then().statusCode(status).body("code", equalTo(code));
    }

    @Test(description = "10c_d5_every kind that pays something is listed with or without a file", testName = "d4917ab1-bb24-4b2d-81e9-9c1be17bcfb0", priority = 1)
    public void _10cD5EveryKindThatPaysSomethingIsListedWithOrWithoutAFile() {
        final @NotNull String project = Projects.approved();
        final @NotNull String bare = purchaseWith(project, Map.of("submit", true));
        final @NotNull String filed = purchaseWith(project, Map.of("submit", true, "attachmentId", file(project)));
        final @NotNull String paid = payment(project);
        Entries.submit(project, paid);
        assertTrue(attachable(Api.asAdmin(), project).containsAll(List.of(bare, filed, paid)));
    }

    @Test(description = "10c_a deduction a credit note and a removed entry are not listed and cannot take one", testName = "69a1a3c9-dc8e-4bd3-baf4-e3c50627bebe", priority = 2)
    public void _10cADeductionACreditNoteAndARemovedEntryAreNotListedAndCannotTakeOne() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.PURCHASES);
        final @NotNull String deduction = purchaseWith(project, Map.of("amount", -50, "description", "Discount", "submit", true));
        final @NotNull String returned = given(Api.asAdmin()).get("/projects/" + project + "/items/" + item + "/columns").then().extract()
                .path("find { it.key == 'entryType' }.choices.find { it.name == 'مرتجع' }.id");
        final @NotNull String credit = purchaseWith(project, Map.of("amount", 80, "columnValues", Map.of("entryType", returned), "submit", true));
        final @NotNull String removed = purchaseWith(project, Map.of("submit", true));
        Entries.approve(project, removed);
        given(Api.asAdmin()).post("/projects/" + project + "/users/" + People.admin().userId() + "/permissions/removeApprovedEntries").then().statusCode(200);
        given(Api.asAdmin()).delete("/projects/" + project + "/entries/" + removed).then().statusCode(200);
        final @NotNull List<String> listed = attachable(Api.asAdmin(), project);
        for (final String entry : List.of(deduction, credit, removed)) {
            assertFalse(listed.contains(entry), entry + " is listed");
        }
        refusedAs(attach(Api.asAdmin(), project, deduction, file(project)), 409, "entryNotAttachable");
        refusedAs(attach(Api.asAdmin(), project, credit, file(project)), 409, "entryNotAttachable");
        attach(Api.asAdmin(), project, removed, file(project)).then().statusCode(404);
    }

    @Test(description = "10f_field lists and attaches to their own alone", testName = "5380611c-17d0-4d9e-9b9a-9ba7d24c701b", priority = 3)
    public void _10fFieldListsAndAttachesToTheirOwnAlone() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person field = People.in(project, "recordEntries", "chargeAnyAccount");
        final @NotNull String mine = Entries.draft(project, field.asked());
        final @NotNull String theirs = purchaseWith(project, Map.of("submit", true));
        assertEquals(attachable(field.asked(), project), List.of(mine));
        attach(field.asked(), project, mine, file(project)).then().statusCode(200);
        refusedAs(attach(field.asked(), project, theirs, file(project)), 403, "notYourEntry");
    }

    @Test(description = "10f_whoever manages any entry lists and attaches to others but never to their drafts", testName = "f38e59e6-5977-4e72-9287-af29ebe69d1b", priority = 4)
    public void _10fWhoeverManagesAnyEntryListsAndAttachesToOthersButNeverToTheirDrafts() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person author = People.in(project, "recordEntries", "chargeAnyAccount");
        final @NotNull Person manager = People.in(project, "recordEntries", "manageAnyEntry");
        final @NotNull String draft = Entries.draft(project, author.asked());
        final @NotNull String sent = Entries.draft(project, author.asked());
        given(author.asked()).post("/projects/" + project + "/entries/" + sent + "/submit").then().statusCode(200);
        final @NotNull List<String> listed = attachable(manager.asked(), project);
        assertTrue(listed.contains(sent) && !listed.contains(draft), "listed " + listed);
        attach(manager.asked(), project, sent, file(project)).then().statusCode(200);
        refusedAs(attach(manager.asked(), project, draft, file(project)), 403, "notYourEntry");
    }

    @Test(description = "The QR codes match alone is answered for the pin over the list", testName = "47c50f90-a67e-47ae-8a3e-6a4a892adea1", priority = 5)
    public void theQrCodesMatchAloneIsAnsweredForThePinOverTheList() {
        final @NotNull String project = Projects.approved();
        final @NotNull String supplier = given(Api.asAdmin()).body(Map.of("name", "Cement Co", "vatNumber", "300000000000003")).post("/projects/" + project + "/suppliers").then()
                .statusCode(201).extract().path("id");
        final @NotNull String match = purchaseWith(project, Map.of("supplierId", supplier, "submit", true));
        purchaseWith(project, Map.of("supplierId", supplier, "amount", 900, "submit", true));
        purchaseWith(project, Map.of("supplierId", Suppliers.add(project, "Steel Co"), "submit", true));
        final @NotNull List<String> found = given(Api.asAdmin()).queryParams(Map.of("total", "200.5", "date", LocalDate.now().toString(), "vatNumber", "300000000000003"))
                .get("/projects/" + project + "/entries/attachable").then().statusCode(200).extract().path("id");
        assertEquals(found, List.of(match));
    }

    @Test(description = "10a_r10_a file goes on an approved entry its figures stay and a history line is written", testName = "dd606ca4-9118-45fe-b937-bc8a2ab5829c", priority = 6)
    public void _10aR10AFileGoesOnAnApprovedEntryItsFiguresStayAndAHistoryLineIsWritten() {
        final @NotNull String project = Projects.approved();
        final @NotNull String entry = Entries.approved(project);
        final @NotNull JsonPath before = read(project, entry);
        attach(Api.asAdmin(), project, entry, file(project)).then().statusCode(200);
        final @NotNull JsonPath after = read(project, entry);
        assertEquals(after.getDouble("entry.amount"), before.getDouble("entry.amount"));
        assertEquals(after.getString("entry.status"), "approved");
        assertEquals(after.getList("attachments").size(), 1);
    }

    @Test(description = "10e_r5_an entry with a file is listed and keeps both files after the attach", testName = "5031f550-0b5d-487e-a778-21e490930d92", priority = 7)
    public void _10eR5AnEntryWithAFileIsListedAndKeepsBothFilesAfterTheAttach() {
        final @NotNull String project = Projects.approved();
        final @NotNull String first = file(project);
        final @NotNull String entry = purchaseWith(project, Map.of("attachmentId", first, "submit", true));
        assertTrue(attachable(Api.asAdmin(), project).contains(entry));
        final @NotNull String second = file(project);
        attach(Api.asAdmin(), project, entry, second).then().statusCode(200);
        assertEquals(read(project, entry).getList("attachments.attachmentId"), List.of(first, second));
    }

    @Test(description = "10g_r9_the entrys page carries every file it holds the oldest first each with its role name type and date", testName = "a4961fea-2be2-48cb-a909-bd95b5c0c8e1", priority = 8)
    public void _10gR9TheEntrysPageCarriesEveryFileItHoldsTheOldestFirstEachWithItsRoleNameTypeAndDate() {
        final @NotNull String project = Projects.approved();
        final @NotNull String first = file(project);
        final @NotNull String entry = purchaseWith(project, Map.of("attachmentId", first));
        final @NotNull String second = file(project);
        attach(Api.asAdmin(), project, entry, second).then().statusCode(200);
        final @NotNull JsonPath files = read(project, entry);
        assertEquals(files.getList("attachments.attachmentId"), List.of(first, second));
        for (int i = 0; i < 2; i++) {
            assertEquals(files.getString("attachments[" + i + "].role"), "invoice");
            assertEquals(files.getString("attachments[" + i + "].fileName"), "shared.png");
            assertEquals(files.getString("attachments[" + i + "].contentType"), "image/png");
            assertFalse(files.getString("attachments[" + i + "].uploadedAt").isEmpty());
        }
    }

    @Test(description = "10g_an entry with no file carries none and no other entrys file", testName = "23247592-1f3a-44e1-9dc8-da24dc43bcb2", priority = 9)
    public void _10gAnEntryWithNoFileCarriesNoneAndNoOtherEntrysFile() {
        final @NotNull String project = Projects.approved();
        purchaseWith(project, Map.of("attachmentId", file(project)));
        final @NotNull String bare = purchaseWith(project, Map.of());
        assertEquals(read(project, bare).getList("attachments"), List.of());
    }

    @Test(description = "Each file takes its tables role", testName = "b2def98b-35bf-4a5d-bc6c-e7fdc8ac7cc7", priority = 10)
    public void eachFileTakesItsTablesRole() {
        final @NotNull String project = Projects.approved();
        final @NotNull String purchase = purchaseWith(project, Map.of());
        final @NotNull String paid = payment(project);
        attach(Api.asAdmin(), project, purchase, file(project)).then().statusCode(200);
        attach(Api.asAdmin(), project, paid, file(project)).then().statusCode(200);
        assertEquals(read(project, purchase).getString("attachments[0].role"), "invoice");
        assertEquals(read(project, paid).getString("attachments[0].role"), "receipt");
    }

    @Test(description = "A waiting entry stays waiting and a draft stays a draft", testName = "0ab3ee29-b424-4cda-8d7f-1c449886b35d", priority = 11)
    public void aWaitingEntryStaysWaitingAndADraftStaysADraft() {
        final @NotNull String project = Projects.approved();
        final @NotNull String waiting = Entries.waiting(project);
        final @NotNull String draft = Entries.draft(project);
        attach(Api.asAdmin(), project, waiting, file(project)).then().statusCode(200);
        attach(Api.asAdmin(), project, draft, file(project)).then().statusCode(200);
        assertEquals(read(project, waiting).getString("entry.status"), "pendingApproval");
        assertEquals(read(project, draft).getString("entry.status"), "draft");
    }

    @Test(description = "An approved entry stays approved and nobody is asked again", testName = "8fb5aa66-c9d8-4190-ba99-b1407660b048", priority = 12)
    public void anApprovedEntryStaysApprovedAndNobodyIsAskedAgain() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person decider = People.in(project, "approveEntries");
        final @NotNull String entry = Entries.approved(project);
        final int told = given(decider.asked()).get("/notifications").then().statusCode(200).extract().jsonPath().getList("$").size();
        attach(Api.asAdmin(), project, entry, file(project)).then().statusCode(200);
        assertEquals(read(project, entry).getString("entry.status"), "approved");
        assertEquals(given(decider.asked()).get("/notifications").then().statusCode(200).extract().jsonPath().getList("$").size(), told);
    }

    @Test(description = "A file already on an entry is refused", testName = "0aef2ae2-0a7c-4b57-b113-2108237e9b80", priority = 13)
    public void aFileAlreadyOnAnEntryIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull String shared = file(project);
        final @NotNull String entry = purchaseWith(project, Map.of("attachmentId", shared));
        refusedAs(attach(Api.asAdmin(), project, entry, shared), 409, "invoiceAlreadyAttached");
        refusedAs(attach(Api.asAdmin(), project, purchaseWith(project, Map.of()), shared), 409, "invoiceAlreadyAttached");
    }

    @Test(description = "A file of no such attachment is refused", testName = "e9e7655d-516d-4eb0-8a00-b264c1b78356", priority = 14)
    public void aFileOfNoSuchAttachmentIsRefused() {
        final @NotNull String project = Projects.approved();
        final @NotNull String entry = purchaseWith(project, Map.of());
        refusedAs(attach(Api.asAdmin(), project, entry, UUID.randomUUID().toString()), 400, "unknownAttachment");
        refusedAs(attach(Api.asAdmin(), project, entry, file(Projects.approved())), 400, "unknownAttachment");
    }
}
