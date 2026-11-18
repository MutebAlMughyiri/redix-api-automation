// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.assertFalse;

public class ConfigurableValuesAndHowLongTheAppWaitsTest {
    @Test(description = "The app reads how long to wait before anyone signs in", testName = "e5ec7217-7d2c-46a4-8b96-c1169cf5f41b", priority = 1)
    public void theAppReadsHowLongToWaitBeforeAnyoneSignsIn() {
        final @NotNull Map<String, String> values = given(Api.anonymous()).get("/config").then().statusCode(200).extract().jsonPath().getMap("$");
        assertFalse(values.isEmpty(), "the values the app waits by");
        assertFalse(values.values().stream().anyMatch(String::isBlank), "a value with nothing in it: " + values);
    }

    @Test(description = "A value changed in the database is read at the next request", testName = "7533125c-88bf-4a40-8891-dbe81a5e3bbd", priority = 2, enabled = false)
    public void aValueChangedInTheDatabaseIsReadAtTheNextRequest() {
        throw new AssertionError("no route changes a configured value, which is kept in the database; Go's internal/config tests prove it");
    }
}
