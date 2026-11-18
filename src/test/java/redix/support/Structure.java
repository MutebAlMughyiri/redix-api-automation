// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import io.restassured.path.json.JsonPath;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

/** A project's categories and items as the admin reads them (UC-012), found by their names, which a new project copies from the template. */
public final class Structure {
    /** The template every project starts from; it is no project anyone sees (00004_template.sql). */
    public static final @NotNull String TEMPLATE = "f11ea9ed-0623-ebf7-abff-3f4e8714248a";

    /** The template's categories, in the order of the report (BRS section 3). */
    public static final @NotNull List<String> TEMPLATE_CATEGORIES = List.of("تكلفة الأراضي", "الرسوم والفواتير", "العظم", "التشطيب", "الإشراف", "رأس المال",
            "استرجاع ضرائب", "إيرادات أخرى", "فلوس البيع المودعة بالحساب");

    /** An item of purchases, of contractor payments, of both, and the one رأس المال, in the template. */
    public static final @NotNull String PURCHASES = "الحديد", CONTRACTOR_PAYMENTS = "دفعات العظم", BOTH = "الحفر", CAPITAL = "رأس المال", SALE = "البيع";

    private Structure() {
    }

    /** Every category and item of the project, disabled ones included. */
    public static @NotNull JsonPath of(final @NotNull String projectId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/categories").then().statusCode(200).extract().jsonPath();
    }

    /** The id of the first category named name. */
    public static @NotNull String categoryId(final @NotNull String projectId, final @NotNull String name) {
        return of(projectId).getString("find { it.name == '" + name + "' }.id");
    }

    /** The first item named name, as the categories hold it. */
    public static @NotNull Map<String, Object> item(final @NotNull String projectId, final @NotNull String name) {
        return of(projectId).getMap("collectMany { it.items }.find { it.name == '" + name + "' }");
    }

    /** The id of the first item named name. */
    public static @NotNull String itemId(final @NotNull String projectId, final @NotNull String name) {
        return (String) item(projectId, name).get("id");
    }

    /** The item's tables in its sheet's order: its general one, each subgroup, each contract. */
    public static @NotNull JsonPath tablesOf(final @NotNull String projectId, final @NotNull String itemId) {
        return given(Api.asAdmin()).get("/projects/" + projectId + "/items/" + itemId + "/tables").then().statusCode(200).extract().jsonPath();
    }

    /** Adds a subgroup to the item and answers its id. */
    public static @NotNull String addSubgroup(final @NotNull String projectId, final @NotNull String itemId, final @NotNull String name) {
        given(Api.asAdmin()).body(Map.of("name", name)).post("/projects/" + projectId + "/items/" + itemId + "/subgroups").then().statusCode(200);
        return of(projectId).getString("collectMany { it.items }.find { it.id == '" + itemId + "' }.subgroups.find { it.name == '" + name + "' }.id");
    }
}
