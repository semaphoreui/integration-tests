package io.bookwright.steps.semaphore.hostconfigs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.HostConfig;
import io.bookwright.api.model.semaphore.HostConfigRequest;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.api.semaphore.hostconfigs.SemaphoreHostConfigsApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.io.IOException;
import java.util.List;
import retrofit2.Call;
import retrofit2.Response;

public class HostConfigSteps {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final SemaphoreHostConfigsApi api;
  private final TeardownStorage teardown;

  @Inject
  public HostConfigSteps(SemaphoreHostConfigsApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create {request.type} mapping for {request.host} in Semaphore project {projectId}")
  public HostConfig create(long projectId, HostConfigRequest request) {
    HostConfig hostConfig =
        Calls.body(api.createHostConfig(projectId, request), 201, "created host config");
    // A scenario may delete its own mapping to prove that the credential it used becomes
    // deletable again, so the cleanup accepts an already removed mapping.
    teardown.push(
        "Delete Semaphore host config " + hostConfig.id(),
        () ->
            Calls.expectStatus(
                Calls.response(api.deleteHostConfig(projectId, hostConfig.id())), 204, 404));
    return hostConfig;
  }

  @Step("Get credential mappings of Semaphore project {projectId}")
  public List<HostConfig> getHostConfigs(long projectId) {
    return Calls.body(api.getHostConfigs(projectId), 200, "host configs");
  }

  @Step("Get credential mappings of Semaphore project {projectId} as isolated user")
  public List<HostConfig> getHostConfigs(SemaphoreSessionApis session, long projectId) {
    return Calls.body(session.hostConfigs().getHostConfigs(projectId), 200, "host configs");
  }

  @Step("Get credential mapping {hostConfigId} of Semaphore project {projectId}")
  public HostConfig get(long projectId, long hostConfigId) {
    return Calls.body(api.getHostConfig(projectId, hostConfigId), 200, "host config");
  }

  @Step("Find required mapping for {host} in Semaphore project {projectId}")
  public HostConfig requireByHost(long projectId, String host) {
    List<HostConfig> hostConfigs = getHostConfigs(projectId);
    return hostConfigs.stream()
        .filter(hostConfig -> host.equals(hostConfig.host()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Required mapping for '%s' was not found in project %d. Available mappings: %s"
                        .formatted(
                            host, projectId, hostConfigs.stream().map(HostConfig::host).toList())));
  }

  @Step("Update credential mapping {request.id} in Semaphore project {projectId}")
  public HostConfig update(long projectId, HostConfigRequest request) {
    if (request.id() == null) {
      throw new IllegalArgumentException("A mapping update needs the id of the mapping");
    }
    Calls.expectStatus(api.updateHostConfig(projectId, request.id(), request), 204);
    return get(projectId, request.id());
  }

  @Step("Delete credential mapping {hostConfigId} from Semaphore project {projectId}")
  public void delete(long projectId, long hostConfigId) {
    Calls.expectStatus(api.deleteHostConfig(projectId, hostConfigId), 204);
  }

  @Step("Verify credential mapping {hostConfigId} is absent from Semaphore project {projectId}")
  public void verifyAbsent(long projectId, long hostConfigId) {
    Calls.expectStatus(api.getHostConfig(projectId, hostConfigId), 404);
    if (getHostConfigs(projectId).stream().anyMatch(item -> item.id() == hostConfigId)) {
      throw new IllegalStateException(
          "Deleted mapping %d is still listed in project %d".formatted(hostConfigId, projectId));
    }
  }

  @Step("Verify Semaphore rejects mapping for {request.host}")
  public void verifyRejected(long projectId, HostConfigRequest request, String expectedError) {
    verifyValidationError(api.createHostConfig(projectId, request), expectedError);
  }

  @Step("Verify Semaphore rejects update of mapping {request.id} to {request.host}")
  public void verifyUpdateRejected(
      long projectId, HostConfigRequest request, String expectedError) {
    if (request.id() == null) {
      throw new IllegalArgumentException("A mapping update needs the id of the mapping");
    }
    verifyValidationError(api.updateHostConfig(projectId, request.id(), request), expectedError);
  }

  @Step("Verify a credential of another project can not be mapped in project {projectId}")
  public void verifyForeignCredentialRejected(long projectId, HostConfigRequest request) {
    var response = Calls.response(api.createHostConfig(projectId, request));
    try (var ignored = response.errorBody()) {
      // The key is resolved inside the project of the mapping, so a key of another
      // project is simply not found there.
      Calls.expectStatus(response, 400, 404);
    }
    if (getHostConfigs(projectId).stream()
        .anyMatch(hostConfig -> hostConfig.host().equals(request.host()))) {
      throw new IllegalStateException(
          "A mapping bound to a credential of another project was stored in project " + projectId);
    }
  }

  @Step("Verify isolated user cannot create mappings in Semaphore project {projectId}")
  public void verifyCannotCreate(
      SemaphoreSessionApis session, long projectId, HostConfigRequest request) {
    Calls.expectStatus(session.hostConfigs().createHostConfig(projectId, request), 403);
  }

  @Step("Verify isolated user cannot update mapping {request.id} in Semaphore project {projectId}")
  public void verifyCannotUpdate(
      SemaphoreSessionApis session, long projectId, HostConfigRequest request) {
    if (request.id() == null) {
      throw new IllegalArgumentException("A mapping update needs the id of the mapping");
    }
    Calls.expectStatus(
        session.hostConfigs().updateHostConfig(projectId, request.id(), request), 403);
  }

  @Step(
      "Verify isolated user cannot delete mapping {hostConfigId} in Semaphore project {projectId}")
  public void verifyCannotDelete(SemaphoreSessionApis session, long projectId, long hostConfigId) {
    Calls.expectStatus(session.hostConfigs().deleteHostConfig(projectId, hostConfigId), 403);
  }

  private void verifyValidationError(Call<?> call, String expectedError) {
    Response<?> response = Calls.response(call);
    Calls.expectStatus(response, 400);
    try (var body = response.errorBody()) {
      String diagnostic = body == null ? "" : body.string();
      if (!validationMessage(diagnostic).contains(expectedError)) {
        throw new IllegalStateException(
            "Host config validation response did not contain '%s'. Body: %s"
                .formatted(expectedError, diagnostic));
      }
    } catch (IOException error) {
      throw new IllegalStateException(
          "Could not read Semaphore host config validation response", error);
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
}
