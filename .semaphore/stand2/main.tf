# ---------------------------------------------------------------------------
# Storage
# ---------------------------------------------------------------------------

resource "cloudflare_r2_bucket" "this" {
  account_id = var.cloudflare_account_id
  name       = var.bucket_name
  location   = var.bucket_location != "" ? var.bucket_location : null
}

# ---------------------------------------------------------------------------
# Zero Trust Access: email one-time-PIN login, allow-listed emails/domains
# ---------------------------------------------------------------------------

resource "cloudflare_zero_trust_access_identity_provider" "otp" {
  account_id = var.cloudflare_account_id
  name       = "Email one-time PIN"
  type       = "onetimepin"
}

resource "cloudflare_zero_trust_access_application" "site" {
  zone_id                   = var.cloudflare_zone_id
  name                      = var.hostname
  domain                    = var.hostname
  type                      = "self_hosted"
  session_duration          = var.session_duration
  allowed_idps              = [cloudflare_zero_trust_access_identity_provider.otp.id]
  auto_redirect_to_identity = true
  app_launcher_visible      = false
}

resource "cloudflare_zero_trust_access_policy" "allow" {
  zone_id        = var.cloudflare_zone_id
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
# Worker: verifies the Access JWT and serves objects from R2
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

resource "cloudflare_workers_domain" "site" {
  account_id = var.cloudflare_account_id
  zone_id    = var.cloudflare_zone_id
  hostname   = var.hostname
  service    = cloudflare_workers_script.site.name
}
