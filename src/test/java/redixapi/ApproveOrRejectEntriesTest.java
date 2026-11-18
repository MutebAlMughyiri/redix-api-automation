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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class ApproveOrRejectEntriesTest {
    private static @NotNull Response approve(final @NotNull String projectId, final @NotNull List<String> entryIds) {
        return given(Api.asAdmin()).body(Map.of("entryIds", entryIds)).post("/projects/" + projectId + "/entries/approve");
    }

    private static @NotNull Response reject(final @NotNull String projectId, final @NotNull String entryId, final @NotNull String reason) {
        return given(Api.asAdmin()).body(Map.of("reason", reason)).post("/projects/" + projectId + "/entries/" + entryId + "/reject");
    }

    private static @NotNull JsonPath read(final @NotNull String projectId, final @NotNull String entryId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/entries/" + entryId).then().statusCode(200).extract().jsonPath();
    }

    private static double balanceOf(final @NotNull String projectId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/accounts").then().statusCode(200).extract().jsonPath().getDouble("[0].balance");
    }

    private static double itemTotal(final @NotNull String projectId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/reports/items/" + Structure.itemId(projectId, "تكلفة الأرض")).then().statusCode(200).extract().jsonPath()
                .getDouble("total");
    }

    @Test(description = "An approved entry counts and marks when it was approved", testName = "4909182a-9115-4fa4-960d-d68732486d96", priority = 1)
    public void anApprovedEntryCountsAndMarksWhenItWasApproved() {
        final @NotNull String project = Projects.approved();
        final @NotNull String entry = Entries.waiting(project);
        assertEquals(read(project, entry).getString("entry.approvedAt"), "1900-01-01T00:00:00Z");
        approve(project, List.of(entry)).then().statusCode(200).body("approved", equalTo(List.of(entry)));
        assertEquals(read(project, entry).getString("entry.status"), "approved");
        assertNotEquals(read(project, entry).getString("entry.approvedAt"), "1900-01-01T00:00:00Z");
        assertEquals(itemTotal(project), 200.0);
    }

    @Test(description = "An accounts balance moves only with what is approved", testName = "c9c87dd8-0d2b-41da-9005-962de78a0cb6", priority = 2)
    public void anAccountsBalanceMovesOnlyWithWhatIsApproved() {
        final @NotNull String project = Projects.approved();
        Entries.draft(project);
        final @NotNull String waiting = Entries.waiting(project);
        assertEquals(balanceOf(project), 0.0);
        approve(project, List.of(waiting)).then().statusCode(200);
        assertEquals(balanceOf(project), -200.0);
    }

    @Test(description = "A bulk approval names every failure and approves the rest", testName = "3dc07f83-827d-4dcc-a678-7b85f0c850d8", priority = 3)
    public void aBulkApprovalNamesEveryFailureAndApprovesTheRest() {
        final @NotNull String project = Projects.approved();
        final @NotNull String first = Entries.waiting(project);
        final @NotNull String draft = Entries.draft(project);
        final @NotNull String second = Entries.waiting(project);
        final @NotNull String unknown = UUID.randomUUID().toString();
        final @NotNull JsonPath answer = approve(project, List.of(first, draft, second, unknown)).then().statusCode(200).extract().jsonPath();
        assertEquals(answer.getList("approved"), List.of(first, second));
        assertEquals(answer.getList("failed.entryId"), List.of(draft, unknown));
        assertEquals(read(project, draft).getString("entry.status"), "draft");
    }

    @Test(description = "An entry of another project is unknown here", testName = "ea2cf10d-c3a8-4184-8b50-7f23e4c949d9", priority = 4)
    public void anEntryOfAnotherProjectIsUnknownHere() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull String theirs = Entries.waiting(other);
        assertEquals(approve(project, List.of(theirs)).then().statusCode(200).extract().jsonPath().getList("failed.entryId"), List.of(theirs));
        reject(project, theirs, "Not ours").then().statusCode(404);
        assertEquals(read(other, theirs).getString("entry.status"), "pendingApproval");
    }

    @Test(description = "A rejection needs a reason and returns the entry to its author with it", testName = "1d093e94-64d2-492f-b781-6e7a63243ae9", priority = 5)
    public void aRejectionNeedsAReasonAndReturnsTheEntryToItsAuthorWithIt() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person author = People.in(project, "recordEntries", "chargeAnyAccount");
        final @NotNull String entry = Entries.draft(project, author.asked());
        given(author.asked()).post("/projects/" + project + "/entries/" + entry + "/submit").then().statusCode(200);
        reject(project, entry, "  ").then().statusCode(400).body("code", equalTo("reasonRequired"));
        reject(project, entry, "Wrong supplier").then().statusCode(200);
        given(author.asked()).get("/projects/" + project + "/entries").then().statusCode(200).body("find { it.id == '" + entry + "' }.status", equalTo("rejected"))
                .body("find { it.id == '" + entry + "' }.rejectionReason", equalTo("Wrong supplier"));
    }

    @Test(description = "A rejected entry is sent again and counts only once approved", testName = "ba4b0ce5-f64a-497e-8b48-59386c602c99", priority = 6)
    public void aRejectedEntryIsSentAgainAndCountsOnlyOnceApproved() {
        final @NotNull String project = Projects.approved();
        final @NotNull String entry = Entries.rejected(project);
        assertEquals(itemTotal(project), 0.0);
        Entries.submit(project, entry);
        assertEquals(itemTotal(project), 0.0);
        Entries.approve(project, entry);
        assertEquals(itemTotal(project), 200.0);
    }

    @Test(description = "What is already decided cannot be decided again", testName = "fcec306d-e4b2-42a0-b987-dad39d97228d", priority = 7)
    public void whatIsAlreadyDecidedCannotBeDecidedAgain() {
        final @NotNull String project = Projects.approved();
        final @NotNull String approved = Entries.approved(project);
        assertEquals(approve(project, List.of(approved)).then().statusCode(200).extract().jsonPath().getList("failed.entryId"), List.of(approved));
        reject(project, approved, "Too late").then().statusCode(409);
        final @NotNull String rejected = Entries.rejected(project);
        reject(project, rejected, "Again").then().statusCode(409);
    }

    @Test(description = "Approving the first expense on a pending supplier approves it too", testName = "6f9e5a06-530a-4a7b-820f-8f56ed5851c5", priority = 8)
    public void approvingTheFirstExpenseOnAPendingSupplierApprovesItToo() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person field = People.in(project, "recordEntries", "addSupplier", "chargeAnyAccount");
        final @NotNull String supplier = given(field.asked()).body(Map.of("name", "Sand Co")).post("/projects/" + project + "/suppliers").then().statusCode(201).extract().path("id");
        final @NotNull Map<String, Object> body = Entries.purchaseOf(project, Structure.itemId(project, Structure.PURCHASES));
        body.putAll(Map.of("supplierId", supplier, "submit", true));
        final @NotNull String entry = Entries.record(field.asked(), project, body).then().statusCode(201).extract().path("id");
        Entries.approve(project, entry);
        given(Api.asAdmin()).get("/projects/" + project + "/suppliers").then().body("find { it.id == '" + supplier + "' }.status", equalTo("approved"));
    }

    @Test(description = "Rejecting it leaves the supplier pending", testName = "1b260913-f8fa-46c8-a409-bd70e490a69b", priority = 9)
    public void rejectingItLeavesTheSupplierPending() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person field = People.in(project, "recordEntries", "addSupplier", "chargeAnyAccount");
        final @NotNull String supplier = given(field.asked()).body(Map.of("name", "Sand Co")).post("/projects/" + project + "/suppliers").then().statusCode(201).extract().path("id");
        final @NotNull Map<String, Object> body = Entries.purchaseOf(project, Structure.itemId(project, Structure.PURCHASES));
        body.putAll(Map.of("supplierId", supplier, "submit", true));
        reject(project, Entries.record(field.asked(), project, body).then().statusCode(201).extract().path("id"), "Unknown supplier").then().statusCode(200);
        given(Api.asAdmin()).get("/projects/" + project + "/suppliers").then().body("find { it.id == '" + supplier + "' }.status", equalTo("pendingApproval"));
    }

    @Test(description = "Each sending and decision is recorded with who and why", testName = "70e8a5bc-ac8e-4b0f-92df-485be3539da6", priority = 10)
    public void eachSendingAndDecisionIsRecordedWithWhoAndWhy() {
        final @NotNull String project = Projects.approved();
        Entries.approved(project);
        Entries.rejected(project);
        final @NotNull JsonPath lines = given(Api.asAdmin()).get("/projects/" + project + "/approvals/history").then().statusCode(200).extract().jsonPath();
        assertTrue(lines.getList("kind").size() >= 2, "lines " + lines.prettify());
        assertEquals(lines.getString("find { it.reason == 'Wrong supplier' }.by"), People.admin().username());
    }
}
