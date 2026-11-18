# REDIX API automation

The REDIX API's functional tests: Java 21, TestNG and RestAssured, with an Extent report (redix-api #117). They call the API over HTTP,
as the app does, and check what it answers.

## How the tests are written

The test cases are managed in [Testin](https://github.com/MutebAlMughyiri/test-in), in their own repository,
[redix-api-test-cases](https://github.com/MutebAlMughyiri/redix-api-test-cases). `testin.yml` links the two, so whoever opens this
project in IntelliJ with Testin lands on that test project.

| In Testin | Here |
| --- | --- |
| A test set | A class under `src/test/java`, which Testin writes |
| A test case | A `@Test` method in that class, which Testin writes with its `description`, `testName`, `groups` and `priority` |
| The steps | The method's body, which the tester writes |
| An API call's request and answer | Two types from Testin's **New → Testin API Model from JSON**: the request a Lombok class, the answer a record |

A body uses `redix.support.Api`:

```java
given(Api.asAdmin()).body(request).post("/projects").then().statusCode(201);
```

## Running them

| Variable | What | Default |
| --- | --- | --- |
| `REDIX_API_URL` | The API the tests call | `http://127.0.0.1:5010`, the Dev server on this machine |
| `REDIX_ADMIN_USERNAME`, `REDIX_ADMIN_PASSWORD` | The admin that server was started with | required for any admin request |
| `REDIX_ADMIN_NEW_PASSWORD` | The password the first sign-in changes the admin's temporary one to; on a server whose admin already changed it, the one that signs in | required for any admin request |

Start the Dev server from redix-api (`go-backend`, `REDIX_Environment=Dev`), then run `mvn test`, or a test from Testin with `F5`. Never
point them at qa: they make projects, users and entries.

Each run writes `target/extent-report/index.html`: a page per test, named by its test case, with every request and the API's answer.
Requests under `/auth/` show only their method, path and status, since their bodies hold passwords and tokens.

CI (`.github/workflows/api-tests.yml`) builds the API from redix-api's `main`, starts it on an empty database and runs every test, on
every push, nightly and on demand; the report is the run's artifact.
