// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.People;
import redix.support.People.Person;
import redix.support.Projects;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class QuickaddASupplierPendingApprovalTest {
    private static @NotNull Response add(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull Map<String, Object> body) {
        return given(as).body(body).post("/projects/" + projectId + "/suppliers");
    }

    private static @NotNull List<String> offeredTo(final @NotNull RequestSpecification as, final @NotNull String projectId) {
        return given(as).get("/projects/" + projectId + "/suppliers/search").then().statusCode(200).extract().path("name");
    }

    private static @NotNull Person field(final @NotNull String projectId) {
        return People.in(projectId, "recordEntries", "addSupplier");
    }

    @Test(description = "What field adds waits for approval", testName = "2645fdfc-2346-4b92-99fd-719f79fcd695", priority = 1)
    public void whatFieldAddsWaitsForApproval() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = add(field(project).asked(), project, Map.of("name", "Sand Co")).then().statusCode(201).body("status", equalTo("pendingApproval"))
                .extract().path("id");
        given(Api.asAdmin()).get("/projects/" + project + "/suppliers").then().body("find { it.id == '" + id + "' }.status", equalTo("pendingApproval"));
    }

    @Test(description = "Finance adds a supplier with all its details", testName = "7af653e0-a28d-4f71-8151-4db1dab7f0b0", priority = 2)
    public void financeAddsASupplierWithAllItsDetails() {
        final @NotNull String project = Projects.approved();
        add(Api.asAdmin(), project, Map.of("name", "Sand Co", "vatNumber", "300000000000003")).then().statusCode(201).body("status", equalTo("approved"));
        add(People.in(project, "addSupplier", "manageSettings").asked(), project, Map.of("name", "Gravel Co")).then().statusCode(201).body("status", equalTo("approved"));
    }

    @Test(description = "What field adds is validated as any supplier", testName = "fb9a78a3-176b-4cc0-a993-4b5e3913dded", priority = 3)
    public void whatFieldAddsIsValidatedAsAnySupplier() {
        final @NotNull String project = Projects.approved();
        final @NotNull RequestSpecification field = field(project).asked();
        add(field, project, Map.of("name", "Sand Co", "vatNumber", "30000A")).then().statusCode(400).body("code", equalTo("invalidVatNumber"));
        add(field, project, Map.of("name", " ")).then().statusCode(400).body("code", equalTo("invalidName"));
        add(field, project, Map.of("name", "Sand Co", "mobile", "05-12")).then().statusCode(400).body("code", equalTo("invalidMobile"));
    }

    @Test(description = "A VAT number already in the register is refused to field naming its supplier", testName = "bafeb8cd-9dfb-45cb-b2b4-8743e7779d79", priority = 4)
    public void aVatNumberAlreadyInTheRegisterIsRefusedToFieldNamingItsSupplier() {
        final @NotNull String project = Projects.approved();
        final @NotNull String holder = add(Api.asAdmin(), project, Map.of("name", "Sand Co", "vatNumber", "300000000000003")).then().statusCode(201).extract().path("id");
        add(field(project).asked(), project, Map.of("name", "Gravel Co", "vatNumber", "300000000000003")).then().statusCode(409).body("code", equalTo("vatNumberTaken"))
                .body("existing.id", equalTo(holder));
    }

    @Test(description = "A pending supplier already holds its VAT number", testName = "df6f2960-ddab-43fa-aa04-12016a41e93f", priority = 5)
    public void aPendingSupplierAlreadyHoldsItsVatNumber() {
        final @NotNull String project = Projects.approved();
        add(field(project).asked(), project, Map.of("name", "Sand Co", "vatNumber", "300000000000003")).then().statusCode(201);
        add(Api.asAdmin(), project, Map.of("name", "Gravel Co", "vatNumber", "300000000000003")).then().statusCode(409).body("code", equalTo("vatNumberTaken"));
    }

    @Test(description = "A pending supplier is offered to who added it and to finance only", testName = "070a52e3-2a29-4955-b4af-57d737703857", priority = 6)
    public void aPendingSupplierIsOfferedToWhoAddedItAndToFinanceOnly() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person adder = field(project);
        add(adder.asked(), project, Map.of("name", "Sand Co")).then().statusCode(201);
        assertTrue(offeredTo(adder.asked(), project).contains("Sand Co"));
        assertTrue(offeredTo(Api.asAdmin(), project).contains("Sand Co"));
        assertFalse(offeredTo(field(project).asked(), project).contains("Sand Co"));
    }

    @Test(description = "Everyone with access sees a pending supplier in the register", testName = "858adf08-fedf-4751-a76b-e74bf685c76a", priority = 7)
    public void everyoneWithAccessSeesAPendingSupplierInTheRegister() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = add(field(project).asked(), project, Map.of("name", "Sand Co")).then().statusCode(201).extract().path("id");
        given(People.in(project).asked()).get("/projects/" + project + "/suppliers").then().statusCode(200)
                .body("find { it.id == '" + id + "' }.status", equalTo("pendingApproval"));
    }

    @Test(description = "Only finance and field add suppliers", testName = "00474578-0335-443b-93bb-ad4f803f1942", priority = 8)
    public void onlyFinanceAndFieldAddSuppliers() {
        final @NotNull String project = Projects.approved();
        add(People.in(project, "recordEntries").asked(), project, Map.of("name", "Sand Co")).then().statusCode(403);
        add(field(project).asked(), project, Map.of("name", "Sand Co")).then().statusCode(201);
    }

    @Test(description = "Only finance changes suppliers", testName = "8b4b293e-1650-4309-a6fe-67fbcafd1315", priority = 9)
    public void onlyFinanceChangesSuppliers() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person adder = field(project);
        final @NotNull String id = add(adder.asked(), project, Map.of("name", "Sand Co")).then().statusCode(201).extract().path("id");
        given(adder.asked()).body(Map.of("name", "Taken")).put("/projects/" + project + "/suppliers/" + id).then().statusCode(403);
        given(adder.asked()).post("/projects/" + project + "/suppliers/" + id + "/disable").then().statusCode(403);
    }

    @Test(description = "A finished project refuses what field adds", testName = "4154f327-5da4-4bef-8dbb-793ba1a4e714", priority = 10)
    public void aFinishedProjectRefusesWhatFieldAdds() {
        final @NotNull String project = Projects.approved();
        final @NotNull Person adder = field(project);
        given(Api.asAdmin()).post("/projects/" + project + "/close").then().statusCode(200);
        add(adder.asked(), project, Map.of("name", "Sand Co")).then().statusCode(409).body("code", equalTo("projectFinished"));
    }
}
