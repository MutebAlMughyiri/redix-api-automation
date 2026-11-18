// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class SetUpTheEnvironmentAndBaseCodeTest {

    @Test(description = "Health answers against migrated database", testName = "c20d7979-8304-46a1-a527-0f6f98879f81", priority = 1)
    public void healthAnswersAgainstMigratedDatabase() {
        given(Api.anonymous()).get("/health").then().statusCode(200).body(equalTo("Healthy"));
    }

    @Test(description = "Bundle holds every word of the language", testName = "3861c06f-5c4c-435d-a7e1-876cafc0267a", priority = 2)
    public void bundleHoldsEveryWordOfTheLanguage() {
        final @NotNull Map<String, String> arabic = bundle("ar");
        final @NotNull Map<String, String> english = bundle("en");
        assertEquals(arabic.keySet(), english.keySet(), "Both languages hold the same words");
        assertTrue(arabic.values().stream().noneMatch(String::isBlank), "No Arabic word is blank");
        assertTrue(english.values().stream().noneMatch(String::isBlank), "No English word is blank");
        assertEquals(arabic.get("permissions.reportsGroup"), "التقارير");
        assertEquals(english.get("permissions.reportsGroup"), "Reports");
    }

    @Test(description = "Unknown language is not found", testName = "ce439b6c-dc2c-47e0-b71b-586ca8493608", priority = 3)
    public void unknownLanguageIsNotFound() {
        given(Api.anonymous()).get("/translations/fr").then().statusCode(404).contentType("application/problem+json");
    }

    @Test(description = "Unchanged bundle answers not modified", testName = "977af17b-832e-477d-a8fe-ae1de08c613a", priority = 4)
    public void unchangedBundleAnswersNotModified() {
        final @NotNull String etag = given(Api.anonymous()).get("/translations/en").then().statusCode(200).extract().header("ETag");
        given(Api.anonymous()).header("If-None-Match", etag).get("/translations/en").then().statusCode(304).body(emptyString());
        given(Api.anonymous()).header("If-None-Match", "\"OTHER\"").get("/translations/en").then().statusCode(200);
    }

    @Test(description = "Swagger is served outside production only", testName = "3fbddb79-8d93-41f7-a53b-3b970104e668", priority = 5)
    public void swaggerIsServedOutsideProductionOnly() {
        given(Api.anonymous()).get("/swagger").then().statusCode(200).contentType(ContentType.HTML).body(containsString("/openapi/v1.json"));
        given(Api.anonymous()).get("/openapi/v1.json").then().statusCode(200).contentType(ContentType.JSON).body("openapi", equalTo("3.1.0"))
                .body("paths.'/health'.get.summary", startsWith("Healthy"));
    }

    private static @NotNull Map<String, String> bundle(final @NotNull String language) {
        return given(Api.anonymous()).get("/translations/" + language).then().statusCode(200).contentType(ContentType.JSON)
                .header("ETag", startsWith("\"")).extract().as(new TypeRef<>() {});
    }
}
