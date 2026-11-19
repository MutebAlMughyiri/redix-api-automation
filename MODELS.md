# The API models

A model is a Java type for one of the Go API's types, under `src/test/java/redix/model`, in the package of the Go package that owns it, and
named as Go names it in the API's OpenAPI document (redix-api `go-backend/internal/apidoc/openapi.json`, its `components`). One Go type is one
Java type, whichever routes answer it, so a field Go adds is one change in one file (findings row 272): a request is a Lombok class to build, an
answer a record to read.

Only what a test builds or reads as an object has a model. Most tests read an answer with RestAssured's `JsonPath` and need none; a model is
made when a test first needs one, from the type's component in the document.

| Model | Go type | Used for |
| --- | --- | --- |
| `auth.Session` | `auth.session` | What signing in, refreshing the session and changing the password answer |
| `auth.RefreshRequest` | `auth.refreshRequest` | Refreshing the session |
| `auth.ChangePasswordRequest` | `auth.changePasswordRequest` | Changing the password |
| `auth.ForgotPasswordRequest` | `auth.forgotPasswordRequest` | Asking for a reset code |
| `auth.ResetWithCodeRequest` | `auth.resetWithCodeRequest` | Resetting the password with the code |
| `projects.StartRequest` | `projects.startRequest` | Starting a project |
| `projects.ProjectView`, its `UnitView` | `projects.projectView`, `projects.unitView` | A project, as every project route answers it |
| `users.CreateUserRequest` | `users.createUserRequest` | Adding a person to a project |
| `users.UserView`, its `ProjectView` | `users.userView`, `users.projectView` | A person, with each project they are in |

A record has every field its Go type has. RestAssured's Jackson refuses a field the record does not know, so a field Go adds fails the test
that reads it, naming the field: add it to the record then. A money field is a `BigDecimal`, exact to its four decimals (BR-13), never a
`Double`, which cannot hold 0.10 exactly.

Until 19 Nov 2026 there was one model per route, made from Go's recorded answers: 138 types, of which 127 no test used. They were deleted, and
the 11 used ones became the 9 Go types above (findings row 272).
