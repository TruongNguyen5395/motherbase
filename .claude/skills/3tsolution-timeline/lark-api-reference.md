# Lark Bitable API Reference for 3TSolution Timeline

## Credentials

Stored in `/config/workspace/motherbase/.env`:
- `LARK_APP_ID` — Lark app ID
- `LARK_APP_SECRET` — Lark app secret

## Bitable IDs

| Resource | ID |
|----------|-----|
| App Token (Bitable) | `YFc2bPi9gatm93sHETWjQLFGpkh` |
| Table: Project | `tblYg0HGWbDTJgQ9` |
| Table: Công Việc (Tasks) | `tblROtRx9eAoVal4` |

## API Base

`https://open.larksuite.com/open-apis`

## Authentication

```
POST /auth/v3/tenant_access_token/internal
Body: { "app_id": "<LARK_APP_ID>", "app_secret": "<LARK_APP_SECRET>" }
Response: { "tenant_access_token": "..." }
```

All subsequent requests use header: `Authorization: Bearer <token>`

## Table: Project (`tblYg0HGWbDTJgQ9`)

| Field | Type | Notes |
|-------|------|-------|
| Text | Text (primary) | Project name |
| Công Việc | DuplexLink (back-link) | Auto-populated from task's Project field |

## Table: Công Việc (`tblROtRx9eAoVal4`)

| Field Name | Field ID | Type | UI Type | Notes |
|-----------|----------|------|---------|-------|
| Tên Công Việc | fldy1oDDLc | 1 | Text | Primary field — task name |
| Parent items | fldHXsHw89 | 18 | SingleLink | Self-referencing link to parent task |
| Hoàn Thành? | fldeBtBZ9R | 7 | Checkbox | Task completion status |
| Deadline | fldQwLLNUQ | 5 | DateTime | Format: dd/MM/yyyy. Value: Unix ms |
| Project | fldadjzr8z | 21 | DuplexLink | Links to Project table |

## Field Value Formats

### Text (type 1)
```json
"Tên Công Việc": "Task name here"
```

### DateTime (type 5)
Unix timestamp in milliseconds:
```json
"Deadline": 1742083200000
```
Helper: `new Date("2026-03-16").getTime()`

### Checkbox (type 7)
```json
"Hoàn Thành?": true
```

### SingleLink — Parent items (type 18)
Array of record ID strings:
```json
"Parent items": ["recXXXXXXXX"]
```

### DuplexLink — Project (type 21)
Array of record ID strings:
```json
"Project": ["recXXXXXXXX"]
```

**IMPORTANT:** DuplexLink takes `["recId"]` (string array), NOT `[{ id: "recId" }]` (object array).

## API Operations

### List Records
```
GET /bitable/v1/apps/{app_token}/tables/{table_id}/records?page_size=200&page_token=...
```
Paginate using `page_token` from response if `has_more` is true.

### Create Single Record
```
POST /bitable/v1/apps/{app_token}/tables/{table_id}/records
Body: { "fields": { "Tên Công Việc": "...", "Deadline": 1742083200000, ... } }
```

### Batch Create (max 500)
```
POST /bitable/v1/apps/{app_token}/tables/{table_id}/records/batch_create
Body: { "records": [{ "fields": { ... } }, ...] }
```

### Update Single Record
```
PUT /bitable/v1/apps/{app_token}/tables/{table_id}/records/{record_id}
Body: { "fields": { "Deadline": 1742083200000 } }
```

### Batch Update (max 500)
```
POST /bitable/v1/apps/{app_token}/tables/{table_id}/records/batch_update
Body: { "records": [{ "record_id": "recXXX", "fields": { ... } }, ...] }
```

### Delete Single Record
```
DELETE /bitable/v1/apps/{app_token}/tables/{table_id}/records/{record_id}
```

### Batch Delete (max 500)
```
POST /bitable/v1/apps/{app_token}/tables/{table_id}/records/batch_delete
Body: { "records": ["recXXX", "recYYY"] }
```

### List Fields
```
GET /bitable/v1/apps/{app_token}/tables/{table_id}/fields
```

## Node.js API Helper Pattern

All scripts should be written to `.tmp/` and use Node.js built-in `https` module (no external deps).

```javascript
const https = require("https");

function api(token, method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const options = {
      hostname: "open.larksuite.com",
      path,
      method,
      headers: {
        "Authorization": "Bearer " + token,
        "Content-Type": "application/json"
      }
    };
    if (data) options.headers["Content-Length"] = Buffer.byteLength(data);
    const req = https.request(options, res => {
      let b = "";
      res.on("data", c => b += c);
      res.on("end", () => {
        try { resolve(JSON.parse(b)); }
        catch (e) { reject(new Error(b)); }
      });
    });
    req.on("error", reject);
    if (data) req.write(data);
    req.end();
  });
}

async function getToken(appId, appSecret) {
  const res = await api("", "POST",
    "/open-apis/auth/v3/tenant_access_token/internal",
    { app_id: appId, app_secret: appSecret });
  return res.tenant_access_token;
}
```

## Common Pitfalls

1. **DuplexLink format**: Must be `["recId"]`, not `[{ id: "recId" }]`
2. **Batch limit**: Max 500 records per batch call
3. **Pagination**: Always check `has_more` and use `page_token`
4. **Date timezone**: `new Date("2026-03-16")` creates UTC midnight. For Vietnam dates, this is usually fine since Lark displays in user's timezone.
5. **Escaping in Node.js -e**: Avoid `!` in single-quoted strings passed to `node -e`. Write scripts to `.tmp/` files instead.
6. **Field names are Vietnamese**: Use exact field names including diacritics (e.g., `Tên Công Việc`, `Hoàn Thành?`)
