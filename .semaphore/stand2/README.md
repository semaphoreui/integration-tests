# stand2: R2 bucket behind a Worker with email login

Static files live in a **private Cloudflare R2 bucket**. A Worker on
`https://<hostname>` serves them, but only after the visitor logs in through
**Cloudflare Access** with an email one-time PIN. Only allow-listed emails /
email domains can log in. The Worker independently verifies the Access JWT
on every request, so the bucket is never reachable anonymously.

Cost profile: R2 has zero egress fees; Access is free for up to 50 users;
Workers free tier covers 100k requests/day. There is no path to a large
surprise bill from someone downloading files in a loop.

```
browser -> Cloudflare Access (email OTP) -> Worker (verify JWT) -> R2
```

## Prerequisites

- A domain on Cloudflare (the `hostname` must be in that zone).
- Zero Trust enabled on the account (free plan is fine); note the team domain
  `https://<team>.cloudflareaccess.com`.
- API token with: Account → *Workers R2 Storage: Edit*, *Workers Scripts: Edit*,
  *Access: Apps and Policies: Edit*; Zone → *Workers Routes: Edit*, *DNS: Edit*.

## Usage

```bash
cd .semaphore/stand2
export CLOUDFLARE_API_TOKEN=...
cp terraform.tfvars.example terraform.tfvars   # fill in IDs, hostname, emails
terraform init
terraform plan
terraform apply

# upload site content (needs R2 credentials or wrangler login)
npx wrangler r2 object put "$(terraform output -raw bucket_name)/index.html" --file ./site/index.html
# or bulk: rclone / aws s3 sync against the R2 S3 endpoint

terraform output site_url
```

## How auth works

1. Visitor opens the site → Cloudflare Access intercepts (app is bound to the
   hostname) → asks for an email → sends a one-time PIN → sets the
   `CF_Authorization` cookie.
2. Every request to the Worker carries the Access JWT (`Cf-Access-Jwt-Assertion`
   header / cookie). The Worker fetches the team's JWKS, verifies the RS256
   signature, `exp`, `iss` and `aud`, then reads the object from R2.
3. Anything without a valid token gets 401/403 even if someone bypasses the
   Access edge somehow (e.g. hits the `workers.dev` URL).

## Inputs

| Name                    | Default        | Description                                     |
|-------------------------|----------------|-------------------------------------------------|
| `cloudflare_account_id` | (required)     | Account ID                                      |
| `cloudflare_zone_id`    | (required)     | Zone ID of the domain                           |
| `hostname`              | (required)     | Site hostname, e.g. `stand2.example.com`        |
| `access_team_domain`    | (required)     | `<team>.cloudflareaccess.com`                   |
| `bucket_name`           | (required)     | R2 bucket name                                  |
| `bucket_location`       | `""`           | R2 location hint (`WEUR`, `ENAM`, ...)          |
| `worker_name`           | `stand2-site`  | Worker script name                              |
| `allowed_emails`        | `[]`           | Emails allowed to log in                        |
| `allowed_email_domains` | `[]`           | Email domains allowed to log in                 |
| `session_duration`      | `24h`          | Access session lifetime                         |
| `index_document`        | `index.html`   | Served for `/` and directory paths              |

At least one of `allowed_emails` / `allowed_email_domains` is required.

## Outputs

`site_url`, `bucket_name`, `access_application_aud`
