// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Api;
import redix.support.People;
import redix.support.Projects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class SearchSuppliersTest {
    private static @NotNull String add(final @NotNull String projectId, final @NotNull String name, final @NotNull String vatNumber) {
        final @NotNull Map<String, Object> body = vatNumber.isEmpty() ? Map.of("name", name) : Map.of("name", name, "vatNumber", vatNumber);
        return given(Api.asAdmin()).body(body).post("/projects/" + projectId + "/suppliers").then().statusCode(201).extract().path("id");
    }

    private static @NotNull Response search(final @NotNull RequestSpecification as, final @NotNull String projectId, final @NotNull String text) {
        return given(as).queryParam("text", text).get("/projects/" + projectId + "/suppliers/search");
    }

    private static @NotNull List<String> found(final @NotNull String projectId, final @NotNull String text) {
        return search(Api.asAdmin(), projectId, text).then().statusCode(200).extract().path("name");
    }

    @Test(description = "Three letters of the name find the supplier", testName = "1362b15d-a318-4657-b846-2a3907f73e50", priority = 1)
    public void threeLettersOfTheNameFindTheSupplier() {
        final @NotNull String project = Projects.approved();
        add(project, "Cement Trading", "");
        add(project, "Steel House", "");
        assertEquals(found(project, "men"), List.of("Cement Trading"));
        assertEquals(found(project, "CEM"), List.of("Cement Trading"));
    }

    @Test(description = "A search treats arabic spelling variants alike and ignores tashkeel", testName = "feb771ed-8bf3-4757-ac07-9d6a598eafec", priority = 2)
    public void aSearchTreatsArabicSpellingVariantsAlikeAndIgnoresTashkeel() {
        final @NotNull String project = Projects.approved();
        add(project, "مؤسسة الإنشاءات", "");
        add(project, "مُحَمَّد للمقاولات", "");
        assertEquals(found(project, "موسسه"), List.of("مؤسسة الإنشاءات"));
        assertEquals(found(project, "الانشاءات"), List.of("مؤسسة الإنشاءات"));
        assertEquals(found(project, "محمد"), List.of("مُحَمَّد للمقاولات"));
        assertEquals(found(project, "مُحمّد"), List.of("مُحَمَّد للمقاولات"));
    }

    @Test(description = "Arabic spelling keeps one form of each letter and no tashkeel", testName = "43321856-7ddf-410a-b7d0-36ef15423f48", priority = 3)
    public void arabicSpellingKeepsOneFormOfEachLetterAndNoTashkeel() {
        final @NotNull String project = Projects.approved();
        add(project, "آل مصطفى", "");
        add(project, "إئتمان أمين", "");
        assertEquals(found(project, "ال مصطفي"), List.of("آل مصطفى"));
        assertEquals(found(project, "ايتمان امين"), List.of("إئتمان أمين"));
    }

    @Test(description = "An answer shows the name as it was typed", testName = "5a98fcc3-cc1c-4727-ac63-26119d21f9b7", priority = 4)
    public void anAnswerShowsTheNameAsItWasTyped() {
        final @NotNull String project = Projects.approved();
        add(project, "مُؤَسَّسَة البِناء", "");
        assertEquals(found(project, "موسسه"), List.of("مُؤَسَّسَة البِناء"));
    }

    @Test(description = "A renamed supplier is found by its new name and no longer by its old one", testName = "5575b94b-dfd6-4b24-a620-a88c1f317eb1", priority = 5)
    public void aRenamedSupplierIsFoundByItsNewNameAndNoLongerByItsOldOne() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = add(project, "Cement Trading", "");
        given(Api.asAdmin()).body(Map.of("name", "Granite House")).put("/projects/" + project + "/suppliers/" + id).then().statusCode(200);
        assertEquals(found(project, "Granite"), List.of("Granite House"));
        assertEquals(found(project, "Cement"), List.of());
    }

    @Test(description = "Tashkeel counts for nothing toward the three letters a search needs", testName = "0c5ba263-6189-4c5f-ad4a-08c2e29b14d0", priority = 6)
    public void tashkeelCountsForNothingTowardTheThreeLettersASearchNeeds() {
        final @NotNull String project = Projects.approved();
        add(project, "محمد للمقاولات", "");
        add(project, "سعد للتجارة", "");
        assertEquals(found(project, "مُحَ").size(), found(project, "").size(), "two letters with their marks narrow nothing down");
        assertEquals(found(project, "مُحَمَ"), List.of("محمد للمقاولات"));
    }

    @Test(description = "The migration fills the normalized name of suppliers saved before it", testName = "94ca8f76-4743-4a8e-bab1-1c6b6a60f99d", priority = 7, enabled = false)
    public void theMigrationFillsTheNormalizedNameOfSuppliersSavedBeforeIt() {
        throw new AssertionError("about the migration that filled the searched name of rows saved before it, inside the server; Go's internal/db tests prove it");
    }

    @Test(description = "Part of the VAT number finds the supplier", testName = "82465864-e274-4b4f-808e-3b02066b9e29", priority = 8)
    public void partOfTheVatNumberFindsTheSupplier() {
        final @NotNull String project = Projects.approved();
        add(project, "Cement Trading", "300123456700003");
        add(project, "Steel House", "300999999900003");
        assertEquals(found(project, "1234567"), List.of("Cement Trading"));
    }

    @Test(description = "Fewer than three letters narrow nothing down", testName = "6d9701c1-44cf-4f50-9452-a465ab71e1e2", priority = 9)
    public void fewerThanThreeLettersNarrowNothingDown() {
        final @NotNull String project = Projects.approved();
        add(project, "Cement Trading", "");
        add(project, "Steel House", "");
        assertEquals(found(project, "Ce"), found(project, ""));
        assertEquals(found(project, "Ce").size(), 3);
    }

    @Test(description = "No text offers the suppliers by name", testName = "1ff3f2ce-2d61-479a-9314-6aa79e16e799", priority = 10)
    public void noTextOffersTheSuppliersByName() {
        final @NotNull String project = Projects.approved();
        add(project, "Zinc Co", "");
        add(project, "Alpha Co", "");
        final @NotNull List<String> names = given(Api.asAdmin()).get("/projects/" + project + "/suppliers/search").then().statusCode(200).extract().path("name");
        final @NotNull List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareTo);
        assertEquals(names, sorted);
        assertTrue(names.containsAll(List.of("Alpha Co", "Zinc Co")));
    }

    @Test(description = "An exact VAT number finds its supplier alone", testName = "1b68b826-d399-4c7d-a0e9-c0203c137fed", priority = 11)
    public void anExactVatNumberFindsItsSupplierAlone() {
        final @NotNull String project = Projects.approved();
        add(project, "Cement Trading", "300123456700003");
        add(project, "Steel Trading", "300123456700013");
        add(project, "Sand Trading", "");
        assertEquals(found(project, "300123456700003"), List.of("Cement Trading"));
    }

    @Test(description = "A VAT number no supplier holds finds nothing", testName = "ca469cb4-e1f1-4aa2-9644-0658970fe392", priority = 12)
    public void aVatNumberNoSupplierHoldsFindsNothing() {
        final @NotNull String project = Projects.approved();
        add(project, "Cement Trading", "300123456700003");
        assertEquals(found(project, "399999999999999"), List.of());
    }

    @Test(description = "A disabled supplier is not offered", testName = "f5f11eae-8ba9-4634-a3ca-026206133fdb", priority = 13)
    public void aDisabledSupplierIsNotOffered() {
        final @NotNull String project = Projects.approved();
        final @NotNull String id = add(project, "Cement Trading", "");
        given(Api.asAdmin()).post("/projects/" + project + "/suppliers/" + id + "/disable").then().statusCode(200);
        assertEquals(found(project, "Cement"), List.of());
        assertFalse(found(project, "").contains("Cement Trading"));
    }

    @Test(description = "A search answers at most20 suppliers within a second", testName = "e904d47e-863d-405e-a8a7-46a6a734aa5d", priority = 14)
    public void aSearchAnswersAtMost20SuppliersWithinASecond() {
        final @NotNull String project = Projects.approved();
        for (int i = 10; i < 35; i++) {
            add(project, "Bulk supplier " + i, "");
        }
        final @NotNull Response answer = search(Api.asAdmin(), project, "Bulk");
        assertEquals(answer.then().statusCode(200).extract().jsonPath().getList("$").size(), 20);
        assertTrue(answer.time() < 1000, "answered in " + answer.time() + " ms");
    }

    @Test(description = "Everyone with access searches", testName = "89fb3724-f4d1-461e-ac35-ace8d58d9d7c", priority = 15)
    public void everyoneWithAccessSearches() {
        final @NotNull String project = Projects.approved();
        add(project, "Cement Trading", "");
        assertEquals(search(People.in(project).asked(), project, "Cement").then().statusCode(200).extract().path("name"), List.of("Cement Trading"));
        search(People.in(Projects.approved()).asked(), project, "Cement").then().statusCode(403);
    }

    @Test(description = "A search never reaches another project", testName = "1a32d629-3cb8-4cfe-a6cb-918dab62c28d", priority = 16)
    public void aSearchNeverReachesAnotherProject() {
        final @NotNull String project = Projects.approved();
        add(Projects.approved(), "Cement Trading", "300123456700003");
        assertEquals(found(project, "Cement"), List.of());
        assertEquals(found(project, "300123456700003"), List.of());
    }
}
