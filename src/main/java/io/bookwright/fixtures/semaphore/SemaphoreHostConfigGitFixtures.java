package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.SecretAccessKey;
import io.bookwright.fixtures.semaphore.SemaphoreHostConfigFixtures.Mapping;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures.Inventory;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures.Repository;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures.Template;
import io.bookwright.util.TestData;
import java.util.List;

/**
 * Credential mappings against the Git servers and the Ansible target of the {@code
 * feature-host-config} profile. Repositories and inventories carry a key of type {@code none}: the
 * private HTTPS repositories and the target container are reachable only through a mapping. The SSH
 * key of the target comes from {@link SemaphoreSshFixtures}, which reads the generated key pair, so
 * this record stays constructible without a running profile.
 *
 * <p>The submodules of the parent repository use three transports: HTTPS with Basic Auth from a URL
 * mapping (a login/password mapping is accepted for {@code https://} alone), anonymous plain HTTP,
 * and SSH with the key from a Host mapping on the Git server.
 */
public record SemaphoreHostConfigGitFixtures(
    ProjectRequest project,
    SemaphoreFixtures.AccessKey noneKey,
    SecretAccessKey privateCredentials,
    Mapping privateUrlMapping,
    Mapping targetHostMapping,
    Mapping gitSshHostMapping,
    Repository submodulesOverHttp,
    Repository ansible,
    Repository galaxy,
    Inventory localhost,
    Inventory target,
    Template submodules,
    Template targetPlaybook,
    Template gitModule,
    Template galaxyRole,
    List<String> submoduleMarkers,
    String targetMarker,
    String gitModuleMarker,
    String galaxyRoleMarker,
    String successfulTaskStatus,
    String failedTaskStatus,
    String cloneFailureMarker,
    String httpsSubmoduleRejection,
    String sshSubmoduleRejection) {

  public static SemaphoreHostConfigGitFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreHostConfigGitFixtures(
        new ProjectRequest("bookwright-host-config-git-" + suffix, false, 0),
        new SemaphoreFixtures.AccessKey("bookwright-host-config-git-none-" + suffix, "none"),
        new SecretAccessKey(
            "bookwright-host-config-git-login-" + suffix,
            "login_password",
            "bookwright-mapped-git",
            "Bookwright-host-config-token-42!"),
        new Mapping(SemaphoreHostConfigFixtures.URL_TYPE, "https://host-config-git/private/"),
        new Mapping(SemaphoreHostConfigFixtures.HOST_TYPE, "host-config-target"),
        new Mapping(SemaphoreHostConfigFixtures.HOST_TYPE, "host-config-git-ssh"),
        new Repository(
            "bookwright-submodules-http-" + suffix,
            "http://host-config-git/public/submodules.git",
            "main"),
        new Repository(
            "bookwright-host-config-ansible-" + suffix,
            "http://host-config-git/public/ansible.git",
            "main"),
        new Repository(
            "bookwright-host-config-galaxy-" + suffix,
            "http://host-config-git/public/galaxy.git",
            "main"),
        new Inventory(
            "bookwright-host-config-localhost-" + suffix,
            "[local]\nlocalhost ansible_connection=local",
            "static"),
        new Inventory(
            "bookwright-host-config-target-" + suffix,
            "[target]\n"
                + "host-config-target ansible_connection=ssh ansible_user=fixture ansible_port=22"
                + " ansible_ssh_extra_args='-o StrictHostKeyChecking=no"
                + " -o UserKnownHostsFile=/dev/null'",
            "static"),
        new Template("bookwright-submodules-" + suffix, "submodules.yml", "ansible", ""),
        new Template("bookwright-ansible-target-" + suffix, "target.yml", "ansible", ""),
        new Template("bookwright-ansible-git-module-" + suffix, "git-module.yml", "ansible", ""),
        new Template("bookwright-galaxy-role-" + suffix, "galaxy.yml", "ansible", ""),
        List.of(
            "semaphore-bookwright-https-submodule-ok",
            "semaphore-bookwright-http-submodule-ok",
            "semaphore-bookwright-ssh-submodule-ok"),
        "semaphore-bookwright-ansible-target-ok on host-config-target",
        "semaphore-bookwright-git-module-ok",
        "semaphore-bookwright-galaxy-role-ok",
        "success",
        "error",
        "Failed updating repository",
        "could not read Username for 'https://host-config-git'",
        "fixture@host-config-git-ssh: Permission denied (publickey)");
  }
}
