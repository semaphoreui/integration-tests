package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.fixtures.semaphore.SemaphoreHostConfigFixtures.Mapping;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures.Inventory;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures.Repository;
import io.bookwright.util.TestData;

/**
 * Credential mappings against the two SSH fixture servers. Repositories and inventories carry no
 * key of their own: whatever reaches a server has to come from a mapping.
 *
 * <p>Inventory hosts disable host-key checking through {@code ansible_ssh_extra_args} rather than
 * {@code ansible_ssh_common_args}: an inventory-level common-args value would replace the {@code
 * --ssh-common-args} option through which the generated ssh configuration reaches Ansible.
 */
public record SemaphoreHostConfigSshFixtures(
    SemaphoreSshFixtures ssh,
    ProjectRequest project,
    SemaphoreFixtures.AccessKey noneKey,
    Repository primaryRepository,
    Repository rotatedRepository,
    Repository rewrittenRepository,
    Inventory primaryTarget,
    Inventory rotatedTarget,
    Inventory localhost,
    Mapping primaryHostMapping,
    Mapping rotatedHostMapping,
    Mapping repositoryUrlMapping) {

  public static SemaphoreHostConfigSshFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    String hostKeyOptions =
        "ansible_ssh_extra_args='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null'";
    return new SemaphoreHostConfigSshFixtures(
        SemaphoreSshFixtures.from(data),
        new ProjectRequest("bookwright-host-config-ssh-" + suffix, false, 0),
        new SemaphoreFixtures.AccessKey("bookwright-host-config-none-" + suffix, "none"),
        new Repository(
            "bookwright-mapped-ssh-repository-" + suffix,
            "ssh://fixture@ssh-fixture:22/repositories/ansible",
            "main"),
        new Repository(
            "bookwright-mapped-rotated-repository-" + suffix,
            "ssh://fixture@ssh-fixture-rotated:22/repositories/ansible",
            "main"),
        new Repository(
            "bookwright-rewritten-repository-" + suffix,
            "https://ssh-fixture/repositories/ansible",
            "main"),
        new Inventory(
            "bookwright-mapped-target-" + suffix,
            "[ssh_target]\n"
                + "ssh-fixture ansible_connection=ssh ansible_user=fixture ansible_port=22 "
                + hostKeyOptions,
            "static"),
        new Inventory(
            "bookwright-mapped-rotated-target-" + suffix,
            "[ssh_target]\n"
                + "ssh-fixture-rotated ansible_connection=ssh ansible_user=fixture ansible_port=22 "
                + hostKeyOptions,
            "static"),
        new Inventory(
            "bookwright-mapped-localhost-" + suffix,
            "[local]\nlocalhost ansible_connection=local",
            "static"),
        new Mapping(SemaphoreHostConfigFixtures.HOST_TYPE, "ssh-fixture"),
        new Mapping(SemaphoreHostConfigFixtures.HOST_TYPE, "ssh-fixture-rotated"),
        new Mapping(SemaphoreHostConfigFixtures.URL_TYPE, "https://ssh-fixture/repositories/"));
  }
}
