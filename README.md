# s3-lifecycle-manager

Cost‑aware S3 tiering with **Spring Boot 3** + **LocalStack**. Classify objects (hot/warm/archive), tag them, and get a **projected monthly cost** report by storage class. Docker Compose spins up LocalStack + the app so you can test lifecycle rules locally before touching AWS.

![Java](https://img.shields.io/badge/Java-17-informational) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen) ![AWS](https://img.shields.io/badge/AWS-S3-orange) ![LocalStack](https://img.shields.io/badge/Local-LocalStack-blue) ![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

##  Features
- **Tag by age** → `tier=hot|warm|archive`
- **Lifecycle-by-tag** rules (Standard → Standard‑IA → Glacier IR) simulated in LocalStack
- **Savings report** → compare current vs projected monthly AUD costs
- **Pure REST** with OpenAPI UI (optional), Micrometer/Actuator ready

---

##  Quick start

```bash
# 1) Clone
git clone git@github.com:LorenzoRaveraOleart/s3-lifecycle-manager.git
cd s3-lifecycle-manager

# 2) Run LocalStack + app
docker compose up --build

# 3) Classify (tag) objects
curl -X POST http://localhost:8080/storage/classify \
  -H 'Content-Type: application/json' \
  -d '{"bucket":"demo-bucket","prefix":"uploads/","warmAfterDays":0,"archiveAfterDays":0}'

# 4) See projected savings
curl "http://localhost:8080/storage/reports/s3-savings?bucket=demo-bucket&prefix=uploads/"
```

> `init/bootstrap.sh` pre-creates a `demo-bucket` and a few sample objects, plus applies lifecycle rules inside LocalStack.

---

##  Endpoints

| Method | Path                               | Body / Params                                             | Description |
|-------:|------------------------------------|-----------------------------------------------------------|-------------|
| POST   | `/storage/classify`                | `{ bucket, prefix, warmAfterDays, archiveAfterDays }`     | Lists objects under `prefix`, tags each with `tier=hot|warm|archive` based on age. |
| GET    | `/storage/reports/s3-savings`      | `bucket`, `prefix`                                        | Returns `{ currentMonthlyAud, projectedMonthlyAud, objectCount, bytesAnalyzed }`. |

Example classify body:
```json
{ "bucket":"demo-bucket", "prefix":"uploads/", "warmAfterDays": 30, "archiveAfterDays": 90 }
```

---

## Architecture

```
Client ──HTTP──> Spring Boot API
                  ├── Classifier: list S3 objects, compute age, putObjectTagging(tier=...)
                  └── Reporter: estimate costs per storage class using a simple price map
S3 (LocalStack) <─┘   Lifecycle configuration transitions by object tag (simulated locally)
```

- **Local only:** path‑style S3 URLs + endpoint override for LocalStack (`http://localstack:4566` in Compose).
- **Prod later:** point to real AWS (remove endpoint override), add IAM, and wire Terraform for lifecycle config.

---

## Configuration

`src/main/resources/application.yml`:
```yaml
app:
  s3:
    region: ${AWS_REGION:ap-southeast-2}
    pricePerGb:         # approximate AUD prices (tune for your region)
      STANDARD: 0.033
      STANDARD_IA: 0.0198
      ONEZONE_IA: 0.0152
      GLACIER_IR: 0.0052
      GLACIER: 0.0045
      DEEP_ARCHIVE: 0.0012
```

Environment variables used by Docker Compose:
- `AWS_REGION=ap-southeast-2`
- `AWS_ENDPOINT=http://localstack:4566` (only for local)
- `S3_BUCKET=demo-bucket`, `S3_PREFIX=uploads/` (used by the bootstrap + curl examples)

---

## LocalStack bootstrap

`init/bootstrap.sh` does the following on startup:
1. Creates `demo-bucket` in `ap-southeast-2`
2. Uploads sample files: `uploads/file1.txt`, `file2.txt`, `file3.txt`
3. Puts **lifecycle-by-tag** rules:
   - `tier=warm` → transition to `STANDARD_IA`
   - `tier=archive` → transition to `GLACIER_IR`
4. Applies example tags to files 2 and 3

Check from the container:
```bash
docker exec -it localstack awslocal s3 ls
docker exec -it localstack awslocal s3 ls s3://demo-bucket/uploads/
```

---

## Development

Build just the app:
```bash
cd app
mvn clean package
```

Run the stack:
```bash
docker compose up --build
```

OpenAPI UI (if you keep springdoc):
```
http://localhost:8080/swagger-ui.html
```

---

## Troubleshooting

**`UnknownHostException` when hitting S3**  
Ensure the S3 client uses **path‑style** and endpoint override. This project sets:
```java
S3Configuration.builder().pathStyleAccessEnabled(true).build();
endpointOverride(URI.create("http://localstack:4566"))
```

**`403 SignatureDoesNotMatch`**  
Check that the app is using the same region as LocalStack (`ap-southeast-2`) and credentials (any static values work in LocalStack).

**Docker networking**  
Inside Docker, the hostname is `localstack`; outside, use `localhost:4566`.

---

## License
MIT
