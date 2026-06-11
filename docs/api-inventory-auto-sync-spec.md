# API Inventory Auto Sync Frontend Spec

## Current backend status

Implemented.

API Inventory can be refreshed automatically from GitHub webhooks when:

- The repository is registered in FlowOps.
- `autoSyncEnabled` is `true` for that repository.
- GitHub sends a `push` event whose `head_commit.message` or any item in `commits[].message` starts with `Merge`.
- The pushed branch is included in `external.github.inventory-webhook-branches`.
  - Default: `dev,staging,main`
  - Environment variable: `GITHUB_WEBHOOK_INVENTORY_BRANCHES`

Manual scan remains separate and works regardless of `autoSyncEnabled`.

## Repository model

Repository responses now include:

```json
{
  "id": 20,
  "projectId": 1,
  "appId": 1,
  "fullName": "flowops/backend",
  "repositoryUrl": "https://github.com/flowops/backend",
  "defaultBranch": "main",
  "connectionStatus": "ACTIVE",
  "autoSyncEnabled": true,
  "branches": [
    {
      "name": "main",
      "isDefault": true,
      "selected": true
    }
  ],
  "scanResults": []
}
```

Use `autoSyncEnabled` to render the Auto Sync toggle state.

## Register repository

`POST /projects/{projectId}/repositories`

Request:

```json
{
  "fullName": "flowops/backend",
  "appId": 1,
  "selectedBranches": ["main", "develop"],
  "autoSyncEnabled": true
}
```

Notes:

- `autoSyncEnabled` is optional.
- If omitted for a new repository, the backend defaults it to `true`.
- If the repository already exists and `autoSyncEnabled` is provided, the backend updates the existing repository setting.

Response:

```json
{
  "success": true,
  "data": {
    "id": 20,
    "projectId": 1,
    "appId": 1,
    "fullName": "flowops/backend",
    "repositoryUrl": "https://github.com/flowops/backend",
    "defaultBranch": "main",
    "connectionStatus": "ACTIVE",
    "autoSyncEnabled": true,
    "branches": [],
    "scanResults": []
  }
}
```

## List repositories

`GET /projects/{projectId}/repositories`

Response `data[]` items include `autoSyncEnabled`.

## Get repository detail

`GET /projects/{projectId}/repositories/{repositoryId}`

Response `data` includes `autoSyncEnabled`.

## Update Auto Sync

`PATCH /projects/{projectId}/repositories/{repositoryId}/auto-sync`

Request:

```json
{
  "autoSyncEnabled": true
}
```

Response:

```json
{
  "success": true,
  "data": {
    "id": 20,
    "projectId": 1,
    "appId": 1,
    "fullName": "flowops/backend",
    "repositoryUrl": "https://github.com/flowops/backend",
    "defaultBranch": "main",
    "connectionStatus": "ACTIVE",
    "autoSyncEnabled": true,
    "branches": [],
    "scanResults": []
  }
}
```

Frontend behavior:

- Enable the toggle when the repository `connectionStatus` is `ACTIVE`.
- Bind checked state to `autoSyncEnabled`.
- On toggle, call the PATCH endpoint and replace local repository state with the returned `data`.
- If the request fails, restore the previous toggle state and show the API error message.

## Manual scan

`POST /projects/{projectId}/repositories/{repositoryId}/scan`

Request:

```json
{
  "branchNames": ["main"]
}
```

Notes:

- If `branchNames` is omitted or empty, the backend scans selected branches.
- Manual scan does not require `autoSyncEnabled`.

## GitHub webhook

`POST /github/webhooks`

Headers:

- `X-GitHub-Event: push`
- `X-Hub-Signature-256: sha256=...` when `external.github.webhook-secret` is configured

Auto Sync scan is triggered for push payloads like:

```json
{
  "ref": "refs/heads/main",
  "repository": {
    "full_name": "flowops/backend"
  },
  "head_commit": {
    "message": "Merge: feature/api into main"
  },
  "commits": []
}
```

The backend also accepts GitHub's common merge messages such as `Merge pull request #12 from ...` because it checks whether the message starts with `Merge`.

Webhook response:

```json
{
  "success": true,
  "data": {
    "event": "push",
    "repositoryFullName": "flowops/backend",
    "scannedBranches": ["main"],
    "message": "API Inventory refresh completed."
  }
}
```

Ignored webhook responses return `scannedBranches: []`.
