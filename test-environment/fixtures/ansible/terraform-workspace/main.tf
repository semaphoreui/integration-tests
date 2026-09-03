terraform {
  required_version = ">= 1.5.0"
}

variable "bookwright_secret" {
  type      = string
  sensitive = true
}

variable "bookwright_expected_hash" {
  type = string
}

output "semaphore_bookwright_workspace" {
  value = terraform.workspace
}

output "semaphore_bookwright_tf_var_secret_verified" {
  value = nonsensitive(sha256(var.bookwright_secret) == var.bookwright_expected_hash) ? "semaphore_bookwright_tf_var_secret_verified" : "secret-mismatch"
}
