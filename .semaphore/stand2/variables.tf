variable "cloudflare_account_id" {
  description = "Cloudflare account ID that owns the R2 bucket and Worker."
  type        = string
}

variable "cloudflare_zone_id" {
  description = "Zone ID of the domain the site is served from."
  type        = string
}

variable "hostname" {
  description = "Full hostname for the site, e.g. stand2.example.com (must belong to the zone)."
  type        = string
}

variable "access_team_domain" {
  description = "Cloudflare Zero Trust team domain, e.g. myteam.cloudflareaccess.com."
  type        = string
}

variable "bucket_name" {
  description = "Name of the R2 bucket."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$", var.bucket_name))
    error_message = "bucket_name must be 3-63 chars, lowercase letters, digits or hyphens."
  }
}

variable "bucket_location" {
  description = "R2 location hint (WNAM, ENAM, WEUR, EEUR, APAC). Empty = automatic."
  type        = string
  default     = ""
}

variable "worker_name" {
  description = "Name of the Worker script."
  type        = string
  default     = "stand2-site"
}

variable "allowed_emails" {
  description = "Individual email addresses allowed to log in."
  type        = list(string)
  default     = []
}

variable "allowed_email_domains" {
  description = "Email domains allowed to log in, e.g. [\"semaphoreui.com\"]."
  type        = list(string)
  default     = []

  validation {
    condition     = length(var.allowed_emails) + length(var.allowed_email_domains) > 0
    error_message = "Provide at least one of allowed_emails or allowed_email_domains."
  }
}

variable "session_duration" {
  description = "How long an Access login stays valid."
  type        = string
  default     = "24h"
}

variable "index_document" {
  description = "Object served for the root and directory requests."
  type        = string
  default     = "index.html"
}
