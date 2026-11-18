// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import io.restassured.common.mapper.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static io.restassured.RestAssured.given;

/** The words the API says things in, as its translations bundle holds them (UC-001). */
public final class Words {
    private Words() {
    }

    /** The word key names in language, "ar" or "en". */
    public static @NotNull String of(final @NotNull String language, final @NotNull String key) {
        return given(Api.anonymous()).get("/translations/" + language).then().statusCode(200).extract().as(new TypeRef<Map<String, String>>() {}).get(key);
    }
}
