# Credential-mapping Git fixture

Sources of the repositories served by the `feature-host-config` profile. On `profile up` the
`host-config-git-init` container commits each folder into its own bare repository; nothing here is
fetched from outside the Compose network.

| Folder | Published as | Protocol and access |
| --- | --- | --- |
| `submodules/` | `public/submodules.git` | `http://host-config-git/`, anonymous |
| `http-submodule/` | `public/http-submodule.git` | `http://host-config-git/`, anonymous |
| `ansible/` | `public/ansible.git` | `http://host-config-git/`, anonymous |
| `galaxy/` | `public/galaxy.git` | `http://host-config-git/`, anonymous |
| `ssh-submodule/` | `ssh/ssh-submodule.git` | `ssh://fixture@host-config-git-ssh/srv/git/`, key `id_ed25519` |
| `private-submodule/` | `private/private-submodule.git` | `https://host-config-git/`, Basic Auth |
| `private-content/` | `private/content.git` | `https://host-config-git/`, Basic Auth |
| `private-role/` | `private/role.git` | `https://host-config-git/`, Basic Auth |

The init container adds `.gitmodules` and the gitlinks to `submodules.git` itself, so the
submodule URLs live in one place, `profiles/feature-host-config/host-config.yml`. Credentials for
the private repositories exist only in Semaphore mappings created by the test (a URL mapping for
HTTPS, a Host mapping for SSH); repositories
and inventories carry a key of type `none`.

Every playbook prints a fixed marker and nothing secret. `ansible/target.yml` runs on the
`host-config-target` container; the others run on the Semaphore controller.
