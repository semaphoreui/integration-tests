variable "aws_region" {
  description = "AWS region where the bucket is created."
  type        = string
  default     = "us-east-1"
}

variable "bucket_name" {
  description = "Globally unique name of the S3 bucket."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.bucket_name))
    error_message = "bucket_name must be 3-63 chars, lowercase letters, digits, dots or hyphens."
  }
}

variable "enable_versioning" {
  description = "Enable object versioning on the bucket."
  type        = bool
  default     = false
}

variable "force_destroy" {
  description = "Delete all objects when the bucket is destroyed (useful for test buckets)."
  type        = bool
  default     = false
}

variable "tags" {
  description = "Tags applied to all resources."
  type        = map(string)
  default = {
    Project   = "semaphore-qa"
    ManagedBy = "terraform"
  }
}
