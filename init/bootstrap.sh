#!/usr/bin/env bash
set -euo pipefail

: "${AWS_DEFAULT_REGION:=ap-southeast-2}"
: "${BUCKET_NAME:=demo-bucket}"
: "${PREFIX:=uploads/}"
: "${LOCALSTACK_HOST:=localstack}"

export AWS_PAGER=""
alias awslocal="aws --endpoint-url=http://${LOCALSTACK_HOST}:4566"

echo ">> Creating S3 bucket: ${BUCKET_NAME} in ${AWS_DEFAULT_REGION}"
awslocal s3api create-bucket --bucket "${BUCKET_NAME}" --create-bucket-configuration LocationConstraint=${AWS_DEFAULT_REGION} >/dev/null 2>&1 || true

echo ">> Putting example objects"
for i in {1..3}; do
  echo "hello ${i}" | awslocal s3 cp - "s3://${BUCKET_NAME}/${PREFIX}file${i}.txt" >/dev/null
done

echo ">> Applying lifecycle-by-tag rules (warm->STANDARD_IA, archive->GLACIER_IR)"
cat >/tmp/lifecycle.json <<'JSON'
{
  "Rules": [
    {
      "ID": "warm-to-ia",
      "Filter": { "Tag": { "Key": "tier", "Value": "warm" } },
      "Status": "Enabled",
      "Transitions": [{ "Days": 0, "StorageClass": "STANDARD_IA" }]
    },
    {
      "ID": "archive-to-glacier-ir",
      "Filter": { "Tag": { "Key": "tier", "Value": "archive" } },
      "Status": "Enabled",
      "Transitions": [{ "Days": 0, "StorageClass": "GLACIER_IR" }]
    }
  ]
}
JSON
awslocal s3api put-bucket-lifecycle-configuration --bucket "${BUCKET_NAME}" --lifecycle-configuration file:///tmp/lifecycle.json

echo ">> Tag a couple of objects so you can test immediately"
awslocal s3api put-object-tagging --bucket "${BUCKET_NAME}" --key "${PREFIX}file2.txt" --tagging 'TagSet=[{Key=tier,Value=warm}]'
awslocal s3api put-object-tagging --bucket "${BUCKET_NAME}" --key "${PREFIX}file3.txt" --tagging 'TagSet=[{Key=tier,Value=archive}]'

echo "LocalStack bootstrap complete"
