package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.api.model.semaphore.Integration;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreIntegrationFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore webhook integrations")
class WebhookIntegrationApiTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Integration settings are listed, readable and updatable")
  void integrationSettingsPersist(
      ApiSteps api, SemaphoreFixtures core, SemaphoreIntegrationFixtures fixture) {
    var integration = createIntegration(api, core, fixture);

    assertThat(api.semaphore().integrations().getIntegrations(integration.projectId()))
        .extracting(item -> item.id())
        .contains(integration.id());
    assertThat(api.semaphore().integrations().get(integration.projectId(), integration.id()))
        .isEqualTo(integration);
    assertThat(
            api.semaphore()
                .integrations()
                .update(
                    integration.projectId(),
                    integration.id(),
                    fixture.updatedIntegration(integration)))
        .satisfies(
            updated -> {
              assertThat(updated.name()).isEqualTo(fixture.updatedIntegrationName());
              assertThat(updated.searchable()).isFalse();
            });
  }

  @Test
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_PROJECT_EXISTS})
  @DisplayName("Shared project webhook alias is persisted in the project alias list")
  void projectAliasIsListed(ApiSteps api, TestStore store) {
    var project = store.semaphoreProject();
    var alias = api.semaphore().integrations().createProjectAlias(project.id());

    assertThat(api.semaphore().integrations().getProjectAliases(project.id())).contains(alias);
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Integration-specific alias can be listed and deleted")
  void integrationAliasLifecycle(
      ApiSteps api, SemaphoreFixtures core, SemaphoreIntegrationFixtures fixture) {
    var integration = createIntegration(api, core, fixture);
    var alias =
        api.semaphore()
            .integrations()
            .createIntegrationAlias(integration.projectId(), integration.id());

    assertThat(
            api.semaphore()
                .integrations()
                .getIntegrationAliases(integration.projectId(), integration.id()))
        .contains(alias);
    api.semaphore()
        .integrations()
        .deleteIntegrationAlias(integration.projectId(), integration.id(), alias.id());
    assertThat(
            api.semaphore()
                .integrations()
                .getIntegrationAliases(integration.projectId(), integration.id()))
        .doesNotContain(alias);
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Integration matcher persists creation, update and deletion")
  void matcherLifecycle(
      ApiSteps api, SemaphoreFixtures core, SemaphoreIntegrationFixtures fixture) {
    var integration = createIntegration(api, core, fixture);
    var matcher =
        api.semaphore()
            .integrations()
            .addMatcher(
                integration.projectId(),
                integration.id(),
                fixture.matcherRequest(integration.id()));

    assertThat(
            api.semaphore().integrations().getMatchers(integration.projectId(), integration.id()))
        .contains(matcher);
    var update = fixture.updatedMatcher(integration.id());
    assertThat(
            api.semaphore()
                .integrations()
                .updateMatcher(integration.projectId(), integration.id(), matcher.id(), update))
        .satisfies(
            updated -> {
              assertThat(updated.name()).isEqualTo(update.name());
              assertThat(updated.value()).isEqualTo(update.value());
            });
    api.semaphore()
        .integrations()
        .deleteMatcher(integration.projectId(), integration.id(), matcher.id());
    assertThat(
            api.semaphore().integrations().getMatchers(integration.projectId(), integration.id()))
        .extracting(item -> item.id())
        .doesNotContain(matcher.id());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Integration extractor persists creation, update and deletion")
  void extractorLifecycle(
      ApiSteps api, SemaphoreFixtures core, SemaphoreIntegrationFixtures fixture) {
    var integration = createIntegration(api, core, fixture);
    var extractor =
        api.semaphore()
            .integrations()
            .addExtractValue(
                integration.projectId(),
                integration.id(),
                fixture.releaseExtractor(integration.id()));

    assertThat(
            api.semaphore()
                .integrations()
                .getExtractValues(integration.projectId(), integration.id()))
        .contains(extractor);
    assertThat(
            api.semaphore()
                .integrations()
                .updateExtractValue(
                    integration.projectId(),
                    integration.id(),
                    extractor.id(),
                    fixture.updatedReleaseExtractor(integration.id())))
        .satisfies(
            updated -> {
              assertThat(updated.name()).isEqualTo(fixture.updatedReleaseExtractorName());
              assertThat(updated.variable()).isEqualTo(fixture.updatedReleaseVariable());
            });
    api.semaphore()
        .integrations()
        .deleteExtractValue(integration.projectId(), integration.id(), extractor.id());
    assertThat(
            api.semaphore()
                .integrations()
                .getExtractValues(integration.projectId(), integration.id()))
        .extracting(item -> item.id())
        .doesNotContain(extractor.id());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Webhook with an invalid token does not create a task")
  void invalidTokenDoesNotDispatch(
      ApiSteps api, SemaphoreFixtures core, SemaphoreIntegrationFixtures fixture) {
    var integration = createIntegration(api, core, fixture);
    var alias = api.semaphore().integrations().createProjectAlias(integration.projectId());
    api.semaphore()
        .integrations()
        .addMatcher(
            integration.projectId(), integration.id(), fixture.matcherRequest(integration.id()));

    api.semaphore()
        .integrations()
        .verifyIgnored(
            integration.projectId(), alias, fixture.invalidTokenHeaders(), fixture.payload());

    assertThat(api.semaphore().tasks().getTasks(integration.projectId())).isEmpty();
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Authenticated webhook with an unmatched event does not create a task")
  void unmatchedEventDoesNotDispatch(
      ApiSteps api, SemaphoreFixtures core, SemaphoreIntegrationFixtures fixture) {
    var integration = createIntegration(api, core, fixture);
    var alias = api.semaphore().integrations().createProjectAlias(integration.projectId());
    api.semaphore()
        .integrations()
        .addMatcher(
            integration.projectId(), integration.id(), fixture.matcherRequest(integration.id()));

    api.semaphore()
        .integrations()
        .verifyIgnored(
            integration.projectId(), alias, fixture.unmatchedHeaders(), fixture.payload());

    assertThat(api.semaphore().tasks().getTasks(integration.projectId())).isEmpty();
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Token-authenticated project webhook routes and extracts task variables")
  void tokenAuthenticatedWebhookRoutesAndExtractsTaskVariables(
      ApiSteps api, SemaphoreFixtures core, SemaphoreIntegrationFixtures fixture) {
    var integration = createIntegration(api, core, fixture);
    var alias = api.semaphore().integrations().createProjectAlias(integration.projectId());

    api.semaphore()
        .integrations()
        .addMatcher(
            integration.projectId(), integration.id(), fixture.matcherRequest(integration.id()));
    api.semaphore()
        .integrations()
        .addExtractValue(
            integration.projectId(), integration.id(), fixture.releaseExtractor(integration.id()));
    api.semaphore()
        .integrations()
        .addExtractValue(
            integration.projectId(), integration.id(), fixture.traceExtractor(integration.id()));

    var dispatch =
        api.semaphore()
            .integrations()
            .dispatch(alias, fixture.acceptedHeaders(), fixture.payload());
    var task =
        api.semaphore().tasks().waitUntilTaskSucceeds(integration.projectId(), dispatch.taskId());

    assertThat(dispatch.projectId()).isEqualTo(integration.projectId());
    assertThat(dispatch.templateId()).isEqualTo(integration.templateId());
    assertThat(dispatch.integrationId()).isEqualTo(integration.id());
    assertThat(task.integrationId()).isEqualTo(integration.id());
    assertThat(task.templateId()).isEqualTo(integration.templateId());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(integration.projectId(), task.id(), fixture.outputMarker());
  }

  private Integration createIntegration(
      ApiSteps api, SemaphoreFixtures core, SemaphoreIntegrationFixtures fixture) {
    var project = api.semaphore().projects().createProject(fixture.projectRequest());
    var key = api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixture.authKey());
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), core.repositories().primary().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), core.inventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixture.templateRequest(project.id(), repository.id(), inventory.id()));
    return api.semaphore()
        .integrations()
        .create(project.id(), fixture.integrationRequest(project.id(), template.id(), key.id()));
  }
}
