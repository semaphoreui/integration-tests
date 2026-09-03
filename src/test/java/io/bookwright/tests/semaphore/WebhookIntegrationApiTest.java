package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
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
  @DisplayName("Token-authenticated project webhook routes and extracts task variables")
  void tokenAuthenticatedWebhookRoutesAndExtractsTaskVariables(
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
    var integration =
        api.semaphore()
            .integrations()
            .create(
                project.id(), fixture.integrationRequest(project.id(), template.id(), key.id()));
    var alias = api.semaphore().integrations().createProjectAlias(project.id());

    api.semaphore()
        .integrations()
        .addMatcher(project.id(), integration.id(), fixture.matcherRequest(integration.id()));
    api.semaphore()
        .integrations()
        .addExtractValue(
            project.id(), integration.id(), fixture.releaseExtractor(integration.id()));
    api.semaphore()
        .integrations()
        .addExtractValue(project.id(), integration.id(), fixture.traceExtractor(integration.id()));

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
    var task = api.semaphore().tasks().waitUntilTaskSucceeds(project.id(), dispatch.taskId());

    assertThat(dispatch.projectId()).isEqualTo(project.id());
    assertThat(dispatch.templateId()).isEqualTo(template.id());
    assertThat(dispatch.integrationId()).isEqualTo(integration.id());
    assertThat(task.integrationId()).isEqualTo(integration.id());
    assertThat(task.templateId()).isEqualTo(template.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), task.id(), fixture.outputMarker());
  }
}
