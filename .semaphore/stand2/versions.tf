terraform {
  required_version = ">= 1.9.0" # cross-variable validation in variables.tf

  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 4.40"
    }
  }
}
