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
import redix.support.History;
import redix.support.People;
import redix.support.Projects;
import redix.support.Structure;
import redix.support.Suppliers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class StoreAnInvoiceOrReceiptAttachmentTest {
    private static final int MEGABYTE = 1_000_000;

    private static @NotNull String attachments(final @NotNull String projectId) {
        return "/projects/" + projectId + "/attachments";
    }

    private static @NotNull Response upload(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull String name, final byte @NotNull [] file, final @NotNull String type) {
        return Files.upload(as, attachments(projectId), name, file, type);
    }

    private static @NotNull String kept(final @NotNull String projectId, final byte @NotNull [] file) {
        return upload(Api.asAdmin(), projectId, "invoice.png", file, "image/png").then().statusCode(201).extract().path("id");
    }

    private static byte @NotNull [] served(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull String attachmentId) {
        return given(as).get(attachments(projectId) + "/" + attachmentId).then().statusCode(200).extract().asByteArray();
    }

    private static @NotNull String sha256(final byte @NotNull [] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    private static @NotNull Map<String, Object> contract(final @NotNull String projectId, final @NotNull List<String> attachmentIds) {
        return new HashMap<>(Map.of("supplierId", Suppliers.add(projectId, "Contractor " + System.nanoTime()), "itemId", Structure.itemId(projectId, Structure.CONTRACTOR_PAYMENTS),
                "value", 10000, "isOpenValue", false, "attachmentIds", attachmentIds));
    }

    private static @NotNull JsonPath contracts(final @NotNull String projectId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/contracts").then().statusCode(200).extract().jsonPath();
    }

    @Test(description = "A picture or a PDF is kept with its name type size and hash", testName = "3b7d4466-6c45-46fb-8080-7f5b493dddc5", priority = 1)
    public void aPictureOrAPdfIsKeptWithItsNameTypeSizeAndHash() {
        final @NotNull String project = Projects.approved();
        final byte[] picture = Files.png(2048);
        final @NotNull JsonPath view = upload(Api.asAdmin(), project, "invoice-114.png", picture, "image/png").then().statusCode(201).extract().jsonPath();
        assertEquals(view.getString("fileName"), "invoice-114.png");
        assertEquals(view.getString("contentType"), "image/png");
        assertEquals(view.getInt("sizeBytes"), 2048);
        assertEquals(sha256(served(Api.asAdmin(), project, view.getString("id"))), sha256(picture));
        final byte[] pdf = Files.pdf(4096);
        upload(Api.asAdmin(), project, "receipt.pdf", pdf, "application/pdf").then().statusCode(201).body("contentType", equalTo("application/pdf"))
                .body("sizeBytes", equalTo(pdf.length));
    }

    @Test(description = "A file that is not a picture or a PDF is refused", testName = "c6e2f677-6783-444a-9799-756827364bc1", priority = 2)
    public void aFileThatIsNotAPictureOrAPdfIsRefused() {
        final @NotNull String project = Projects.approved();
        upload(Api.asAdmin(), project, "notes.txt", "Just some words, no picture.".getBytes(), "text/plain").then().statusCode(400).body("code", equalTo("invalidFileType"));
    }

    @Test(description = "What a file is comes from its bytes not from what the sender says", testName = "9ad97764-dc98-40d5-bfe9-6cedd03a1ba9", priority = 3)
    public void whatAFileIsComesFromItsBytesNotFromWhatTheSenderSays() {
        final @NotNull String project = Projects.approved();
        upload(Api.asAdmin(), project, "invoice.png", "Just some words, no picture.".getBytes(), "image/png").then().statusCode(400).body("code", equalTo("invalidFileType"));
        upload(Api.asAdmin(), project, "scan.txt", Files.png(1024), "text/plain").then().statusCode(201).body("contentType", equalTo("image/png"));
    }

    @Test(description = "A picture of1 mb or more is refused and one just below is kept", testName = "23408d05-71aa-458b-9339-9d71c5cd7989", priority = 4)
    public void aPictureOf1MbOrMoreIsRefusedAndOneJustBelowIsKept() {
        final @NotNull String project = Projects.approved();
        upload(Api.asAdmin(), project, "large.png", Files.png(MEGABYTE), "image/png").then().statusCode(400).body("code", equalTo("imageTooLarge"));
        kept(project, Files.png(MEGABYTE - 1));
    }

    @Test(description = "A PDF of1 mb or more is kept", testName = "8401fd97-e9d0-4850-b241-73a6b8af3454", priority = 5)
    public void aPdfOf1MbOrMoreIsKept() {
        final @NotNull String project = Projects.approved();
        upload(Api.asAdmin(), project, "contract.pdf", Files.pdf(2 * MEGABYTE), "application/pdf").then().statusCode(201);
    }

    @Test(description = "The file is kept outside the database and served back unchanged", testName = "3d2a8fd0-6146-4ff1-954e-c7f0889b4e52", priority = 6)
    public void theFileIsKeptOutsideTheDatabaseAndServedBackUnchanged() {
        final @NotNull String project = Projects.approved();
        final byte[] picture = Files.png(500_000);
        assertEquals(sha256(served(Api.asAdmin(), project, kept(project, picture))), sha256(picture));
    }

    @Test(description = "A name the sender chose never reaches the path of the file", testName = "37bcade5-47a3-4bd7-bbd4-56a735a41a64", priority = 7)
    public void aNameTheSenderChoseNeverReachesThePathOfTheFile() {
        final @NotNull String project = Projects.approved();
        final byte[] picture = Files.png(1024);
        final @NotNull String id = upload(Api.asAdmin(), project, "../../../../etc/passwd.png", picture, "image/png").then().statusCode(201).extract().path("id");
        assertEquals(sha256(served(Api.asAdmin(), project, id)), sha256(picture), "kept under a name of the server's own, and served back");
    }

    @Test(description = "Someone with no access to the project cannot read or keep a file", testName = "ff4d88d0-0d62-4b59-9383-458a81a12cb2", priority = 8)
    public void someoneWithNoAccessToTheProjectCannotReadOrKeepAFile() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = kept(project, Files.png(1024));
        final @NotNull RequestSpecification stranger = People.in(Projects.approved(), "attachFiles").asked();
        given(stranger).get(attachments(project) + "/" + id).then().statusCode(403);
        upload(stranger, project, "invoice.png", Files.png(1024), "image/png").then().statusCode(403);
    }

    @Test(description = "A file of another project is not found even for someone in both", testName = "b962bbcf-b424-43af-99a7-6318556b33c9", priority = 9)
    public void aFileOfAnotherProjectIsNotFoundEvenForSomeoneInBoth() {
        final @NotNull String project = Projects.approved();
        final @NotNull String other = Projects.approved();
        final @NotNull String theirs = kept(other, Files.png(1024));
        given(Api.asAdmin()).get(attachments(project) + "/" + theirs).then().statusCode(404);
    }

    @Test(description = "Without a session no file is served", testName = "378c0ecd-c10b-4773-9870-abe444c32fc3", priority = 10)
    public void withoutASessionNoFileIsServed() {
        final @NotNull String project = Projects.approved();
        given(Api.anonymous()).get(attachments(project) + "/" + kept(project, Files.png(1024))).then().statusCode(401);
    }

    @Test(description = "Field and finance keep files", testName = "48492b35-7303-426d-b46a-1696f5eee046", priority = 11)
    public void fieldAndFinanceKeepFiles() {
        final @NotNull String project = Projects.approved();
        upload(People.in(project, "recordEntries", "attachFiles").asked(), project, "invoice.png", Files.png(1024), "image/png").then().statusCode(201);
        upload(People.in(project, "manageSettings", "attachFiles").asked(), project, "invoice.png", Files.png(1024), "image/png").then().statusCode(201);
    }

    @Test(description = "Who holds no attach files cannot keep a file but reads it", testName = "f017be69-c7dc-4eaf-ac73-474d3c22cf1a", priority = 12)
    public void whoHoldsNoAttachFilesCannotKeepAFileButReadsIt() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = kept(project, Files.png(1024));
        final @NotNull RequestSpecification reader = People.in(project, "manageProject").asked();
        upload(reader, project, "invoice.png", Files.png(1024), "image/png").then().statusCode(403);
        served(reader, project, id);
    }

    @Test(description = "A finished project keeps no new file and still serves its own", testName = "8e05da41-5d6b-4a36-83a1-681a1db60f46", priority = 13)
    public void aFinishedProjectKeepsNoNewFileAndStillServesItsOwn() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = kept(project, Files.png(1024));
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);
        upload(Api.asAdmin(), project, "invoice.png", Files.png(1024), "image/png").then().statusCode(409).body("code", equalTo("projectFinished"));
        served(Api.asAdmin(), project, id);
    }

    @Test(description = "Keeping a file is in the history", testName = "60677197-425f-4734-8b20-33d7f5d7df2a", priority = 14, enabled = false)
    public void keepingAFileIsInTheHistory() {
        throw new AssertionError("«سجل المشروع» shows a file kept through what it is attached to, never alone (UC-069 D4); Go's internal/attachments tests prove its History line is kept");
    }

    @Test(description = "Linking a document to a contract is in the history with which one", testName = "68dd7ab0-065c-41c1-9976-e0d1892568f6", priority = 15, enabled = false)
    public void linkingADocumentToAContractIsInTheHistoryWithWhichOne() {
        throw new AssertionError("«سجل المشروع» shows a contract's document as the contract's change, not which document (UC-069 D4); Go's internal/contracts tests prove which one its History line keeps");
    }

    @Test(description = "A contract carries its document from when it is added", testName = "6210ff58-9af8-420a-9dd9-532577396da1", priority = 16)
    public void aContractCarriesItsDocumentFromWhenItIsAdded() {
        final @NotNull String project = Projects.approved();
        final @NotNull String document = kept(project, Files.png(1024));
        given(Api.asAdmin()).body(contract(project, List.of(document))).post("/projects/" + project + "/contracts").then().statusCode(200);
        assertEquals(contracts(project).getList("[0].documents.id"), List.of(document));
    }

    @Test(description = "An amendment adds a document and keeps those already carried", testName = "00a94ce5-dc48-4071-9672-b4ee18c6c106", priority = 17)
    public void anAmendmentAddsADocumentAndKeepsThoseAlreadyCarried() {
        final @NotNull String project = Projects.approved();
        final @NotNull String first = kept(project, Files.png(1024));
        given(Api.asAdmin()).body(contract(project, List.of(first))).post("/projects/" + project + "/contracts").then().statusCode(200);
        final @NotNull String contract = contracts(project).getString("[0].id");
        final @NotNull String second = kept(project, Files.png(1024));
        given(Api.asAdmin()).body(Map.of("value", 10000, "isOpenValue", false, "attachmentIds", List.of(second))).put("/projects/" + project + "/contracts/" + contract)
                .then().statusCode(200);
        assertTrue(contracts(project).getList("[0].documents.id").containsAll(List.of(first, second)));
    }

    @Test(description = "A document of another project is refused on a contract", testName = "3dbba23c-2245-46ec-9889-b8286ee33ca4", priority = 18)
    public void aDocumentOfAnotherProjectIsRefusedOnAContract() {
        final @NotNull String project = Projects.approved();
        final @NotNull String theirs = kept(Projects.approved(), Files.png(1024));
        given(Api.asAdmin()).body(contract(project, List.of(theirs))).post("/projects/" + project + "/contracts").then().statusCode(400)
                .body("code", equalTo("unknownAttachment"));
        assertEquals(contracts(project).getList("$"), List.of());
    }
}
