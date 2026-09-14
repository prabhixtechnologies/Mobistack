# @prabhix/mobistack-api

TypeScript client types for the MobiStack API, generated with openapi-typescript from
`../../backend/apidocs.json`.

```bash
npm ci
npm run generate
```

Refresh the snapshot from a running API (`SWAGGER_ENABLED=true`):

```powershell
../../backend/scripts/export-openapi.ps1
```

CI fails if `src/schema.ts` drifts from the committed snapshot.
