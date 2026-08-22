package io.bookwright.steps.semaphore.integrations;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Integration;
import io.bookwright.api.model.semaphore.IntegrationAlias;
import io.bookwright.api.model.semaphore.IntegrationDispatch;
import io.bookwright.api.model.semaphore.IntegrationExtractValue;
import io.bookwright.api.model.semaphore.IntegrationExtractValueRequest;
import io.bookwright.api.model.semaphore.IntegrationMatcher;
import io.bookwright.api.model.semaphore.IntegrationMatcherRequest;
import io.bookwright.api.model.semaphore.IntegrationRequest;
import io.bookwright.api.semaphore.integrations.SemaphoreIntegrationsApi;
import io.bookwright.api.semaphore.tasks.SemaphoreTasksApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.Map;
import okhttp3.Headers;
import retrofit2.Response;

public class IntegrationSteps {

  private final SemaphoreIntegrationsApi api;
  private final SemaphoreTasksApi tasksApi;
  private final TeardownStorage teardown;

  @Inject
  public IntegrationSteps(
      SemaphoreIntegrationsApi api, SemaphoreTasksApi tasksApi, TeardownStorage teardown) {
    this.api = api;
    this.tasksApi = tasksApi;
    this.teardown = teardown;
  }

  @Step("Create Semaphore integration {request.name}")
  public Integration create(long projectId, IntegrationRequest request) {
    Integration integration =
        Calls.body(api.create(projectId, request), 201, "created integration");
    teardown.push(
        "Delete Semaphore integration " + integration.id(),
        () -> Calls.expectStatus(api.delete(projectId, integration.id()), 204));
    return integration;
  }

  @Step("Create shared webhook alias for Semaphore project {projectId}")
  public IntegrationAlias createProjectAlias(long projectId) {
    IntegrationAlias alias =
        Calls.body(api.createProjectAlias(projectId), 200, "project integration alias");
    teardown.push(
        "Delete Semaphore integration alias " + alias.id(),
        () -> Calls.expectStatus(api.deleteProjectAlias(projectId, alias.id()), 204));
    return alias;
  }

  @Step("Add matcher {request.name} to Semaphore integration {integrationId}")
  public IntegrationMatcher addMatcher(
      long projectId, long integrationId, IntegrationMatcherRequest request) {
    return Calls.body(
        api.addMatcher(projectId, integrationId, request), 200, "integration matcher");
  }

  @Step("Add extracted value {request.name} to Semaphore integration {integrationId}")
  public IntegrationExtractValue addExtractValue(
      long projectId, long integrationId, IntegrationExtractValueRequest request) {
    return Calls.body(
        api.addExtractValue(projectId, integrationId, request), 201, "integration extracted value");
  }

  @Step("Dispatch Semaphore webhook through {alias.url}")
  public IntegrationDispatch dispatch(
      IntegrationAlias alias, Map<String, String> headers, Map<String, Object> payload) {
    Response<Void> response = Calls.expectStatus(api.dispatch(alias.url(), headers, payload), 204);
    IntegrationDispatch dispatch = requiredDispatch(response.headers(), alias);
    teardown.push(
        "Delete webhook-created Semaphore task " + dispatch.taskId(),
        () ->
            Calls.expectStatus(tasksApi.deleteTask(dispatch.projectId(), dispatch.taskId()), 204));
    return dispatch;
  }

  @Step("Verify Semaphore ignores webhook sent through {alias.url}")
  public void verifyIgnored(
      IntegrationAlias alias, Map<String, String> headers, Map<String, Object> payload) {
    Response<Void> response = Calls.expectStatus(api.dispatch(alias.url(), headers, payload), 204);
    if (response.headers().get("X-Semaphore-Task-ID") != null) {
      throw new IllegalStateException(
          "Semaphore unexpectedly launched task %s for webhook alias %s"
              .formatted(response.headers().get("X-Semaphore-Task-ID"), alias.id()));
    }
  }

  private IntegrationDispatch requiredDispatch(Headers headers, IntegrationAlias alias) {
    return new IntegrationDispatch(
        requiredHeader(headers, "X-Semaphore-Task-ID", alias),
        requiredHeader(headers, "X-Semaphore-Template-ID", alias),
        requiredHeader(headers, "X-Semaphore-Project-ID", alias),
        requiredHeader(headers, "X-Semaphore-Integration-ID", alias),
        optionalHeader(headers, "X-Semaphore-Inventory-ID", alias));
  }

  private long requiredHeader(Headers headers, String name, IntegrationAlias alias) {
    Long value = optionalHeader(headers, name, alias);
    if (value == null) {
      throw new IllegalStateException(
          "Semaphore returned 204 for webhook alias %d without required %s header"
              .formatted(alias.id(), name));
    }
    return value;
  }

  private Long optionalHeader(Headers headers, String name, IntegrationAlias alias) {
    String value = headers.get(name);
    if (value == null) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException error) {
      throw new IllegalStateException(
          "Semaphore returned non-numeric %s='%s' for webhook alias %d"
              .formatted(name, value, alias.id()),
          error);
    }
  }
}
