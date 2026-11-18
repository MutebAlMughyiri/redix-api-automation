// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

/** «سجل المشروع» and «سجلي» as the API reads them (UC-069, redix-api #86). */
public final class History {
    private History() {
    }

    /** The lines of «سجل المشروع» of projectId that as may see, newest first, asked with query: "" for every kind, "?area=people" for one. */
    public static @NotNull List<Map<String, Object>> of(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull String query) {
        return given(as).get("/projects/" + projectId + "/history" + query).then().statusCode(200).extract().jsonPath().get("$");
    }

    /** Each line of «سجل المشروع» of projectId that as may see, newest first, as said(line) says it. */
    public static @NotNull List<String> said(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull String query) {
        return of(as, projectId, query).stream().map(History::said).toList();
    }

    /** What line says was done to what, and its record's name when it has one: "created unit B3". */
    public static @NotNull String said(final @NotNull Map<String, Object> line) {
        final @NotNull String done = line.get("action") + " " + line.get("thing");
        return line.get("name").toString().isEmpty() ? done : done + " " + line.get("name");
    }

    /** The lines of «سجلي» of the person as is, newest first. */
    public static @NotNull List<Map<String, Object>> mine(final @NotNull RequestSpecification as) {
        return given(as).get("/me/history").then().statusCode(200).extract().jsonPath().get("$");
    }

    /** What each line of «سجلي» of the person as is says happened, newest first: "signIn". */
    public static @NotNull List<String> kindsOf(final @NotNull RequestSpecification as) {
        return mine(as).stream().map(line -> line.get("kind").toString()).toList();
    }

    /** «سجلي» of the person as is, as the API writes it, to check what it never holds. */
    public static @NotNull String mineAsWritten(final @NotNull RequestSpecification as) {
        return given(as).get("/me/history").then().statusCode(200).extract().asString();
    }

    /** The name the person userId goes by in projectId, as its members list them. */
    public static @NotNull String nameOf(final @NotNull String projectId, final @NotNull String userId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/members").then().statusCode(200).extract().path("find { it.id == '" + userId + "' }.displayName");
    }
}
