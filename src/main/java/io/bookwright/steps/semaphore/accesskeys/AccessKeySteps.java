package io.bookwright.steps.semaphore.accesskeys;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.AccessKey;
import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.api.semaphore.accesskeys.SemaphoreAccessKeysApi;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.SecretAccessKey;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures.SshAccessKey;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.List;

public class AccessKeySteps {

  private final SemaphoreAccessKeysApi api;
  private final TeardownStorage teardown;

  @Inject
  public AccessKeySteps(SemaphoreAccessKeysApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create no-auth access key in Semaphore project {projectId}")
  public AccessKey create(long projectId, AccessKeyRequest request) {
    AccessKey key = Calls.body(api.createAccessKey(projectId, request), 201, "created access key");
    teardown.push(
        "Delete Semaphore access key " + key.id(),
        () -> Calls.expectStatus(api.deleteAccessKey(projectId, key.id()), 204));
    return key;
  }

  @Step("Find required access key {name} in Semaphore project {projectId}")
  public AccessKey requireByName(long projectId, String name) {
    List<AccessKey> keys = Calls.body(api.getAccessKeys(projectId), 200, "access keys");
    return keys.stream()
        .filter(key -> name.equals(key.name()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Required access key '%s' was not found in project %d. Available keys: %s"
                        .formatted(name, projectId, keys.stream().map(AccessKey::name).toList())));
  }

  @Step("Create access key as isolated user in Semaphore project {projectId}")
  public AccessKey create(SemaphoreSessionApis session, long projectId, AccessKeyRequest request) {
    AccessKey key =
        Calls.body(
            session.accessKeys().createAccessKey(projectId, request), 201, "created access key");
    teardown.push(
        "Delete Semaphore access key " + key.id(),
        () -> Calls.expectStatus(api.deleteAccessKey(projectId, key.id()), 204));
    return key;
  }

  @Step("Verify isolated user cannot create access keys in Semaphore project {projectId}")
  public void verifyCannotCreate(
      SemaphoreSessionApis session, long projectId, AccessKeyRequest request) {
    Calls.expectStatus(session.accessKeys().createAccessKey(projectId, request), 403);
  }

  @Step("Create a login/password key and verify API responses mask its password")
  public AccessKey createAndVerifyMasked(long projectId, SecretAccessKey fixture) {
    var created =
        Calls.body(
            api.createAccessKeyDocument(projectId, fixture.request(projectId)),
            201,
            "created access key document");
    SecretAssertions.absent("access-key create response", created.toString(), fixture);

    long keyId = requiredLong(created, "id");
    teardown.push(
        "Delete Semaphore access key " + keyId,
        () -> Calls.expectStatus(api.deleteAccessKey(projectId, keyId), 204));

    verifyMasked(projectId, keyId, fixture);

    return new AccessKey(
        keyId,
        requiredText(created, "name"),
        requiredText(created, "type"),
        created.path("project_id").asLong());
  }

  @Step("Create an SSH key and verify API responses mask its secrets")
  public AccessKey createAndVerifyMasked(long projectId, SshAccessKey fixture) {
    var created =
        Calls.body(
            api.createAccessKeyDocument(projectId, fixture.request(projectId)),
            201,
            "created SSH access key document");
    SecretAssertions.absent("SSH access-key create response", created.toString(), fixture);

    long keyId = requiredLong(created, "id");
    teardown.push(
        "Delete Semaphore access key " + keyId,
        () -> Calls.expectStatus(api.deleteAccessKey(projectId, keyId), 204));

    verifyMasked(projectId, keyId, fixture);

    return new AccessKey(
        keyId,
        requiredText(created, "name"),
        requiredText(created, "type"),
        created.path("project_id").asLong());
  }

  @Step("Verify persisted access key {keyId} remains masked")
  public void verifyMasked(long projectId, long keyId, SecretAccessKey fixture) {
    var saved =
        Calls.body(api.getAccessKeyDocument(projectId, keyId), 200, "saved access key document");
    var listed = Calls.body(api.getAccessKeysDocument(projectId), 200, "access key collection");
    SecretAssertions.absent("access-key GET response", saved.toString(), fixture);
    SecretAssertions.absent("access-key collection response", listed.toString(), fixture);
  }

  @Step("Verify persisted SSH access key {keyId} remains masked")
  public void verifyMasked(long projectId, long keyId, SshAccessKey fixture) {
    var saved =
        Calls.body(
            api.getAccessKeyDocument(projectId, keyId), 200, "saved SSH access key document");
    var listed = Calls.body(api.getAccessKeysDocument(projectId), 200, "access key collection");
    SecretAssertions.absent("SSH access-key GET response", saved.toString(), fixture);
    SecretAssertions.absent("SSH access-key collection response", listed.toString(), fixture);
  }

  private long requiredLong(com.fasterxml.jackson.databind.JsonNode document, String field) {
    if (!document.has(field) || !document.get(field).canConvertToLong()) {
      throw new IllegalStateException("Access-key response has no numeric '" + field + "'");
    }
    return document.get(field).asLong();
  }

  private String requiredText(com.fasterxml.jackson.databind.JsonNode document, String field) {
    if (!document.hasNonNull(field) || !document.get(field).isTextual()) {
      throw new IllegalStateException("Access-key response has no text '" + field + "'");
    }
    return document.get(field).asText();
  }
}
