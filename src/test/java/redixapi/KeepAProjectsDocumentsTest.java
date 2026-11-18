// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Files;
import redix.support.People;
import redix.support.Projects;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;

public class KeepAProjectsDocumentsTest {
    private static @NotNull Response keep(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull String name, final @NotNull String group, final @NotNull String fileName, final byte @NotNull [] file) {
        return given(as).contentType("multipart/form-data").multiPart("name", name).multiPart("group", group).multiPart("file", fileName, file, "application/octet-stream")
                .post("/projects/" + projectId + "/documents");
    }

    private static @NotNull JsonPath documents(final @NotNull RequestSpecification as, final @NotNull String projectId) {
        return given(as).get("/projects/" + projectId + "/documents").then().statusCode(200).extract().jsonPath();
    }

    private static byte @NotNull [] text(final @NotNull String words) {
        return (words + " " + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
    }

    @Test(description = "The manager keeps documents and everyone reads them ungrouped first then by group", testName = "ef8822cf-5fcf-4c83-83ef-8eee7c7c100b", priority = 1)
    public void theManagerKeepsDocumentsAndEveryoneReadsThemUngroupedFirstThenByGroup() {
        final @NotNull String project = Projects.approved();
        keep(Api.asAdmin(), project, "Permit", "Licences", "permit.txt", text("permit")).then().statusCode(200);
        keep(Api.asAdmin(), project, "Deed", "", "deed.txt", text("deed")).then().statusCode(200);
        keep(Api.asAdmin(), project, "Plan", "Drawings", "plan.txt", text("plan")).then().statusCode(200);
        final @NotNull JsonPath listed = documents(People.in(project).asked(), project);
        assertEquals(listed.getString("[0].name"), "Deed");
        assertEquals(listed.getString("[0].group"), "");
        assertEquals(listed.getList("name").size(), 3);
        keep(People.in(project, "recordEntries").asked(), project, "Mine", "", "mine.txt", text("mine")).then().statusCode(403);
    }

    @Test(description = "A document is any file named by its extension while an invoice stays a picture or a PDF", testName = "12771d20-c085-4836-9c64-c2e567478c61", priority = 2)
    public void aDocumentIsAnyFileNamedByItsExtensionWhileAnInvoiceStaysAPictureOrAPdf() {
        final @NotNull String project = Projects.approved();
        keep(Api.asAdmin(), project, "Contract", "", "contract.docx", text("word document")).then().statusCode(200);
        assertEquals(documents(Api.asAdmin(), project).getString("[0].fileName"), "contract.docx");
        Files.upload(Api.asAdmin(), "/projects/" + project + "/attachments", "contract.docx", text("word document"), "application/octet-stream").then().statusCode(400)
                .body("code", equalTo("invalidFileType"));
    }

    @Test(description = "A document needs a name a group of100 letters at most a file of25 mb at most and is kept once", testName = "ba6ef143-c771-40ec-9236-cec4bbd91a08", priority = 3)
    public void aDocumentNeedsANameAGroupOf100LettersAtMostAFileOf25MbAtMostAndIsKeptOnce() {
        final @NotNull String project = Projects.approved();
        keep(Api.asAdmin(), project, " ", "", "deed.txt", text("deed")).then().statusCode(400).body("code", equalTo("invalidName"));
        keep(Api.asAdmin(), project, "Deed", "g".repeat(101), "deed.txt", text("deed")).then().statusCode(400).body("code", equalTo("invalidGroup"));
        final byte[] deed = text("deed");
        keep(Api.asAdmin(), project, "Deed", "g".repeat(100), "deed.txt", deed).then().statusCode(200);
        keep(Api.asAdmin(), project, "Deed again", "", "deed.txt", deed).then().statusCode(409).body("code", equalTo("documentAlreadyKept"));
        assertEquals(documents(Api.asAdmin(), project).getList("name"), List.of("Deed"));
    }

    @Test(description = "Findings137_a documents request may hold as much as a form so a file over25 mb is answered rather than cut off", testName = "72dd8102-a763-49d0-a332-0e7ee16f0d35", priority = 4)
    public void findings137ADocumentsRequestMayHoldAsMuchAsAFormSoAFileOver25MbIsAnsweredRatherThanCutOff() {
        final @NotNull String project = Projects.approved();
        keep(Api.asAdmin(), project, "Survey", "", "survey.bin", new byte[30_000_000]).then().statusCode(400).body("code", equalTo("fileTooLarge"));
    }

    @Test(description = "A removed document leaves the list but its file stays", testName = "e235c2d6-9ad5-424f-9723-ad705d5e2174", priority = 5)
    public void aRemovedDocumentLeavesTheListButItsFileStays() {
        final @NotNull String project = Projects.approved();
        keep(Api.asAdmin(), project, "Deed", "", "deed.txt", text("deed")).then().statusCode(200);
        final @NotNull JsonPath kept = documents(Api.asAdmin(), project);
        given(Api.asAdmin()).delete("/projects/" + project + "/documents/" + kept.getString("[0].id")).then().statusCode(200);
        assertEquals(documents(Api.asAdmin(), project).getList("$"), List.of());
        given(Api.asAdmin()).get("/projects/" + project + "/attachments/" + kept.getString("[0].attachmentId")).then().statusCode(200);
    }
}
