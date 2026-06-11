# Application Delete Frontend Spec

## Backend status

Implemented.

Deleting an application removes the application and its dependent FlowOps data:

- API endpoints
- Test cases and versions
- Test generations, selections, and generated drafts
- Scenarios and scenario steps
- Executions, execution step logs, validation results, and execution-linked reports
- Environments and trigger rules
- GitHub repository links, repository branches, and repository-scoped API inventory

Project-level data that is not directly tied to the application is not deleted.

## Delete application

`DELETE /apps/{appId}`

Path parameters:

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `appId` | number | yes | Application ID to delete |

Request body: none

Success response:

```json
{
  "success": true,
  "data": null
}
```

Error responses:

```json
{
  "success": false,
  "code": "COMMON-404",
  "message": "Requested resource was not found."
}
```

Use `404` when the app no longer exists.

## Frontend behavior

Recommended UI flow:

1. Open a confirmation dialog before calling the API.
2. Show the app name in the dialog.
3. Disable the confirm button while the request is pending.
4. On success:
   - Remove the app from local app lists.
   - If the deleted app is currently selected, clear the selected app or navigate to the app/project overview.
   - Invalidate app-scoped queries such as environments, repositories, API inventory, test cases, scenarios, executions, and dashboard data.
5. On failure:
   - Keep the app visible.
   - Re-enable the confirm button.
   - Show the API error message.

## React Query example

```ts
async function deleteApplication(appId: number) {
  return api.delete(`/apps/${appId}`);
}

const mutation = useMutation({
  mutationFn: deleteApplication,
  onSuccess: (_, appId) => {
    queryClient.invalidateQueries({ queryKey: ["apps"] });
    queryClient.removeQueries({ queryKey: ["app", appId] });
    queryClient.removeQueries({ queryKey: ["dashboard", appId] });
    queryClient.removeQueries({ queryKey: ["repositories", appId] });
    queryClient.removeQueries({ queryKey: ["api-inventory", appId] });
    queryClient.removeQueries({ queryKey: ["test-cases", appId] });
    queryClient.removeQueries({ queryKey: ["scenarios", appId] });
    queryClient.removeQueries({ queryKey: ["executions", appId] });
  }
});
```

## UX copy

Confirmation title:

```text
Delete application?
```

Confirmation body:

```text
This will delete the application and its FlowOps data, including environments, repositories, API inventory, test cases, scenarios, and execution history.
```

Primary action:

```text
Delete
```

Cancel action:

```text
Cancel
```
