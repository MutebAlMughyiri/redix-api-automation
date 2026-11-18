// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;

/** Entries a test needs in a project: a purchase of two units at 100 into its first table, paid by its first method from a bank account (UC-022). */
public final class Entries {
    private Entries() {
    }

    /** A purchase the admin recorded and kept as a draft. */
    public static @NotNull String draft(final @NotNull String projectId) {
        return record(projectId, Api.asAdmin(), false);
    }

    /** A purchase recordedBy recorded and kept as a draft: someone holding RecordEntries, and ChargeAnyAccount to pay from the project's bank. */
    public static @NotNull String draft(final @NotNull String projectId, final @NotNull RequestSpecification recordedBy) {
        return record(projectId, recordedBy, false);
    }

    /** A purchase the admin recorded into one table of an item and kept as a draft. */
    public static @NotNull String draftIn(final @NotNull String projectId, final @NotNull String itemId, final @NotNull String tableId) {
        return draftIn(projectId, itemId, tableId, Map.of()).then().statusCode(201).extract().path("id");
    }

    /** What recording a purchase into one table of an item answers, its columns' values beside unit price and quantity those columns names. */
    public static @NotNull Response draftIn(final @NotNull String projectId, final @NotNull String itemId, final @NotNull String tableId, final @NotNull Map<String, Object> columns) {
        return send(projectId, Api.asAdmin(), false, itemId, tableId, columns);
    }

    /**
     * A capital movement recordedBy kept as a draft on a partner of the project's رأس المال (UC-016): amount put in from the project's bank, or
     * taken out when takeOut. recordedBy moves money and charges any account.
     */
    public static @NotNull String capital(final @NotNull String projectId, final @NotNull String partnerId, final @NotNull RequestSpecification recordedBy, final boolean takeOut, final int amount) {
        final @NotNull String project = "/projects/" + projectId;
        final @NotNull String capital = Structure.itemId(projectId, Structure.CAPITAL);
        final @NotNull String movement = given(Api.asAdmin()).get(project + "/items/" + capital + "/columns").then().statusCode(200).extract()
                .path("find { it.key == 'movementType' }.choices[" + (takeOut ? 1 : 0) + "].id");
        if (given(Api.asAdmin()).get(project + "/accounts").then().statusCode(200).extract().jsonPath().getList("$").isEmpty()) {
            given(Api.asAdmin()).body(Map.of("name", "Bank", "type", "bank")).post(project + "/accounts").then().statusCode(200);
        }
        final @NotNull String accountId = given(Api.asAdmin()).get(project + "/accounts").then().statusCode(200).extract().path("[0].id");
        return given(recordedBy).body(Map.of("id", UUID.randomUUID().toString(), "tableId", partnerId, "itemId", capital, "accountId", accountId, "date", LocalDate.now().toString(),
                "description", "Capital", "amount", amount, "columnValues", Map.of("movementType", movement))).post(project + "/entries").then().statusCode(201).extract().path("id");
    }

    /** Sends the admin's draft for approval. */
    public static void submit(final @NotNull String projectId, final @NotNull String entryId) {
        given(Api.asAdmin()).post("/projects/" + projectId + "/entries/" + entryId + "/submit").then().statusCode(200);
    }

    /** Approves an entry waiting for approval, as the admin. */
    public static void approve(final @NotNull String projectId, final @NotNull String entryId) {
        given(Api.asAdmin()).body(Map.of("entryIds", new String[] {entryId})).post("/projects/" + projectId + "/entries/approve").then().statusCode(200)
                .body("approved", hasItem(entryId));
    }

    /** A purchase the admin recorded and sent for approval (UC-028). */
    public static @NotNull String waiting(final @NotNull String projectId) {
        return record(projectId, Api.asAdmin(), true);
    }

    /** A purchase the admin recorded, sent and approved (UC-032). */
    public static @NotNull String approved(final @NotNull String projectId) {
        final @NotNull String entryId = waiting(projectId);
        approve(projectId, entryId);
        return entryId;
    }

    /** A purchase the admin recorded, sent and rejected (UC-032). */
    public static @NotNull String rejected(final @NotNull String projectId) {
        final @NotNull String entryId = waiting(projectId);
        given(Api.asAdmin()).body(Map.of("reason", "Wrong supplier")).post("/projects/" + projectId + "/entries/" + entryId + "/reject").then().statusCode(200);
        return entryId;
    }

    private static @NotNull String record(final @NotNull String projectId, final @NotNull RequestSpecification recordedBy, final boolean submit) {
        final @NotNull JsonPath tables = given(Api.asAdmin()).get("/projects/" + projectId + "/entry-tables").then().statusCode(200).extract().jsonPath();
        return send(projectId, recordedBy, submit, tables.getString("[0].items[0].id"), tables.getString("[0].items[0].tables[0].id"), Map.of()).then().statusCode(201).extract().path("id");
    }

    private static @NotNull Response send(final @NotNull String projectId, final @NotNull RequestSpecification recordedBy, final boolean submit, final @NotNull String itemId, final @NotNull String tableId, final @NotNull Map<String, Object> columns) {
        final @NotNull String project = "/projects/" + projectId;
        final @NotNull JsonPath accounts = given(Api.asAdmin()).get(project + "/accounts").then().statusCode(200).extract().jsonPath();
        if (accounts.getList("$").isEmpty()) {
            given(Api.asAdmin()).body(Map.of("name", "Bank", "type", "bank")).post(project + "/accounts").then().statusCode(200);
        }
        final @NotNull String accountId = given(Api.asAdmin()).get(project + "/accounts").then().statusCode(200).extract().path("[0].id");
        final @NotNull String paymentMethodId = given(Api.asAdmin()).get(project + "/payment-methods").then().statusCode(200).extract().path("[0].id");
        final @NotNull Map<String, Object> values = new HashMap<>(Map.of("unitPrice", "100", "quantity", "2"));
        values.putAll(columns);
        return given(recordedBy).body(Map.of("id", UUID.randomUUID().toString(), "tableId", tableId, "itemId", itemId, "paymentMethodId", paymentMethodId, "accountId", accountId,
                "date", LocalDate.now().toString(), "description", "Cement", "columnValues", values, "submit", submit)).post(project + "/entries");
    }
}
