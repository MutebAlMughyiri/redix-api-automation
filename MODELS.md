# The API models

A request class and a response record for every route of the REDIX API that takes or answers JSON, under `src/test/java/redix/model`,
one package per package of the Go API that owns the route. Each is written by Testin's own **API Model from JSON** writer, so it looks
exactly as the dialog would make it: the request a Lombok class to build, the response a record to read (Testin Rule-CODEGEN-100 to 104).

| Where the JSON came from | What was taken from the Go API's own types |
| --- | --- |
| Every request .NET's tests sent (redix-api `testdata/golden`, recorded 16 Nov 2026), and Go's own answer to each, from replaying them; the time zone routes from a Dev server | A money field (`fixed.Number`, `Total`, `Written` in Go) is a `BigDecimal`, exact to its four decimals (BR-13), never a `Double`, which cannot hold 0.10 exactly; `columnValues` is a `Map<String, String>` and a sync's `lists` a `Map<String, Object>`; a payment method's `accountIds`, which no recorded request sent |

Every refusal the API answers is `application/problem+json`, read as `redix.model.ProblemResponse`.

A response record has every field Go answers. RestAssured's Jackson refuses a field the record does not know, so a field Go adds later fails
the test that reads it, naming the field: make the model again then. The models are made again from Go's OpenAPI document once
redix-api #142 is built.

## Routes with models (114)

| Route | Request | Response |
| --- | --- | --- |
| `GET /projects/{projectId:guid}/accounts` | - | `redix.model.accounts.AccountsResponse` |
| `POST /projects/{projectId:guid}/accounts` | `redix.model.accounts.AddAccountRequest` | - |
| `GET /projects/{projectId:guid}/accounts/offered` | - | `redix.model.accounts.OfferedAccountsResponse` |
| `PUT /projects/{projectId:guid}/accounts/{accountId:guid}` | `redix.model.accounts.EditAccountRequest` | - |
| `POST /projects/{projectId:guid}/reconciliations` | `redix.model.accounts.EnterReconciliationRequest` | `redix.model.accounts.EnterReconciliationResponse` |
| `GET /projects/{projectId:guid}/reconciliations/latest` | - | `redix.model.accounts.LatestReconciliationResponse` |
| `POST /projects/{projectId:guid}/transfers` | `redix.model.accounts.TransferMoneyRequest` | - |
| `POST /projects/{projectId:guid}/attachments` | - | `redix.model.attachments.UploadAttachmentResponse` |
| `POST /projects/{projectId:guid}/attachments/document` | - | `redix.model.attachments.UploadDocumentAttachmentResponse` |
| `POST /auth/biometrics` | `redix.model.auth.SetBiometricsRequest` | `redix.model.auth.SetBiometricsResponse` |
| `POST /auth/change-password` | `redix.model.auth.ChangePasswordRequest` | `redix.model.auth.ChangePasswordResponse` |
| `POST /auth/forgot-password` | `redix.model.auth.ForgotPasswordRequest` | - |
| `POST /auth/refresh` | `redix.model.auth.RefreshSessionRequest` | `redix.model.auth.RefreshSessionResponse` |
| `POST /auth/reset-with-code` | `redix.model.auth.ResetWithCodeRequest` | - |
| `POST /auth/sign-in` | `redix.model.auth.SignInRequest` | `redix.model.auth.SignInResponse` |
| `POST /me/email` | `redix.model.auth.ChangeEmailRequest` | - |
| `POST /me/email/confirm` | `redix.model.auth.ConfirmEmailRequest` | - |
| `GET /config` | - | `redix.model.config.ConfigResponse` |
| `GET /projects/{projectId:guid}/contracts` | - | `redix.model.contracts.ContractsResponse` |
| `POST /projects/{projectId:guid}/contracts` | `redix.model.contracts.AddContractRequest` | - |
| `PUT /projects/{projectId:guid}/contracts/{contractId:guid}` | `redix.model.contracts.AmendContractRequest` | - |
| `GET /projects/{projectId:guid}/documents` | - | `redix.model.documents.DocumentsResponse` |
| `POST /projects/{projectId:guid}/documents` | - | `redix.model.documents.AddDocumentResponse` |
| `DELETE /projects/{projectId:guid}/documents/{documentId:guid}` | - | `redix.model.documents.RemoveDocumentResponse` |
| `GET /projects/{projectId:guid}/accounts/history` | - | `redix.model.entries.AccountHistoryResponse` |
| `GET /projects/{projectId:guid}/approvals/history` | - | `redix.model.entries.ApprovalHistoryResponse` |
| `GET /projects/{projectId:guid}/entries` | - | `redix.model.entries.EntriesResponse` |
| `POST /projects/{projectId:guid}/entries` | `redix.model.entries.RecordEntryRequest` | `redix.model.entries.RecordEntryResponse` |
| `POST /projects/{projectId:guid}/entries/approve` | `redix.model.entries.ApproveEntriesRequest` | `redix.model.entries.ApproveEntriesResponse` |
| `GET /projects/{projectId:guid}/entries/attachable` | - | `redix.model.entries.AttachableEntriesResponse` |
| `GET /projects/{projectId:guid}/entries/mine` | - | `redix.model.entries.MyEntriesResponse` |
| `GET /projects/{projectId:guid}/entries/pending` | - | `redix.model.entries.PendingEntriesResponse` |
| `GET /projects/{projectId:guid}/entries/{entryId:guid}` | - | `redix.model.entries.EntryResponse` |
| `PUT /projects/{projectId:guid}/entries/{entryId:guid}` | `redix.model.entries.ChangeEntryRequest` | `redix.model.entries.ChangeEntryResponse` |
| `PUT /projects/{projectId:guid}/entries/{entryId:guid}/description` | `redix.model.entries.DescribeEntryRequest` | - |
| `POST /projects/{projectId:guid}/entries/{entryId:guid}/invoice` | `redix.model.entries.AttachInvoiceRequest` | `redix.model.entries.AttachInvoiceResponse` |
| `POST /projects/{projectId:guid}/entries/{entryId:guid}/reject` | `redix.model.entries.RejectEntryRequest` | - |
| `GET /projects/{projectId:guid}/entries/{entryId:guid}/row` | - | `redix.model.entries.EntryRowResponse` |
| `POST /projects/{projectId:guid}/entries/{entryId:guid}/submit` | - | `redix.model.entries.SubmitEntryResponse` |
| `GET /projects/{projectId:guid}/entry-tables` | - | `redix.model.entries.EntryTablesResponse` |
| `GET /projects/{projectId:guid}/items/{itemId:guid}/tables` | - | `redix.model.entries.ItemTablesResponse` |
| `GET /projects/{projectId:guid}/partners/history` | - | `redix.model.entries.PartnerHistoryResponse` |
| `GET /projects/{projectId:guid}/reports/contracts/{contractId:guid}` | - | `redix.model.entries.ContractReportResponse` |
| `GET /projects/{projectId:guid}/reports/items/{itemId:guid}` | - | `redix.model.entries.ItemReportResponse` |
| `GET /notifications` | - | `redix.model.notifications.NotificationsResponse` |
| `GET /projects/{projectId:guid}/capital` | - | `redix.model.partners.CapitalResponse` |
| `GET /projects/{projectId:guid}/partners` | - | `redix.model.partners.PartnersResponse` |
| `POST /projects/{projectId:guid}/partners` | `redix.model.partners.AddPartnerRequest` | - |
| `PUT /projects/{projectId:guid}/partners/{partnerId:guid}` | `redix.model.partners.EditPartnerRequest` | - |
| `GET /projects/{projectId:guid}/partners/{partnerId:guid}/movements` | - | `redix.model.partners.PartnerMovementsResponse` |
| `POST /projects/{projectId:guid}/project-file` | - | `redix.model.projectfile.LoadProjectFileResponse` |
| `GET /projects` | - | `redix.model.projects.ProjectsResponse` |
| `POST /projects` | `redix.model.projects.CreateProjectRequest` | `redix.model.projects.CreateProjectResponse` |
| `DELETE /projects/default` | - | `redix.model.projects.RemoveDefaultProjectResponse` |
| `POST /projects/register` | `redix.model.projects.RegisterProjectRequest` | `redix.model.projects.RegisterProjectResponse` |
| `GET /projects/registered` | - | `redix.model.projects.RegisteredProjectsResponse` |
| `PUT /projects/{projectId:guid}` | `redix.model.projects.RenameProjectRequest` | `redix.model.projects.RenameProjectResponse` |
| `POST /projects/{projectId:guid}/close` | - | `redix.model.projects.CloseProjectResponse` |
| `POST /projects/{projectId:guid}/default` | - | `redix.model.projects.SetDefaultProjectResponse` |
| `PUT /projects/{projectId:guid}/description` | `redix.model.projects.DescribeProjectRequest` | `redix.model.projects.DescribeProjectResponse` |
| `POST /projects/{projectId:guid}/reject` | `redix.model.projects.RejectProjectRequest` | - |
| `PUT /projects/{projectId:guid}/time-zone` | `redix.model.projects.SetTimeZoneRequest` | - |
| `POST /projects/{projectId:guid}/units` | `redix.model.projects.AddUnitRequest` | `redix.model.projects.AddUnitResponse` |
| `DELETE /projects/{projectId:guid}/units/{unitId:guid}` | - | `redix.model.projects.DeleteUnitResponse` |
| `PUT /projects/{projectId:guid}/units/{unitId:guid}` | `redix.model.projects.RenameUnitRequest` | `redix.model.projects.RenameUnitResponse` |
| `GET /time-zones` | - | `redix.model.projects.TimeZonesResponse` |
| `GET /projects/{projectId:guid}/periodic-reports` | - | `redix.model.reports.PeriodicReportsResponse` |
| `POST /projects/{projectId:guid}/periodic-reports` | - | `redix.model.reports.IssuePeriodicReportResponse` |
| `GET /projects/{projectId:guid}/periodic-reports/next` | - | `redix.model.reports.NextPeriodicReportResponse` |
| `GET /projects/{projectId:guid}/periodic-reports/{number:int}` | - | `redix.model.reports.PeriodicReportResponse` |
| `GET /projects/{projectId:guid}/summary` | - | `redix.model.reports.SummaryResponse` |
| `GET /projects/{projectId:guid}/payment-methods` | - | `redix.model.settings.PaymentMethodsResponse` |
| `POST /projects/{projectId:guid}/payment-methods` | `redix.model.settings.AddPaymentMethodRequest` | - |
| `GET /projects/{projectId:guid}/payment-methods/offered` | - | `redix.model.settings.OfferedPaymentMethodsResponse` |
| `PUT /projects/{projectId:guid}/payment-methods/{paymentMethodId:guid}` | `redix.model.settings.RenamePaymentMethodRequest` | - |
| `GET /projects/{projectId:guid}/categories` | - | `redix.model.structure.CategoriesResponse` |
| `POST /projects/{projectId:guid}/categories` | `redix.model.structure.AddCategoryRequest` | `redix.model.structure.AddCategoryResponse` |
| `GET /projects/{projectId:guid}/categories/offered` | - | `redix.model.structure.OfferedCategoriesResponse` |
| `POST /projects/{projectId:guid}/categories/reorder` | `redix.model.structure.ReorderCategoriesRequest` | - |
| `PUT /projects/{projectId:guid}/categories/{categoryId:guid}` | `redix.model.structure.RenameCategoryRequest` | - |
| `POST /projects/{projectId:guid}/categories/{categoryId:guid}/items` | `redix.model.structure.AddItemRequest` | `redix.model.structure.AddItemResponse` |
| `POST /projects/{projectId:guid}/categories/{categoryId:guid}/items/reorder` | `redix.model.structure.ReorderItemsRequest` | - |
| `GET /projects/{projectId:guid}/default-columns/{itemType}` | - | `redix.model.structure.DefaultColumnsResponse` |
| `POST /projects/{projectId:guid}/default-columns/{itemType}` | `redix.model.structure.AddDefaultColumnRequest` | - |
| `POST /projects/{projectId:guid}/default-columns/{itemType}/reorder` | `redix.model.structure.ReorderDefaultColumnsRequest` | - |
| `PUT /projects/{projectId:guid}/default-columns/{itemType}/{columnId:guid}` | `redix.model.structure.EditDefaultColumnRequest` | - |
| `PUT /projects/{projectId:guid}/items/{itemId:guid}` | `redix.model.structure.RenameItemRequest` | - |
| `GET /projects/{projectId:guid}/items/{itemId:guid}/columns` | - | `redix.model.structure.ItemColumnsResponse` |
| `POST /projects/{projectId:guid}/items/{itemId:guid}/columns` | `redix.model.structure.AddItemColumnRequest` | - |
| `POST /projects/{projectId:guid}/items/{itemId:guid}/columns/reorder` | `redix.model.structure.ReorderItemColumnsRequest` | - |
| `PUT /projects/{projectId:guid}/items/{itemId:guid}/columns/{columnId:guid}` | `redix.model.structure.EditItemColumnRequest` | - |
| `POST /projects/{projectId:guid}/items/{itemId:guid}/subgroups` | `redix.model.structure.AddSubgroupRequest` | - |
| `PUT /projects/{projectId:guid}/items/{itemId:guid}/subgroups/{subgroupId:guid}` | `redix.model.structure.RenameSubgroupRequest` | - |
| `PUT /projects/{projectId:guid}/items/{itemId:guid}/table-percentages` | `redix.model.structure.SetTablePercentagesRequest` | - |
| `POST /projects/{projectId:guid}/items/{itemId:guid}/tables/reorder` | `redix.model.structure.ReorderTablesRequest` | - |
| `PUT /projects/{projectId:guid}/items/{itemId:guid}/type` | `redix.model.structure.ChangeItemTypeRequest` | - |
| `GET /projects/{projectId:guid}/suppliers` | - | `redix.model.suppliers.SuppliersResponse` |
| `POST /projects/{projectId:guid}/suppliers` | `redix.model.suppliers.AddSupplierRequest` | `redix.model.suppliers.AddSupplierResponse` |
| `GET /projects/{projectId:guid}/suppliers/search` | - | `redix.model.suppliers.SearchSuppliersResponse` |
| `PUT /projects/{projectId:guid}/suppliers/{supplierId:guid}` | `redix.model.suppliers.EditSupplierRequest` | - |
| `GET /projects/{projectId:guid}/sync` | - | `redix.model.sync.SyncResponse` |
| `GET /projects/{projectId:guid}/members` | - | `redix.model.users.MembersResponse` |
| `GET /projects/{projectId:guid}/users` | - | `redix.model.users.UsersResponse` |
| `POST /projects/{projectId:guid}/users` | `redix.model.users.CreateUserRequest` | `redix.model.users.CreateUserResponse` |
| `POST /projects/{projectId:guid}/users/existing` | `redix.model.users.AddExistingUserRequest` | `redix.model.users.AddExistingUserResponse` |
| `POST /projects/{projectId:guid}/users/{userId:guid}/disable` | - | `redix.model.users.DisableUserResponse` |
| `POST /projects/{projectId:guid}/users/{userId:guid}/enable` | - | `redix.model.users.EnableUserResponse` |
| `DELETE /projects/{projectId:guid}/users/{userId:guid}/partners/{partnerId:guid}` | - | `redix.model.users.UnchoosePartnerResponse` |
| `POST /projects/{projectId:guid}/users/{userId:guid}/partners/{partnerId:guid}` | - | `redix.model.users.ChoosePartnerResponse` |
| `DELETE /projects/{projectId:guid}/users/{userId:guid}/permissions/{permission}` | - | `redix.model.users.TakePermissionResponse` |
| `POST /projects/{projectId:guid}/users/{userId:guid}/permissions/{permission}` | - | `redix.model.users.GivePermissionResponse` |
| `PUT /projects/{projectId:guid}/users/{userId:guid}/role-name` | `redix.model.users.SetRoleNameRequest` | `redix.model.users.SetRoleNameResponse` |
| `PUT /users/{userId:guid}` | `redix.model.users.EditUserRequest` | `redix.model.users.EditUserResponse` |
| `POST /users/{userId:guid}/reset-password` | `redix.model.users.ResetUserPasswordRequest` | `redix.model.users.ResetUserPasswordResponse` |

## Routes without a model (36)

| Route | Why |
| --- | --- |
| `DELETE /projects/{projectId:guid}/accounts/{accountId:guid}` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/accounts/{accountId:guid}/disable` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/accounts/{accountId:guid}/enable` | takes no body and answers with none |
| `GET /files/{link}` | answers a file |
| `GET /projects/{projectId:guid}/attachments/{attachmentId:guid}` | answers a file |
| `POST /auth/sign-out` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/contracts/{contractId:guid}/disable` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/contracts/{contractId:guid}/enable` | takes no body and answers with none |
| `DELETE /projects/{projectId:guid}/entries/{entryId:guid}` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/entries/{entryId:guid}/restore` | takes no body and answers with none |
| `POST /notifications/{id:guid}/read` | takes no body and answers with none |
| `DELETE /projects/{projectId:guid}/partners/{partnerId:guid}` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/approve` | takes no body and answers with none |
| `GET /projects/{projectId:guid}/periodic-reports/{number:int}/pdf` | answers a file |
| `GET /projects/{projectId:guid}/periodic-reports/{number:int}/xlsx` | answers a file |
| `GET /projects/{projectId:guid}/reports/contracts/pdf` | answers a file |
| `GET /projects/{projectId:guid}/reports/contracts/xlsx` | answers a file |
| `GET /projects/{projectId:guid}/reports/contracts/{contractId:guid}/pdf` | answers a file |
| `GET /projects/{projectId:guid}/reports/contracts/{contractId:guid}/xlsx` | answers a file |
| `GET /projects/{projectId:guid}/reports/items/{itemId:guid}/pdf` | answers a file |
| `GET /projects/{projectId:guid}/reports/items/{itemId:guid}/xlsx` | answers a file |
| `GET /projects/{projectId:guid}/vat-statement/xlsx` | answers a file |
| `GET /health` | answers the text Healthy |
| `DELETE /projects/{projectId:guid}/payment-methods/{paymentMethodId:guid}` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/payment-methods/{paymentMethodId:guid}/disable` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/payment-methods/{paymentMethodId:guid}/enable` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/categories/{categoryId:guid}/disable` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/categories/{categoryId:guid}/enable` | takes no body and answers with none |
| `DELETE /projects/{projectId:guid}/default-columns/{itemType}/{columnId:guid}` | takes no body and answers with none |
| `DELETE /projects/{projectId:guid}/items/{itemId:guid}/columns/{columnId:guid}` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/items/{itemId:guid}/disable` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/items/{itemId:guid}/enable` | takes no body and answers with none |
| `DELETE /projects/{projectId:guid}/items/{itemId:guid}/subgroups/{subgroupId:guid}` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/suppliers/{supplierId:guid}/disable` | takes no body and answers with none |
| `POST /projects/{projectId:guid}/suppliers/{supplierId:guid}/enable` | takes no body and answers with none |
| `GET /translations/{language}` | every word of a language by its key: read it as Map<String, String> |
