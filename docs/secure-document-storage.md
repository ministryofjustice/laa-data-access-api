# Document Storage (SDS)

The API integrates with SDS (Secure Document Storage) to handle document operations. Four endpoints are available under the applications resource; they are excluded from Swagger UI but accessible via HTTP. Authentication with SDS is handled automatically by the service via OAuth2 client credentials.

## Configuration

SDS connectivity is controlled by the following environment variables:

| Variable | Description |
|---|---|
| `SDS_API_URL` | Base URL of the SDS API |
| `SDS_API_BUCKET` | Bucket name used when storing files |
| `SDS_API_CLIENT_REGISTRATION_ID` | OAuth2 client registration ID for SDS |
| `SDS_API_PRINCIPAL_NAME` | OAuth2 principal name for SDS |

## Endpoints

### Upload a document

The file is stored in a folder named after the application ID, e.g. `{applicationId}/document.pdf`.

```
POST /api/v0/applications/{id}/upload-document
Content-Type: multipart/form-data
X-Service-Name: <service>
```

```bash
curl -X POST "http://localhost:8080/api/v0/applications/123e4567-e89b-12d3-a456-426614174000/upload-document" \
  -H "accept: application/json" \
  -H "Authorization: <token>" \
  -H "X-Service-Name: <service>" \
  -F "file=@/path/to/document.pdf"
```

Response `201 Created`:
```json
{
  "detail": "<bucket>/<applicationId>/document.pdf",
  "success": "File uploaded successfully",
  "checksum": "<checksum>"
}
```

| Status | Meaning |
|---|---|
| `201` | File uploaded successfully |
| `409` | A file with the same name already exists |

---

### Download a document

The `documentId` path parameter is the file key in the format `{applicationId}/{filename}`, i.e. the path the file was stored under when uploaded.

```
GET /api/v0/applications/{id}/download-document/{documentId}
X-Service-Name: <service>
```

```bash
curl -X GET "http://localhost:8080/api/v0/applications/123e4567-e89b-12d3-a456-426614174000/download-document/document.pdf" \
  -H "accept: application/json" \
  -H "Authorization: <token>" \
  -H "X-Service-Name: <service>"
```

Response `200 OK`:
```json
{
  "fileURL": "https://..."
}
```

The `fileURL` is a pre-signed URL. Callers should redirect the user or download from it directly.

| Status | Meaning |
|---|---|
| `200` | Returns a pre-signed download URL |
| `404` | Document not found |

---

### Update a document

Replaces an existing document. The file name is used to match the existing document.

```
PUT /api/v0/applications/{id}/update-document
Content-Type: multipart/form-data
X-Service-Name: <service>
```

```bash
curl -X PUT "http://localhost:8080/api/v0/applications/123e4567-e89b-12d3-a456-426614174000/update-document" \
  -H "accept: application/json" \
  -H "Authorization: <token>" \
  -H "X-Service-Name: <service>" \
  -F "file=@/path/to/document.pdf"
```

Response `200 OK`:
```json
{
  "fileURL": "https://..."
}
```

| Status | Meaning |
|---|---|
| `200` | File updated; returns a pre-signed URL for the updated file |

---

### Delete documents

Accepts one or more document IDs as query parameters. Each ID is the file key in the format `{applicationId}/{filename}`.

```
DELETE /api/v0/applications/{id}/delete-document?documentIds=<id1>&documentIds=<id2>
X-Service-Name: <service>
```

```bash
curl -X DELETE "http://localhost:8080/api/v0/applications/123e4567-e89b-12d3-a456-426614174000/delete-document?documentIds=123e4567-e89b-12d3-a456-426614174000/document.pdf" \
  -H "accept: application/json" \
  -H "Authorization: <token>" \
  -H "X-Service-Name: <service>"
```

| Status | Meaning |
|---|---|
| `204` | All documents deleted |
| `400` | `documentIds` query parameter is empty |

---

## Local setup

To run the service locally with SDS enabled, set the following environment variables (e.g. in your IDE run configuration or a `.env` file):

```bash
# SDS service
SDS_API_URL=<sds-base-url>           # e.g. https://sds.dev.example.justice.gov.uk
SDS_API_BUCKET=<bucket-name>         # bucket name provided by the SDS team

# OAuth2 client credentials used to authenticate with SDS
SDS_API_CLIENT_REGISTRATION_ID=moj-identity   # matches the registration key in application.yml
SDS_API_PRINCIPAL_NAME=<principal>            # OAuth2 principal name provided by the SDS team
```

These values can be retrieved from the UAT key vault.
