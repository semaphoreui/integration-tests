# S3 bucket (Terraform)

Creates a private, encrypted S3 bucket with public access blocked and
versioning enabled by default.

## Usage

```bash
cd .semaphore/terraform/s3-bucket
cp terraform.tfvars.example terraform.tfvars   # edit bucket_name
terraform init
terraform plan
terraform apply
```

AWS credentials are read from the usual sources (`AWS_PROFILE`,
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, etc.).

## Inputs

| Name                | Default       | Description                              |
|---------------------|---------------|------------------------------------------|
| `bucket_name`       | (required)    | Globally unique bucket name              |
| `aws_region`        | `us-east-1`   | Region for the bucket                    |
| `enable_versioning` | `true`        | Enable object versioning                 |
| `force_destroy`     | `false`       | Empty bucket on `terraform destroy`      |
| `tags`              | see variables | Tags applied to all resources            |

## Outputs

`bucket_id`, `bucket_arn`, `bucket_regional_domain_name`
