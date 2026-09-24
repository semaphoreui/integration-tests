package io.bookwright.steps.semaphore.accesskeys;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.AccessKey;
import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.AccessKeyUpdateRequest;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.api.semaphore.accesskeys.SemaphoreAccessKeysApi;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.SecretAccessKey;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures.SshAccessKey;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.io.IOException;
import java.util.List;
import java.util.stream.StreamSupport;
import retrofit2.Call;
import retrofit2.Response;

public class AccessKeySteps {

  private static final ObjectMapper JSON = new ObjectMapper();

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

  @Step("Rotate SSH access key {keyId} and verify API responses mask its new secrets")
  public void rotateAndVerifyMasked(long projectId, long keyId, SshAccessKey fixture) {
    Calls.expectStatus(
        api.updateAccessKey(projectId, keyId, fixture.rotationRequest(projectId, keyId)), 204);
    verifyMasked(projectId, keyId, fixture);
  }

  @Step("Get access key {keyId} in Semaphore project {projectId}")
  public AccessKey get(long projectId, long keyId) {
    return Calls.body(api.getAccessKey(projectId, keyId), 200, "access key");
  }

  @Step("Get ids of the credential mappings referring to access key {keyId}")
  public List<Long> getHostConfigReferrerIds(long projectId, long keyId) {
    JsonNode refs = Calls.body(api.getAccessKeyRefs(projectId, keyId), 200, "access key refs");
    JsonNode hostConfigs = refs.path("host_configs");
    if (!hostConfigs.isArray()) {
      throw new IllegalStateException(
          "Access key refs have no 'host_configs' array. Body: " + refs);
    }
    return StreamSupport.stream(hostConfigs.spliterator(), false)
        .map(referrer -> referrer.path("id").asLong())
        .toList();
  }

  @Step("Verify access key {keyId} can not be deleted while a mapping uses it")
  public void verifyCannotDelete(long projectId, long keyId, String expectedError) {
    verifyValidationError(api.deleteAccessKey(projectId, keyId), expectedError);
    Calls.expectStatus(api.getAccessKey(projectId, keyId), 200);
  }

  @Step("Verify access key {keyId} can not change to a type its mappings can not use")
  public void verifyCannotRetype(
      long projectId, long keyId, AccessKeyUpdateRequest request, String expectedError) {
    AccessKey before = get(projectId, keyId);
    verifyValidationError(api.updateAccessKey(projectId, keyId, request), expectedError);
    AccessKey after = get(projectId, keyId);
    if (!before.type().equals(after.type())) {
      throw new IllegalStateException(
          "Access key %d changed type from %s to %s although the update was rejected"
              .formatted(keyId, before.type(), after.type()));
    }
  }

  private void verifyValidationError(Call<?> call, String expectedError) {
    Response<?> response = Calls.response(call);
    Calls.expectStatus(response, 400);
    try (var body = response.errorBody()) {
      String diagnostic = body == null ? "" : body.string();
      if (!validationMessage(diagnostic).contains(expectedError)) {
        throw new IllegalStateException(
            "Access key validation response did not contain '%s'. Body: %s"
                .formatted(expectedError, diagnostic));
      }
    } catch (IOException error) {
      throw new IllegalStateException(
          "Could not read Semaphore access key validation response", error);
    }
  }

  private String validationMessage(String responseBody) {
    try {
      var document = JSON.readTree(responseBody);
      return document.hasNonNull("error") ? document.get("error").asText() : responseBody;
    } catch (JsonProcessingException ignored) {
      return responseBody;
    }
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
