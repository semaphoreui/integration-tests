package io.bookwright.steps.semaphore.variablegroups;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.VariableGroup;
import io.bookwright.api.model.semaphore.VariableGroupRequest;
import io.bookwright.api.semaphore.variablegroups.SemaphoreVariableGroupsApi;
import io.bookwright.assertions.SecretAssertions;
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

  @Step("Create Semaphore Variable Group in project {projectId}")
  public VariableGroup createAndVerifyMasked(long projectId, VariableGroupRequest request) {
    JsonNode created = Calls.body(api.create(projectId, request), 201, "created Variable Group");
    verifySecretsAbsent("Variable Group create response", created, request);
    VariableGroup group = JSON.convertValue(created, VariableGroup.class);
    teardown.push(
        "Delete Semaphore Variable Group " + group.id(),
        () -> Calls.expectStatus(api.delete(projectId, group.id()), 204));
    return getAndVerifyMasked(projectId, group.id(), request);
  }

  private VariableGroup getAndVerifyMasked(
      long projectId, long groupId, VariableGroupRequest sourceRequest) {
    JsonNode saved = Calls.body(api.get(projectId, groupId), 200, "saved Variable Group");
    JsonNode listed = Calls.body(api.getAll(projectId), 200, "Variable Group collection");
    verifySecretsAbsent("Variable Group GET response", saved, sourceRequest);
    verifySecretsAbsent("Variable Group collection response", listed, sourceRequest);
    return JSON.convertValue(saved, VariableGroup.class);
  }

  @Step("Update Semaphore Variable Group {groupId}")
  public VariableGroup updateAndVerifyMasked(
      long projectId,
      long groupId,
      VariableGroupRequest updateRequest,
      VariableGroupRequest sourceRequest) {
    Calls.expectStatus(api.update(projectId, groupId, updateRequest), 204);
    return getAndVerifyMasked(projectId, groupId, sourceRequest);
  }

  @Step("Reject a Variable Group environment variable with an empty name")
  public void emptyEnvironmentNameIsRejected(
      long projectId, VariableGroupRequest request, String expectedValidationError) {
    var response = Calls.response(api.create(projectId, request));
    String errorBody;
    try {
      errorBody = response.errorBody() == null ? "" : response.errorBody().string();
    } catch (IOException error) {
      throw new IllegalStateException("Could not read Variable Group validation response", error);
    }
    if (response.code() != 400 || !errorBody.contains(expectedValidationError)) {
      throw new IllegalStateException(
          "Empty Variable Group variable name expected HTTP 400 with validation details but received HTTP "
              + response.code());
    }
  }

  private void verifySecretsAbsent(
      String surface, JsonNode document, VariableGroupRequest sourceRequest) {
    sourceRequest.secrets().stream()
        .map(secret -> secret.secret())
        .filter(secret -> secret != null && !secret.isBlank())
        .forEach(secret -> SecretAssertions.absent(surface, document.toString(), secret));
  }
}
