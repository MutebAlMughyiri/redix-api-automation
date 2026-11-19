// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Files;
import redix.support.History;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;
import redix.support.Suppliers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ArchiveAndRetrieveAProjectTest {
    private static final @NotNull ObjectMapper JSON = new ObjectMapper();

    /** The archive of projectId as as takes it, «أرشفة المشروع». */
    private static @NotNull String archive(final @NotNull RequestSpecification as, final @NotNull String projectId) {
        return given(as).get("/projects/" + projectId + "/archive/json").then().statusCode(200).extract().asString();
    }

    /** archived sent to projectId's «استرجاع المشروع» as as, a preview when preview is true. */
    private static @NotNull Response retrieve(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull String archived, final boolean preview) {
        return Files.upload(as, "/projects/" + projectId + "/retrieve" + (preview ? "?preview=true" : ""), "archive.json", archived.getBytes(StandardCharsets.UTF_8),
                "application/json");
    }

    /** archived with change made to it, as a file someone edited by hand. */
    private static @NotNull String edited(final @NotNull String archived, final @NotNull Consumer<ObjectNode> change) {
        try {
            final @NotNull ObjectNode file = (ObjectNode) JSON.readTree(archived);
            change.accept(file);
            return JSON.writeValueAsString(file);
        } catch (final JsonProcessingException reading) {
            throw new AssertionError("the archive is not JSON", reading);
        }
    }

    private static @NotNull List<String> suppliers(final @NotNull String projectId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/suppliers").then().statusCode(200).extract().jsonPath().getList("name");
    }

    private static @NotNull String newSupplier(final @NotNull String projectId) {
        final @NotNull String name = "Supplier " + UUID.randomUUID().toString().substring(0, 8);
        Suppliers.add(projectId, name);
        return name;
    }

    @Test(description = "An archive holds every row of the project and names who took it", testName = "6862390e-c99f-49c9-bb07-268c6cab18a5", priority = 1)
    public void anArchiveHoldsEveryRowOfTheProjectAndNamesWhoTookIt() {
        final @NotNull String project = Projects.approved();
        final @NotNull String supplier = newSupplier(project);

        final @NotNull JsonPath archived = JsonPath.from(archive(Api.asAdmin(), project));

        assertEquals(archived.getString("format"), "redix.archive");
        assertEquals(archived.getString("project.id"), project);
        assertTrue(archived.getInt("release") > 0, "the archive names no release");
        assertFalse(archived.getString("archivedBy.name").isEmpty(), "the archive names nobody who took it");
        assertEquals(archived.getList("tables.projects").size(), 1, "the archive holds another project's row");
        assertTrue(archived.getList("tables.suppliers.name").contains(supplier), "the archive lacks the project's supplier");
        assertTrue(History.said(Api.asAdmin(), project, "").getFirst().startsWith("archived project"), "«سجل المشروع» does not say it was archived");
    }

    @Test(description = "A preview counts what the project holds now and after and changes nothing", testName = "dc166e2a-5ccf-4c39-bd7f-79ae95c0793b", priority = 2)
    public void aPreviewCountsWhatTheProjectHoldsNowAndAfterAndChangesNothing() {
        final @NotNull String project = Projects.approved();
        final @NotNull String archived = archive(Api.asAdmin(), project);
        final @NotNull String added = newSupplier(project);

        final @NotNull JsonPath preview = retrieve(Api.asAdmin(), project, archived, true).then().statusCode(200).extract().jsonPath();

        assertEquals(preview.getInt("now.suppliers"), preview.getInt("after.suppliers") + 1);
        assertEquals(preview.getString("archivedAt"), JsonPath.from(archived).getString("archivedAt"));
        assertFalse(preview.getString("archivedBy").isEmpty(), "the preview names nobody who took the archive");
        assertTrue(suppliers(project).contains(added), "the preview changed the project");
    }

    @Test(description = "A retrieve makes the project its archive again", testName = "37d1d436-a2b8-4d8c-a6a1-d76db7cf3703", priority = 3)
    public void aRetrieveMakesTheProjectItsArchiveAgain() {
        final @NotNull String project = Projects.approved();
        final @NotNull String kept = newSupplier(project);
        final @NotNull String archived = archive(Api.asAdmin(), project);
        final @NotNull String added = newSupplier(project);

        retrieve(Api.asAdmin(), project, archived, false).then().statusCode(200);

        assertTrue(suppliers(project).contains(kept), "the archive's supplier is gone");
        assertFalse(suppliers(project).contains(added), "a supplier added after the archive stayed");
        assertTrue(History.said(Api.asAdmin(), project, "").getFirst().startsWith("retrieved project"), "«سجل المشروع» does not say it was retrieved");
    }

    @Test(description = "A retrieve keeps the history the issued reports and the members", testName = "7d70019b-26f6-431b-bcf0-4a5b15c42964", priority = 4)
    public void aRetrieveKeepsTheHistoryTheIssuedReportsAndTheMembers() {
        final @NotNull String project = Projects.approved();
        final @NotNull String archived = archive(Api.asAdmin(), project);
        final @NotNull Person joined = People.newcomer(project);
        final int lines = History.of(Api.asAdmin(), project, "").size();

        retrieve(Api.asAdmin(), project, archived, false).then().statusCode(200);

        assertTrue(given(Api.asAdmin()).get("/projects/" + project + "/users").then().statusCode(200).extract().jsonPath().getList("id").contains(joined.id()),
                "a person added after the archive left the project");
        assertEquals(History.of(Api.asAdmin(), project, "").size(), lines + 1, "the retrieve took History lines away, or wrote more than its own");
    }

    @Test(description = "A file that is no archive or of a newer release is refused and nothing is saved", testName = "9051daac-8b57-4b10-848b-1aba33dd14af", priority = 5)
    public void aFileThatIsNoArchiveOrOfANewerReleaseIsRefusedAndNothingIsSaved() {
        final @NotNull String project = Projects.approved();
        final @NotNull String archived = archive(Api.asAdmin(), project);
        final @NotNull String added = newSupplier(project);

        retrieve(Api.asAdmin(), project, "{ \"format\": \"another.app\" }", false).then().statusCode(400).body("code", equalTo("notAnArchive"));
        retrieve(Api.asAdmin(), project, edited(archived, file -> file.put("release", 9999)), false).then().statusCode(400).body("code", equalTo("newerArchive"));

        assertTrue(suppliers(project).contains(added), "a refused file changed the project");
    }

    @Test(description = "Another projects archive is refused naming that project", testName = "fac0ba76-c70b-43e7-b6f1-5e00f83bb2ea", priority = 6)
    public void anotherProjectsArchiveIsRefusedNamingThatProject() {
        final @NotNull String other = Projects.approved();
        final @NotNull String archived = archive(Api.asAdmin(), other);
        final @NotNull String project = Projects.approved();

        retrieve(Api.asAdmin(), project, archived, true).then().statusCode(409).body("code", equalTo("archiveOfAnotherProject"))
                .body("project", equalTo(JsonPath.from(archived).getString("project.name")));
    }

    @Test(description = "A finished project is archived but never retrieved", testName = "0367907e-5c87-4711-b49c-5b580ea78d40", priority = 7)
    public void aFinishedProjectIsArchivedButNeverRetrieved() {
        final @NotNull String project = Projects.approved();
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);

        final @NotNull String archived = archive(Api.asAdmin(), project);

        retrieve(Api.asAdmin(), project, archived, true).then().statusCode(409).body("code", equalTo("projectFinished"));
    }

    /** Release 11 came before the tables' sight (redix-api #106), whose two tables release 12 added; an archive of it holds neither. */
    @Test(description = "An archive of an earlier release is brought up and retrieves", testName = "c82e419d-4a45-4e3e-9546-6ea060a67fc5", priority = 8)
    public void anArchiveOfAnEarlierReleaseIsBroughtUpAndRetrieves() {
        final @NotNull String project = Projects.approved();
        final @NotNull String kept = newSupplier(project);
        final @NotNull String earlier = edited(archive(Api.asAdmin(), project), file -> {
            file.put("release", 11);
            ((ObjectNode) file.get("tables")).remove(List.of("unseen_groups", "unseen_contracts"));
        });
        final @NotNull String added = newSupplier(project);

        retrieve(Api.asAdmin(), project, earlier, false).then().statusCode(200);

        assertTrue(suppliers(project).contains(kept), "the earlier archive's supplier is gone");
        assertFalse(suppliers(project).contains(added), "a supplier added after the archive stayed");
    }

    @Test(description = "Without ManageProjectFiles neither the archive nor the retrieve answers", testName = "9cacd05f-6366-43ab-95d0-388a2d1fd22c", priority = 9)
    public void withoutManageProjectFilesNeitherTheArchiveNorTheRetrieveAnswers() {
        final @NotNull String project = Projects.approved();
        final @NotNull String archived = archive(Api.asAdmin(), project);
        final @NotNull Person reader = People.in(project);

        given(reader.asked()).get("/projects/" + project + "/archive/json").then().statusCode(403);
        retrieve(reader.asked(), project, archived, true).then().statusCode(403);
    }
}
