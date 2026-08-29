locals {
  hostname = "${var.worker_name}.${var.workers_subdomain}.workers.dev"
}

# ---------------------------------------------------------------------------
# Storage
# ---------------------------------------------------------------------------

resource "cloudflare_r2_bucket" "this" {
  account_id = var.cloudflare_account_id
  name       = var.bucket_name
  location   = var.bucket_location != "" ? var.bucket_location : null
}

# ---------------------------------------------------------------------------
# Zero Trust Access: email one-time-PIN login, allow-listed emails/domains.
# Account-scoped application on the built-in workers.dev hostname.
# ---------------------------------------------------------------------------

# The account's "One-time PIN" identity provider is not managed here: Cloudflare
# allows exactly one per account and it usually already exists. With
# allowed_idps unset the app accepts every IdP configured in Zero Trust
# (Settings -> Authentication); make sure One-time PIN is enabled there.
resource "cloudflare_zero_trust_access_application" "site" {
  account_id           = var.cloudflare_account_id
  name                 = local.hostname
  domain               = local.hostname
  type                 = "self_hosted"
  session_duration     = var.session_duration
  app_launcher_visible = false
}

resource "cloudflare_zero_trust_access_policy" "allow" {
  account_id     = var.cloudflare_account_id
  application_id = cloudflare_zero_trust_access_application.site.id
  name           = "Allowed emails"
  precedence     = 1
  decision       = "allow"

  include {
    email        = length(var.allowed_emails) > 0 ? var.allowed_emails : null
    email_domain = length(var.allowed_email_domains) > 0 ? var.allowed_email_domains : null
  }
}

# ---------------------------------------------------------------------------
# Worker: verifies the Access JWT and serves objects from R2.
# Served on the built-in workers.dev hostname (no custom domain needed).
# ---------------------------------------------------------------------------

resource "cloudflare_workers_script" "site" {
  account_id         = var.cloudflare_account_id
  name               = var.worker_name
  content            = file("${path.module}/worker/index.js")
  module             = true
  compatibility_date = "2024-09-01"

  r2_bucket_binding {
    name        = "BUCKET"
    bucket_name = cloudflare_r2_bucket.this.name
  }

  plain_text_binding {
    name = "ACCESS_TEAM_DOMAIN"
    text = var.access_team_domain
  }

  plain_text_binding {
    name = "ACCESS_AUD"
    text = cloudflare_zero_trust_access_application.site.aud
  }

  plain_text_binding {
    name = "INDEX_DOCUMENT"
    text = var.index_document
  }
}
