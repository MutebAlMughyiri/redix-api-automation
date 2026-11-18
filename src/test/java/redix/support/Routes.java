// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import io.restassured.path.json.JsonPath;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;

/** Every route of the API, as its OpenAPI document names them, which Dev and QA serve at /openapi/v1.json (redix-api #142). */
public final class Routes {
    private static final @NotNull Pattern NEEDS = Pattern.compile("Access: needs the (\\w+) permission");

    private Routes() {
    }

    /** One route: its method, its path with {values}, and what it needs. */
    public record Route(@NotNull String method, @NotNull String path, @NotNull String description, boolean open) {
        /** The permission its gate needs, as the API names it - "manageSettings" - or "" for a route that needs none. */
        public @NotNull String permission() {
            final @NotNull Matcher needs = NEEDS.matcher(description);
            return needs.find() ? Character.toLowerCase(needs.group(1).charAt(0)) + needs.group(1).substring(1) : "";
        }

        /** Whether a session whose password is still temporary may use it: only the routes for any session, such as changing that password. */
        public boolean takesATemporaryPassword() {
            return description.contains("Access: any session");
        }

        /** The path with projectId as its project, a new UUID for every other id and 1 for a number, as a request names it. */
        public @NotNull String in(final @NotNull String projectId) {
            return path.replace("{projectId}", projectId).replaceAll("\\{(number|language|link|permission)}", "1").replaceAll("\\{\\w+}", UUID.randomUUID().toString());
        }

        @Override
        public @NotNull String toString() {
            return method + " " + path;
        }
    }

    /** Every route the document describes. */
    public static @NotNull List<Route> every() {
        final @NotNull JsonPath document = given(Api.anonymous()).get("/openapi/v1.json").then().statusCode(200).extract().jsonPath();
        final @NotNull List<Route> routes = new ArrayList<>();
        document.<String, Map<String, Map<String, Object>>>getMap("paths").forEach((path, operations) -> operations.forEach((method, operation) ->
                routes.add(new Route(method.toUpperCase(), path, String.valueOf(operation.get("description")), List.of().equals(operation.get("security"))))));
        return routes;
    }
}
