// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import io.restassured.path.json.JsonPath;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/** Entries a test needs in a project: a purchase of two units at 100 into its first table, paid by its first method from a bank account (UC-022). */
public final class Entries {
    private Entries() {
    }

    /** A purchase the admin recorded and kept as a draft. */
    public static @NotNull String draft(final @NotNull String projectId) {
        return record(projectId, false);
    }

    /** A purchase the admin recorded and sent for approval (UC-028). */
    public static @NotNull String waiting(final @NotNull String projectId) {
        return record(projectId, true);
    }

    /** A purchase the admin recorded, sent and approved (UC-032). */
    public static @NotNull String approved(final @NotNull String projectId) {
        final @NotNull String entryId = waiting(projectId);
        given(Api.asAdmin()).body(Map.of("entryIds", new String[] {entryId})).post("/projects/" + projectId + "/entries/approve").then().statusCode(200);
        return entryId;
    }

    /** A purchase the admin recorded, sent and rejected (UC-032). */
    public static @NotNull String rejected(final @NotNull String projectId) {
        final @NotNull String entryId = waiting(projectId);
        given(Api.asAdmin()).body(Map.of("reason", "Wrong supplier")).post("/projects/" + projectId + "/entries/" + entryId + "/reject").then().statusCode(200);
        return entryId;
    }

    private static @NotNull String record(final @NotNull String projectId, final boolean submit) {
        final @NotNull String project = "/projects/" + projectId;
        final @NotNull JsonPath tables = given(Api.asAdmin()).get(project + "/entry-tables").then().statusCode(200).extract().jsonPath();
        final @NotNull JsonPath accounts = given(Api.asAdmin()).get(project + "/accounts").then().statusCode(200).extract().jsonPath();
        if (accounts.getList("$").isEmpty()) {
            given(Api.asAdmin()).body(Map.of("name", "Bank", "type", "bank")).post(project + "/accounts").then().statusCode(200);
        }
        final @NotNull String accountId = given(Api.asAdmin()).get(project + "/accounts").then().statusCode(200).extract().path("[0].id");
        final @NotNull String paymentMethodId = given(Api.asAdmin()).get(project + "/payment-methods").then().statusCode(200).extract().path("[0].id");
        final @NotNull String entryId = UUID.randomUUID().toString();
        given(Api.asAdmin()).body(Map.of("id", entryId, "tableId", tables.getString("[0].items[0].tables[0].id"), "itemId", tables.getString("[0].items[0].id"),
                        "paymentMethodId", paymentMethodId, "accountId", accountId, "date", LocalDate.now().toString(), "description", "Cement",
                        "columnValues", Map.of("unitPrice", "100", "quantity", "2"), "submit", submit))
                .post(project + "/entries").then().statusCode(201);
        return entryId;
    }
}
