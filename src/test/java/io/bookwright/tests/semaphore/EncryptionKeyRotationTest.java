package io.bookwright.tests.semaphore;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreEncryptionRotationFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.bookwright.teardown.TeardownStorage;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@OwnerDanil
@Feature("Semaphore database-encryption key rotation")
class EncryptionKeyRotationTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @EnabledIfSystemProperty(named = "SEMAPHORE_ENCRYPTION_ROTATION_PHASE", matches = "seed")
  @DisplayName("Old primary encryption key creates persisted secrets and an executable template")
  void seedOldPrimary(
      ApiSteps api, SemaphoreEncryptionRotationFixtures fixture, TeardownStorage teardown) {
    var project = api.semaphore().projects().createProject(fixture.project());
    var key =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixture.oldPrimaryKey());
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixture.repository().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixture.inventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixture.template().request(project.id(), repository.id(), inventory.id()));

    runTemplateAndVerifyOutput(api, project.id(), template.id(), fixture.outputMarker());

    teardown.retainCreatedData();
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @EnabledIfSystemProperty(named = "SEMAPHORE_ENCRYPTION_ROTATION_PHASE", matches = "rotated")
  @DisplayName("New primary writes new secrets while the retired key still decrypts old data")
  void verifyHotReloadedPrimary(
      ApiSteps api, SemaphoreEncryptionRotationFixtures fixture, TeardownStorage teardown) {
    var project = api.semaphore().projects().requireByName(fixture.project().name());
    var oldKey =
        api.semaphore().accessKeys().requireByName(project.id(), fixture.oldPrimaryKey().name());
    var template =
        api.semaphore().templates().requireByName(project.id(), fixture.template().name());

    api.semaphore().accessKeys().verifyMasked(project.id(), oldKey.id(), fixture.oldPrimaryKey());
    api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixture.newPrimaryKey());
    runTemplateAndVerifyOutput(api, project.id(), template.id(), fixture.outputMarker());

    teardown.retainCreatedData();
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @EnabledIfSystemProperty(named = "SEMAPHORE_ENCRYPTION_ROTATION_PHASE", matches = "verify")
  @DisplayName("Rekeyed secrets work after the retired encryption key is removed")
  void verifyRekeyedData(ApiSteps api, SemaphoreEncryptionRotationFixtures fixture) {
    var project = api.semaphore().projects().requireByName(fixture.project().name());
    api.semaphore().projects().deleteAfterTest(project);
    var template =
        api.semaphore().templates().requireByName(project.id(), fixture.template().name());
    var oldKey =
        api.semaphore().accessKeys().requireByName(project.id(), fixture.oldPrimaryKey().name());
    var newKey =
        api.semaphore().accessKeys().requireByName(project.id(), fixture.newPrimaryKey().name());

    api.semaphore().accessKeys().verifyMasked(project.id(), oldKey.id(), fixture.oldPrimaryKey());
    api.semaphore().accessKeys().verifyMasked(project.id(), newKey.id(), fixture.newPrimaryKey());
    api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixture.postRekeyKey());
    runTemplateAndVerifyOutput(api, project.id(), template.id(), fixture.outputMarker());
  }

  private void runTemplateAndVerifyOutput(
      ApiSteps api, long projectId, long templateId, String outputMarker) {
    var task = api.semaphore().tasks().startAndWait(projectId, templateId);
    api.semaphore().tasks().waitUntilTaskOutputContains(projectId, task.id(), outputMarker);
  }
}
