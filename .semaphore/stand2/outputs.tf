output "site_url" {
  description = "URL of the protected site."
  value       = "https://${local.hostname}"
}

output "bucket_name" {
  description = "Name of the R2 bucket."
  value       = cloudflare_r2_bucket.this.name
}

output "access_application_aud" {
  description = "Access application audience tag the Worker validates against."
  value       = cloudflare_zero_trust_access_application.site.aud
}
