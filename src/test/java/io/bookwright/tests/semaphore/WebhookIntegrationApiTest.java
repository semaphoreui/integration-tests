package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.api.model.semaphore.Integration;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreIntegrationFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
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
  @DisplayName("Webhook integration configuration supports its full lifecycle")
  void webhookIntegrationConfigurationLifecycle(
      ApiSteps api, SemaphoreFixtures core, SemaphoreIntegrationFixtures fixture) {
    var context = createIntegration(api, core, fixture);
    var integration = context.integration();

    assertThat(api.semaphore().integrations().getIntegrations(context.projectId()))
        .extracting(item -> item.id())
        .contains(integration.id());
    assertThat(api.semaphore().integrations().get(context.projectId(), integration.id()))
        .isEqualTo(integration);
    assertThat(
            api.semaphore()
                .integrations()
                .update(
                    context.projectId(), integration.id(), fixture.updatedIntegration(integration)))
        .satisfies(
            updated -> {
              assertThat(updated.name()).isEqualTo(fixture.updatedIntegrationName());
              assertThat(updated.searchable()).isFalse();
            });

    var projectAlias = api.semaphore().integrations().createProjectAlias(context.projectId());
    var integrationAlias =
        api.semaphore()
            .integrations()
            .createIntegrationAlias(context.projectId(), integration.id());
    assertThat(api.semaphore().integrations().getProjectAliases(context.projectId()))
        .contains(projectAlias);
    assertThat(
            api.semaphore()
                .integrations()
                .getIntegrationAliases(context.projectId(), integration.id()))
        .contains(integrationAlias);

    var matcher =
        api.semaphore()
            .integrations()
            .addMatcher(
                context.projectId(), integration.id(), fixture.matcherRequest(integration.id()));
    var extractedValue =
        api.semaphore()
            .integrations()
            .addExtractValue(
                context.projectId(), integration.id(), fixture.releaseExtractor(integration.id()));
    assertThat(api.semaphore().integrations().getMatchers(context.projectId(), integration.id()))
        .contains(matcher);
    assertThat(
            api.semaphore().integrations().getExtractValues(context.projectId(), integration.id()))
        .contains(extractedValue);
    assertThat(
            api.semaphore()
                .integrations()
                .updateExtractValue(
                    context.projectId(),
                    integration.id(),
                    extractedValue.id(),
                    fixture.updatedReleaseExtractor(integration.id())))
        .satisfies(
            updated -> {
              assertThat(updated.name()).isEqualTo(fixture.updatedReleaseExtractorName());
              assertThat(updated.variable()).isEqualTo(fixture.updatedReleaseVariable());
            });

    api.semaphore()
        .integrations()
        .deleteIntegrationAlias(context.projectId(), integration.id(), integrationAlias.id());

    assertThat(
            api.semaphore()
                .integrations()
                .getIntegrationAliases(context.projectId(), integration.id()))
        .doesNotContain(integrationAlias);
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Known defect: integration child mutations return false success")
  void integrationChildMutationsReturnFalseSuccess(
      ApiSteps api, SemaphoreFixtures core, SemaphoreIntegrationFixtures fixture) {
    var context = createIntegration(api, core, fixture);
    var integration = context.integration();
    var matcher =
        api.semaphore()
            .integrations()
            .addMatcher(
                context.projectId(), integration.id(), fixture.matcherRequest(integration.id()));
    var extractedValue =
        api.semaphore()
            .integrations()
            .addExtractValue(
                context.projectId(), integration.id(), fixture.releaseExtractor(integration.id()));

    assertThat(
            api.semaphore()
                .integrations()
                .updateMatcher(
                    context.projectId(),
                    integration.id(),
                    matcher.id(),
                    fixture.updatedMatcher(integration.id())))
        .satisfies(
            unchanged -> {
              assertThat(unchanged.name()).isEqualTo(matcher.name());
              assertThat(unchanged.value()).isEqualTo(matcher.value());
            });
    api.semaphore()
        .integrations()
        .deleteMatcher(context.projectId(), integration.id(), matcher.id());
    api.semaphore()
        .integrations()
        .deleteExtractValue(context.projectId(), integration.id(), extractedValue.id());

    assertThat(api.semaphore().integrations().getMatchers(context.projectId(), integration.id()))
        .contains(matcher);
    assertThat(
            api.semaphore().integrations().getExtractValues(context.projectId(), integration.id()))
        .contains(extractedValue);
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Token-authenticated project webhook routes and extracts task variables")
  void tokenAuthenticatedWebhookRoutesAndExtractsTaskVariables(
      ApiSteps api, SemaphoreFixtures core, SemaphoreIntegrationFixtures fixture) {
    var context = createIntegration(api, core, fixture);
    var integration = context.integration();
    var alias = api.semaphore().integrations().createProjectAlias(context.projectId());

    api.semaphore()
        .integrations()
        .addMatcher(
            context.projectId(), integration.id(), fixture.matcherRequest(integration.id()));
    api.semaphore()
        .integrations()
        .addExtractValue(
            context.projectId(), integration.id(), fixture.releaseExtractor(integration.id()));
    api.semaphore()
        .integrations()
        .addExtractValue(
            context.projectId(), integration.id(), fixture.traceExtractor(integration.id()));

    api.semaphore()
        .integrations()
        .verifyIgnored(alias, fixture.invalidTokenHeaders(), fixture.payload());
    api.semaphore()
        .integrations()
        .verifyIgnored(alias, fixture.unmatchedHeaders(), fixture.payload());

    var dispatch =
        api.semaphore()
            .integrations()
            .dispatch(alias, fixture.acceptedHeaders(), fixture.payload());
    var task =
        api.semaphore().tasks().waitUntilTaskSucceeds(context.projectId(), dispatch.taskId());

    assertThat(dispatch.projectId()).isEqualTo(context.projectId());
    assertThat(dispatch.templateId()).isEqualTo(integration.templateId());
    assertThat(dispatch.integrationId()).isEqualTo(integration.id());
    assertThat(task.integrationId()).isEqualTo(integration.id());
    assertThat(task.templateId()).isEqualTo(integration.templateId());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(context.projectId(), task.id(), fixture.outputMarker());
  }

  private IntegrationContext createIntegration(
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
    return new IntegrationContext(
        project.id(),
        api.semaphore()
            .integrations()
            .create(
                project.id(), fixture.integrationRequest(project.id(), template.id(), key.id())));
  }

  private record IntegrationContext(long projectId, Integration integration) {}
}
