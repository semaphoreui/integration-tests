# Authenticates via CLOUDFLARE_API_TOKEN environment variable.
# Token needs: Account -> Workers R2 Storage:Edit, Workers Scripts:Edit,
# Access: Apps and Policies:Edit; Zone -> Workers Routes:Edit.
provider "cloudflare" {}
