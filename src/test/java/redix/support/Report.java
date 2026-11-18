// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.markuputils.CodeLanguage;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.jetbrains.annotations.NotNull;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * The run's Extent report, target/extent-report/index.html: a page per test, named by its test case, holding each request and the API's
 * answer, then whether it passed. Before the first test it waits for the API to answer /health, so a run never starts against nothing.
 */
public final class Report implements ISuiteListener, ITestListener {
    private static final @NotNull ExtentReports REPORT = new ExtentReports();
    private static final @NotNull ThreadLocal<ExtentTest> CURRENT = new ThreadLocal<>();

    @Override
    public void onStart(final @NotNull ISuite suite) {
        REPORT.attachReporter(new ExtentSparkReporter("target/extent-report/index.html"));
        REPORT.setSystemInfo("API", Api.URL);
        waitForTheApi();
    }

    @Override
    public void onFinish(final @NotNull ISuite suite) {
        REPORT.flush();
    }

    @Override
    public void onTestStart(final @NotNull ITestResult result) {
        final String description = result.getMethod().getDescription();
        final @NotNull String name = description == null || description.isBlank() ? result.getMethod().getMethodName() : description;
        CURRENT.set(REPORT.createTest(name).assignCategory(result.getMethod().getGroups()));
    }

    @Override
    public void onTestSuccess(final @NotNull ITestResult result) {
        CURRENT.get().pass("Passed");
    }

    @Override
    public void onTestFailure(final @NotNull ITestResult result) {
        CURRENT.get().fail(result.getThrowable());
    }

    @Override
    public void onTestSkipped(final @NotNull ITestResult result) {
        if (CURRENT.get() == null) {
            onTestStart(result);
        }
        CURRENT.get().skip(result.getThrowable() == null ? "Skipped" : result.getThrowable().getMessage());
    }

    private static void waitForTheApi() {
        final @NotNull Instant until = Instant.now().plus(Duration.ofSeconds(60));
        final @NotNull HttpRequest health = HttpRequest.newBuilder(URI.create(Api.URL + "/health")).timeout(Duration.ofSeconds(2)).build();
        try (final HttpClient client = HttpClient.newHttpClient()) {
            while (Instant.now().isBefore(until)) {
                try {
                    if (client.send(health, HttpResponse.BodyHandlers.ofString()).statusCode() == 200) {
                        return;
                    }
                } catch (final java.io.IOException notYet) {
                    // The API is still starting.
                }
                Thread.sleep(1000);
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("The API at " + Api.URL + " did not answer /health within 60 seconds");
    }

    /**
     * Writes each request and the API's answer into the running test's page. A route under /auth/ shows only its method, path and status:
     * its bodies hold passwords, codes and tokens, and the report is shared.
     */
    static final class Exchange implements Filter {
        @Override
        public @NotNull Response filter(final @NotNull FilterableRequestSpecification request, final @NotNull FilterableResponseSpecification response,
                                        final @NotNull FilterContext context) {
            final @NotNull Response answer = context.next(request, response);
            final ExtentTest test = CURRENT.get();
            if (test == null) {
                return answer;
            }
            test.info(request.getMethod() + " " + request.getURI() + " → " + answer.getStatusCode());
            if (!request.getURI().contains("/auth/")) {
                final Object body = request.getBody();
                if (body != null) {
                    test.info(MarkupHelper.createCodeBlock(String.valueOf(body), CodeLanguage.JSON));
                }
                test.info(MarkupHelper.createCodeBlock(answer.asPrettyString(), CodeLanguage.JSON));
            }
            return answer;
        }
    }
}
