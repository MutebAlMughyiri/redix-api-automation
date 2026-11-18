// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Entries;
import redix.support.Files;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;
import redix.support.Structure;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class ListPendingEntriesWithTheirInvoicesTest {
    private static @NotNull JsonPath inbox(final @NotNull String projectId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/entries/pending").then().statusCode(200).extract().jsonPath();
    }

    private static int numberOf(final @NotNull String projectId, final @NotNull String entryId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/entries/" + entryId).then().statusCode(200).extract().jsonPath().getInt("entry.number");
    }

    @Test(description = "The inbox is oldest first with the file when there is one", testName = "3b3d9fa8-6dc7-404e-9f53-2b9a9a5d34df", priority = 1)
    public void theInboxIsOldestFirstWithTheFileWhenThereIsOne() {
        final @NotNull String project = Projects.approved();
        final @NotNull String first = Entries.waiting(project);
        final @NotNull Map<String, Object> body = Entries.purchaseOf(project, Structure.itemId(project, Structure.PURCHASES));
        final @NotNull String file = Files.upload(Api.asAdmin(), "/projects/" + project + "/attachments", "invoice.png", Files.png(2048), "image/png").then().statusCode(201)
                .extract().path("id");
        body.putAll(Map.of("attachmentId", file, "submit", true));
        final @NotNull String second = Entries.record(Api.asAdmin(), project, body).then().statusCode(201).extract().path("id");
        final @NotNull JsonPath pending = inbox(project);
        assertEquals(pending.getList("id"), List.of(first, second));
        assertEquals(pending.getString("[1].attachmentId"), file);
        assertEquals(pending.getString("[1].contentType"), "image/png");
        assertEquals(pending.getString("[0].attachmentId"), "");
    }

    @Test(description = "A draft and what is decided are not in the inbox", testName = "8882b989-e783-414f-843c-0df1395d15c3", priority = 2)
    public void aDraftAndWhatIsDecidedAreNotInTheInbox() {
        final @NotNull String project = Projects.approved();
        Entries.draft(project);
        Entries.approved(project);
        Entries.rejected(project);
        final @NotNull String waiting = Entries.waiting(project);
        assertEquals(inbox(project).getList("id"), List.of(waiting));
    }

    @Test(description = "Finances inbox leads each waiting entry with its number", testName = "480b5fd2-e0d9-4d5b-b648-5588ab00ec53", priority = 3)
    public void financesInboxLeadsEachWaitingEntryWithItsNumber() {
        final @NotNull String project = Projects.approved();
        final @NotNull String entry = Entries.waiting(project);
        assertEquals(inbox(project).getInt("[0].number"), numberOf(project, entry));
    }

    @Test(description = "The inbox shows the entrys own number not its place in the inbox", testName = "c0d6c4cd-5130-4127-b8cf-8eca61c71487", priority = 4)
    public void theInboxShowsTheEntrysOwnNumberNotItsPlaceInTheInbox() {
        final @NotNull String project = Projects.approved();
        Entries.draft(project);
        Entries.approved(project);
        final @NotNull String waiting = Entries.waiting(project);
        final @NotNull JsonPath pending = inbox(project);
        assertEquals(pending.getList("$").size(), 1);
        assertEquals(pending.getInt("[0].number"), numberOf(project, waiting));
        assertFalse(pending.getInt("[0].number") == 1, "the third entry kept is not the inbox's first place");
    }

    @Test(description = "Only whoever decides entries sees the inbox or decides", testName = "2d18b46e-334e-4777-95a1-9c139c02e049", priority = 5)
    public void onlyWhoeverDecidesEntriesSeesTheInboxOrDecides() {
        final @NotNull String project = Projects.approved();
        final @NotNull String waiting = Entries.waiting(project);
        final @NotNull Person field = People.in(project, "recordEntries");
        given(field.asked()).get("/projects/" + project + "/entries/pending").then().statusCode(403);
        given(field.asked()).body(Map.of("entryIds", List.of(waiting))).post("/projects/" + project + "/entries/approve").then().statusCode(403);
        given(People.in(project, "approveEntries").asked()).get("/projects/" + project + "/entries/pending").then().statusCode(200);
    }
}
