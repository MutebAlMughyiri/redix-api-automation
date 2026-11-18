// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.Contracts;
import redix.support.Entries;
import redix.support.Files;
import redix.support.People;
import redix.support.Projects;
import redix.support.Structure;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class AcceptOfflineUploadsExactlyOnceTest {
    private static int countOf(final @NotNull String projectId, final @NotNull Object entryId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/entries").then().statusCode(200).extract().jsonPath().getList("findAll { it.id == '" + entryId + "' }").size();
    }

    @Test(description = "An upload sent again lands once and answers the entry", testName = "1e5ad396-3336-4a19-836a-ee4ce10c6fff", priority = 1)
    public void anUploadSentAgainLandsOnceAndAnswersTheEntry() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = Entries.purchaseOf(project, Structure.itemId(project, Structure.PURCHASES));
        body.put("submit", true);
        final @NotNull String id = Entries.record(Api.asAdmin(), project, body).then().statusCode(201).extract().path("id");
        Entries.record(Api.asAdmin(), project, body).then().statusCode(200).body("id", equalTo(id)).body("status", equalTo("pendingApproval"));
        assertEquals(countOf(project, id), 1);
    }

    @Test(description = "A payment sent again lands once", testName = "bb1a359d-acda-4ac2-97fa-b222182fb47a", priority = 2)
    public void aPaymentSentAgainLandsOnce() {
        final @NotNull String project = Projects.approved();
        final @NotNull String item = Structure.itemId(project, Structure.CONTRACTOR_PAYMENTS);
        final @NotNull Map<String, Object> body = Entries.purchaseOf(project, Structure.itemId(project, Structure.BOTH));
        body.putAll(Map.of("itemId", item, "tableId", Contracts.add(project, item, "Contractor Co"), "amount", 3000, "columnValues", Map.of()));
        Entries.record(Api.asAdmin(), project, body).then().statusCode(201);
        Entries.record(Api.asAdmin(), project, body).then().statusCode(200).body("id", equalTo(body.get("id")));
        assertEquals(countOf(project, body.get("id")), 1);
    }

    @Test(description = "An ID someone else used is refused and tells them nothing of it", testName = "4fb8189e-96fb-4380-b6f6-03c206fbf7e5", priority = 3)
    public void anIdSomeoneElseUsedIsRefusedAndTellsThemNothingOfIt() {
        final @NotNull String project = Projects.approved();
        final @NotNull Map<String, Object> body = Entries.purchaseOf(project, Structure.itemId(project, Structure.PURCHASES));
        Entries.record(Api.asAdmin(), project, body).then().statusCode(201);
        final @NotNull String refused = Entries.record(People.in(project, "recordEntries", "chargeAnyAccount").asked(), project, body).then().statusCode(409)
                .body("code", equalTo("entryExists")).extract().asString();
        assertFalse(refused.contains("Cement") || refused.contains("pendingApproval") || refused.contains("draft"), refused);
    }

    @Test(description = "The same file sent again is kept once", testName = "784c2079-0dd7-4526-8253-f7e7b01cda8b", priority = 4)
    public void theSameFileSentAgainIsKeptOnce() {
        final @NotNull String project = Projects.approved();
        final byte[] picture = Files.png(4096);
        final @NotNull String first = Files.upload(Api.asAdmin(), "/projects/" + project + "/attachments", "invoice.png", picture, "image/png").then().statusCode(201).extract()
                .path("id");
        Files.upload(Api.asAdmin(), "/projects/" + project + "/attachments", "invoice.png", picture, "image/png").then().statusCode(200).body("id", equalTo(first));
    }
}
