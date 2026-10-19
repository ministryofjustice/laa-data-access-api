# Document Storage (SDS)

The API integrates with SDS (Secure Document Storage) for document operations. The legacy filename-keyed Application upload, update, and delete endpoints have been removed. Authentication with SDS is handled automatically by the service via OAuth2 client credentials.

## Configuration

SDS connectivity is controlled by the following environment variables:

| Variable | Description |
|---|---|
| `SDS_API_URL` | Base URL of the SDS API |
| `SDS_API_BUCKET` | Bucket name used when storing files |
| `SDS_API_CLIENT_REGISTRATION_ID` | OAuth2 client registration ID for SDS |
| `SDS_API_PRINCIPAL_NAME` | OAuth2 principal name for SDS |

## Download a document

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
