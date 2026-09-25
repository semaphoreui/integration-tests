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
import io.bookwright.api.model.semaphore.IntegrationUpdateRequest;
import io.bookwright.api.model.semaphore.WebhookHeaders;
import io.bookwright.api.semaphore.integrations.SemaphoreIntegrationsApi;
import io.bookwright.api.semaphore.tasks.SemaphoreTasksApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Param;
import io.qameta.allure.Step;
import io.qameta.allure.model.Parameter;
import java.util.List;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.RequestBody;
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

  @Step("List Semaphore integrations in project {projectId}")
  public List<Integration> getIntegrations(long projectId) {
    return Calls.body(api.getIntegrations(projectId), 200, "integrations");
  }

  @Step("Get Semaphore integration {integrationId}")
  public Integration get(long projectId, long integrationId) {
    return Calls.body(api.get(projectId, integrationId), 200, "integration");
  }

  @Step("Update Semaphore integration {integrationId}")
  public Integration update(long projectId, long integrationId, IntegrationUpdateRequest request) {
    Calls.expectStatus(api.update(projectId, integrationId, request), 204);
    return get(projectId, integrationId);
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

  @Step("List shared webhook aliases for Semaphore project {projectId}")
  public List<IntegrationAlias> getProjectAliases(long projectId) {
    return Calls.body(api.getProjectAliases(projectId), 200, "project integration aliases");
  }

  @Step("Create webhook alias for Semaphore integration {integrationId}")
  public IntegrationAlias createIntegrationAlias(long projectId, long integrationId) {
    IntegrationAlias alias =
        Calls.body(api.createIntegrationAlias(projectId, integrationId), 200, "integration alias");
    teardown.push(
        "Delete Semaphore integration alias " + alias.id(),
        () -> deleteIntegrationAliasIfPresent(projectId, integrationId, alias.id()));
    return alias;
  }

  @Step("List webhook aliases for Semaphore integration {integrationId}")
  public List<IntegrationAlias> getIntegrationAliases(long projectId, long integrationId) {
    return Calls.body(
        api.getIntegrationAliases(projectId, integrationId), 200, "integration aliases");
  }

  @Step("Delete webhook alias {aliasId} from Semaphore integration {integrationId}")
  public void deleteIntegrationAlias(long projectId, long integrationId, long aliasId) {
    Calls.expectStatus(api.deleteIntegrationAlias(projectId, integrationId, aliasId), 204);
  }

  @Step("List matchers for Semaphore integration {integrationId}")
  public List<IntegrationMatcher> getMatchers(long projectId, long integrationId) {
    return Calls.body(api.getMatchers(projectId, integrationId), 200, "integration matchers");
  }

  @Step("Add matcher {request.name} to Semaphore integration {integrationId}")
  public IntegrationMatcher addMatcher(
      long projectId, long integrationId, IntegrationMatcherRequest request) {
    return Calls.body(
        api.addMatcher(projectId, integrationId, request), 200, "integration matcher");
  }

  @Step("Update matcher {matcherId} in Semaphore integration {integrationId}")
  public IntegrationMatcher updateMatcher(
      long projectId, long integrationId, long matcherId, IntegrationMatcherRequest request) {
    Calls.expectStatus(api.updateMatcher(projectId, integrationId, matcherId, request), 204);
    return requireMatcher(projectId, integrationId, matcherId);
  }

  @Step("Delete matcher {matcherId} from Semaphore integration {integrationId}")
  public void deleteMatcher(long projectId, long integrationId, long matcherId) {
    Calls.expectStatus(api.deleteMatcher(projectId, integrationId, matcherId), 204);
  }

  @Step("List extracted values for Semaphore integration {integrationId}")
  public List<IntegrationExtractValue> getExtractValues(long projectId, long integrationId) {
    return Calls.body(
        api.getExtractValues(projectId, integrationId), 200, "integration extracted values");
  }

  @Step("Add extracted value {request.name} to Semaphore integration {integrationId}")
  public IntegrationExtractValue addExtractValue(
      long projectId, long integrationId, IntegrationExtractValueRequest request) {
    return Calls.body(
        api.addExtractValue(projectId, integrationId, request), 201, "integration extracted value");
  }

  @Step("Update extracted value {valueId} in Semaphore integration {integrationId}")
  public IntegrationExtractValue updateExtractValue(
      long projectId, long integrationId, long valueId, IntegrationExtractValueRequest request) {
    Calls.expectStatus(api.updateExtractValue(projectId, integrationId, valueId, request), 204);
    return requireExtractValue(projectId, integrationId, valueId);
  }

  @Step("Delete extracted value {valueId} from Semaphore integration {integrationId}")
  public void deleteExtractValue(long projectId, long integrationId, long valueId) {
    Calls.expectStatus(api.deleteExtractValue(projectId, integrationId, valueId), 204);
  }

  @Step("Dispatch Semaphore webhook through {alias.url}")
  public IntegrationDispatch dispatch(
      IntegrationAlias alias,
      @Param(mode = Parameter.Mode.HIDDEN) WebhookHeaders headers,
      String payload) {
    Response<Void> response =
        Calls.expectStatus(api.dispatch(alias.url(), headers.values(), jsonBody(payload)), 204);
    IntegrationDispatch dispatch = requiredDispatch(response.headers(), alias);
    teardown.push(
        "Delete webhook-created Semaphore task " + dispatch.taskId(),
        () ->
            Calls.expectStatus(tasksApi.deleteTask(dispatch.projectId(), dispatch.taskId()), 204));
    return dispatch;
  }

  @Step("Verify Semaphore ignores webhook sent through {alias.url}")
  public void verifyIgnored(
      IntegrationAlias alias,
      @Param(mode = Parameter.Mode.HIDDEN) WebhookHeaders headers,
      String payload) {
    Response<Void> response =
        Calls.expectStatus(api.dispatch(alias.url(), headers.values(), jsonBody(payload)), 204);
    if (response.headers().get("X-Semaphore-Task-ID") != null) {
      throw new IllegalStateException(
          "Semaphore unexpectedly launched task %s for webhook alias %s"
              .formatted(response.headers().get("X-Semaphore-Task-ID"), alias.id()));
    }
  }

  private IntegrationMatcher requireMatcher(long projectId, long integrationId, long matcherId) {
    List<IntegrationMatcher> matchers = getMatchers(projectId, integrationId);
    return matchers.stream()
        .filter(matcher -> matcher.id() == matcherId)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Required matcher %d was not found in Semaphore integration %d. Available IDs: %s"
                        .formatted(
                            matcherId,
                            integrationId,
                            matchers.stream().map(IntegrationMatcher::id).toList())));
  }

  private RequestBody jsonBody(String payload) {
    return RequestBody.create(payload, MediaType.get("application/json"));
  }

  private IntegrationExtractValue requireExtractValue(
      long projectId, long integrationId, long valueId) {
    List<IntegrationExtractValue> values = getExtractValues(projectId, integrationId);
    return values.stream()
        .filter(value -> value.id() == valueId)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Required extracted value %d was not found in Semaphore integration %d. Available IDs: %s"
                        .formatted(
                            valueId,
                            integrationId,
                            values.stream().map(IntegrationExtractValue::id).toList())));
  }

  private void deleteIntegrationAliasIfPresent(long projectId, long integrationId, long aliasId) {
    var response = Calls.response(api.deleteIntegrationAlias(projectId, integrationId, aliasId));
    Calls.expectStatus(response, 204, 404);
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
