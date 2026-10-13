package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyUpdateRequest;
import io.bookwright.api.model.semaphore.HostConfigRequest;
import io.bookwright.api.model.semaphore.LoginPasswordRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.SecretAccessKey;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures.SshAccessKey;
import io.bookwright.util.TestData;
import java.util.List;

/**
 * Typed fixtures for the credential-mapping API contract. The SSH keys carry synthetic material:
 * the API stores a key without parsing it, and nothing here reaches a real host.
 */
public record SemaphoreHostConfigFixtures(
    ProjectRequest project,
    ProjectRequest otherProject,
    SshAccessKey sshKey,
    SshAccessKey secondSshKey,
    SecretAccessKey loginPasswordKey,
    SemaphoreFixtures.AccessKey noneKey,
    Mapping hostMapping,
    Mapping paddedHostMapping,
    Mapping paddedDuplicateHostMapping,
    Mapping updatedHostMapping,
    Mapping urlMapping,
    Mapping httpsLoginMapping,
    Mapping httpLoginMapping,
    Mapping foreignKeyMapping,
    List<InvalidMapping> malformedMappings,
    Errors errors,
    Backup backup) {

  public static final String HOST_TYPE = "host";
  public static final String URL_TYPE = "url";

  public static SemaphoreHostConfigFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    String gitHost = "bookwright-" + suffix + ".git.test";
    return new SemaphoreHostConfigFixtures(
        new ProjectRequest("bookwright-host-config-" + suffix, false, 0),
        new ProjectRequest("bookwright-host-config-other-" + suffix, false, 0),
        new SshAccessKey(
            "bookwright-mapping-ssh-key-" + suffix,
            "ssh",
            "git",
            "Bookwright-mapping-passphrase-" + suffix + "-42!",
            "bookwright-mapping-private-key-" + suffix),
        new SshAccessKey(
            "bookwright-mapping-second-ssh-key-" + suffix,
            "ssh",
            "deploy",
            "Bookwright-second-passphrase-" + suffix + "-42!",
            "bookwright-second-private-key-" + suffix),
        new SecretAccessKey(
            "bookwright-mapping-login-key-" + suffix,
            "login_password",
            "bookwright-mapping-user",
            "Bw-mapping-secret-" + suffix + "-42!"),
        new SemaphoreFixtures.AccessKey("bookwright-mapping-none-key-" + suffix, "none"),
        new Mapping(HOST_TYPE, gitHost),
        new Mapping(HOST_TYPE, "  padded-" + gitHost + "  "),
        new Mapping(HOST_TYPE, "  " + gitHost + "  "),
        new Mapping(HOST_TYPE, "updated-" + gitHost),
        new Mapping(URL_TYPE, "https://" + gitHost + "/acme/"),
        new Mapping(URL_TYPE, "https://" + gitHost + "/acme/private/"),
        new Mapping(URL_TYPE, "http://" + gitHost + "/acme/"),
        new Mapping(HOST_TYPE, "foreign-" + gitHost),
        List.of(
            new InvalidMapping(
                new Mapping(HOST_TYPE, gitHost + "\n  IdentityFile /etc/shadow"),
                "host must be a host name"),
            new InvalidMapping(new Mapping(HOST_TYPE, gitHost + ";id"), "host must be a host name"),
            new InvalidMapping(new Mapping(HOST_TYPE, "   "), "host or URL can not be empty"),
            new InvalidMapping(
                new Mapping(URL_TYPE, "ftp://" + gitHost + "/acme/"),
                "URL must start with http:// or https://"),
            new InvalidMapping(new Mapping(URL_TYPE, gitHost), "URL must be a git URL"),
            new InvalidMapping(
                new Mapping(URL_TYPE, "https://deploy:token@" + gitHost + "/acme/"),
                "URL must not contain credentials"),
            new InvalidMapping(
                new Mapping(URL_TYPE, "https://" + gitHost + "/acme/?ref=main"),
                "URL contains invalid characters"),
            new InvalidMapping(new Mapping("proxy", gitHost), "unsupported mapping type")),
        new Errors(
            "already exists",
            "a host mapping needs an SSH key",
            "a URL mapping needs an SSH key or a login/password credential",
            "a login/password credential can only be used with an https URL",
            "the credential is used by the mapping for"),
        new Backup(
            "bookwright-mappings-restored-" + suffix,
            "bookwright-mappings-missing-key-" + suffix,
            "bookwright-mappings-duplicate-" + suffix,
            "bookwright-mappings-malformed-" + suffix,
            "bookwright-missing-mapping-key-" + suffix,
            gitHost + "\n  ProxyCommand /bin/sh"));
  }

  /** The update which turns an SSH key into a login/password credential in place. */
  public AccessKeyUpdateRequest retypeToLoginPassword(long projectId, long keyId) {
    return new AccessKeyUpdateRequest(
        keyId,
        sshKey.name(),
        loginPasswordKey.type(),
        projectId,
        true,
        new LoginPasswordRequest(loginPasswordKey.login(), loginPasswordKey.password()),
        null);
  }

  public record Mapping(String type, String host) {

    public HostConfigRequest request(long projectId, long keyId) {
      return new HostConfigRequest(projectId, type, host, keyId);
    }

    public HostConfigRequest updateRequest(long hostConfigId, long projectId, long keyId) {
      return request(projectId, keyId).withId(hostConfigId);
    }

    /** What the API stores and returns: surrounding whitespace is not part of a host. */
    public String storedHost() {
      return host.strip();
    }
  }

  public record InvalidMapping(Mapping mapping, String expectedError) {}

  public record Errors(
      String duplicate,
      String hostNeedsSshKey,
      String urlNeedsCredential,
      String loginPasswordNeedsHttps,
      String credentialInUse) {}

  public record Backup(
      String restoredProjectName,
      String missingKeyProjectName,
      String duplicateProjectName,
      String malformedProjectName,
      String missingKeyName,
      String malformedHost) {}
}
