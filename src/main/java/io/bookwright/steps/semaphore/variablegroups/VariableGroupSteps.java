package io.bookwright.steps.semaphore.variablegroups;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.VariableGroup;
import io.bookwright.api.semaphore.variablegroups.SemaphoreVariableGroupsApi;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreVariableGroupFixtures;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.io.IOException;

public class VariableGroupSteps {

  private static final ObjectMapper JSON =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final SemaphoreVariableGroupsApi api;
  private final TeardownStorage teardown;

  @Inject
  public VariableGroupSteps(SemaphoreVariableGroupsApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create mixed Semaphore Variable Group in project {projectId}")
  public VariableGroup createAndVerifyMasked(
      long projectId, SemaphoreVariableGroupFixtures fixture) {
    JsonNode created =
        Calls.body(
            api.create(projectId, fixture.createRequest(projectId)), 201, "created Variable Group");
    verifySecretsAbsent("Variable Group create response", created, fixture);
    VariableGroup group = JSON.convertValue(created, VariableGroup.class);
    teardown.push(
        "Delete Semaphore Variable Group " + group.id(),
        () -> Calls.expectStatus(api.delete(projectId, group.id()), 204));
    return getAndVerifyMasked(projectId, group.id(), fixture);
  }

  @Step("Get Semaphore Variable Group {groupId} and verify secrets remain masked")
  public VariableGroup getAndVerifyMasked(
      long projectId, long groupId, SemaphoreVariableGroupFixtures fixture) {
    JsonNode saved = Calls.body(api.get(projectId, groupId), 200, "saved Variable Group");
    JsonNode listed = Calls.body(api.getAll(projectId), 200, "Variable Group collection");
    verifySecretsAbsent("Variable Group GET response", saved, fixture);
    verifySecretsAbsent("Variable Group collection response", listed, fixture);
    return JSON.convertValue(saved, VariableGroup.class);
  }

  @Step("Rename a persisted Variable Group secret and preserve its value")
  public VariableGroup renameSecretAndVerifyMasked(
      long projectId, VariableGroup group, SemaphoreVariableGroupFixtures fixture) {
    Calls.expectStatus(
        api.update(projectId, group.id(), fixture.renameRequest(projectId, group)), 204);
    VariableGroup updated = getAndVerifyMasked(projectId, group.id(), fixture);
    if (updated.secrets().stream()
        .noneMatch(secret -> fixture.renamedVariable().equals(secret.name()))) {
      throw new IllegalStateException("Variable Group secret rename was not persisted");
    }
    return updated;
  }

  @Step("Reject a Variable Group environment variable with an empty name")
  public void emptyEnvironmentNameIsRejected(
      long projectId, SemaphoreVariableGroupFixtures fixture) {
    var response =
        Calls.response(
            api.create(projectId, fixture.invalidEmptyEnvironmentNameRequest(projectId)));
    String errorBody;
    try {
      errorBody = response.errorBody() == null ? "" : response.errorBody().string();
    } catch (IOException error) {
      throw new IllegalStateException("Could not read Variable Group validation response", error);
    }
    if (response.code() != 400 || !errorBody.contains(fixture.expectedValidationError())) {
      throw new IllegalStateException(
          "Empty Variable Group variable name expected HTTP 400 with validation details but received HTTP "
              + response.code());
    }
  }

  private void verifySecretsAbsent(
      String surface, JsonNode document, SemaphoreVariableGroupFixtures fixture) {
    SecretAssertions.absent(surface, document.toString(), fixture.variableSecret().value());
    SecretAssertions.absent(surface, document.toString(), fixture.environmentSecret().value());
  }
}
