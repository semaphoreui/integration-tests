package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.api.model.semaphore.BackupHostConfig;
import io.bookwright.api.model.semaphore.HostConfig;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreHostConfigFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore credential mappings")
class HostConfigApiTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Host and URL mappings can be created, read, updated and deleted")
  void mappingsRoundTrip(ApiSteps api, SemaphoreHostConfigFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var sshKey =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.sshKey());
    var secondSshKey =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.secondSshKey());
    var loginKey =
        api.semaphore()
            .accessKeys()
            .createAndVerifyMasked(project.id(), fixtures.loginPasswordKey());
    var hostMapping =
        api.semaphore()
            .hostConfigs()
            .create(project.id(), fixtures.hostMapping().request(project.id(), sshKey.id()));
    var paddedMapping =
        api.semaphore()
            .hostConfigs()
            .create(project.id(), fixtures.paddedHostMapping().request(project.id(), sshKey.id()));
    var urlMapping =
        api.semaphore()
            .hostConfigs()
            .create(project.id(), fixtures.urlMapping().request(project.id(), sshKey.id()));
    var httpsMapping =
        api.semaphore()
            .hostConfigs()
            .create(
                project.id(), fixtures.httpsLoginMapping().request(project.id(), loginKey.id()));

    assertThat(hostMapping.projectId()).isEqualTo(project.id());
    assertThat(hostMapping.type()).isEqualTo(fixtures.hostMapping().type());
    assertThat(hostMapping.host()).isEqualTo(fixtures.hostMapping().host());
    assertThat(hostMapping.sshKeyId()).isEqualTo(sshKey.id());
    assertThat(paddedMapping.host()).isEqualTo(fixtures.paddedHostMapping().storedHost());
    assertThat(urlMapping.type()).isEqualTo(fixtures.urlMapping().type());
    assertThat(httpsMapping.sshKeyId()).isEqualTo(loginKey.id());
    assertThat(api.semaphore().hostConfigs().getHostConfigs(project.id()))
        .extracting(HostConfig::id)
        .containsExactlyInAnyOrder(
            hostMapping.id(), paddedMapping.id(), urlMapping.id(), httpsMapping.id());
    assertThat(api.semaphore().hostConfigs().get(project.id(), urlMapping.id()))
        .isEqualTo(urlMapping);

    var updated =
        api.semaphore()
            .hostConfigs()
            .update(
                project.id(),
                fixtures
                    .updatedHostMapping()
                    .updateRequest(hostMapping.id(), project.id(), secondSshKey.id()));

    assertThat(updated.id()).isEqualTo(hostMapping.id());
    assertThat(updated.type()).isEqualTo(fixtures.updatedHostMapping().type());
    assertThat(updated.host()).isEqualTo(fixtures.updatedHostMapping().host());
    assertThat(updated.sshKeyId()).isEqualTo(secondSshKey.id());

    api.semaphore().hostConfigs().delete(project.id(), urlMapping.id());
    api.semaphore().hostConfigs().verifyAbsent(project.id(), urlMapping.id());

    assertThat(api.semaphore().hostConfigs().getHostConfigs(project.id()))
        .extracting(HostConfig::id)
        .containsExactlyInAnyOrder(hostMapping.id(), paddedMapping.id(), httpsMapping.id());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Malformed hosts and URLs are rejected before anything is stored")
  void malformedMappingsAreRejected(ApiSteps api, SemaphoreHostConfigFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var sshKey =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.sshKey());

    for (var malformed : fixtures.malformedMappings()) {
      api.semaphore()
          .hostConfigs()
          .verifyRejected(
              project.id(),
              malformed.mapping().request(project.id(), sshKey.id()),
              malformed.expectedError());
    }

    assertThat(api.semaphore().hostConfigs().getHostConfigs(project.id())).isEmpty();
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("The kind of credential must match the kind of mapping")
  void credentialKindMustMatchMappingType(ApiSteps api, SemaphoreHostConfigFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var loginKey =
        api.semaphore()
            .accessKeys()
            .createAndVerifyMasked(project.id(), fixtures.loginPasswordKey());
    var noneKey =
        api.semaphore().accessKeys().create(project.id(), fixtures.noneKey().request(project.id()));

    api.semaphore()
        .hostConfigs()
        .verifyRejected(
            project.id(),
            fixtures.hostMapping().request(project.id(), loginKey.id()),
            fixtures.errors().hostNeedsSshKey());
    api.semaphore()
        .hostConfigs()
        .verifyRejected(
            project.id(),
            fixtures.hostMapping().request(project.id(), noneKey.id()),
            fixtures.errors().hostNeedsSshKey());
    api.semaphore()
        .hostConfigs()
        .verifyRejected(
            project.id(),
            fixtures.urlMapping().request(project.id(), noneKey.id()),
            fixtures.errors().urlNeedsCredential());
    api.semaphore()
        .hostConfigs()
        .verifyRejected(
            project.id(),
            fixtures.httpLoginMapping().request(project.id(), loginKey.id()),
            fixtures.errors().loginPasswordNeedsHttps());

    var accepted =
        api.semaphore()
            .hostConfigs()
            .create(
                project.id(), fixtures.httpsLoginMapping().request(project.id(), loginKey.id()));
    api.semaphore()
        .hostConfigs()
        .verifyUpdateRejected(
            project.id(),
            fixtures.httpsLoginMapping().updateRequest(accepted.id(), project.id(), noneKey.id()),
            fixtures.errors().urlNeedsCredential());

    assertThat(api.semaphore().hostConfigs().getHostConfigs(project.id()))
        .containsExactly(accepted);
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("A host or URL is mapped at most once per project")
  void duplicateMappingsAreRejected(ApiSteps api, SemaphoreHostConfigFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var sshKey =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.sshKey());
    var secondSshKey =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.secondSshKey());
    var hostMapping =
        api.semaphore()
            .hostConfigs()
            .create(project.id(), fixtures.hostMapping().request(project.id(), sshKey.id()));
    var urlMapping =
        api.semaphore()
            .hostConfigs()
            .create(project.id(), fixtures.urlMapping().request(project.id(), sshKey.id()));

    api.semaphore()
        .hostConfigs()
        .verifyRejected(
            project.id(),
            fixtures.hostMapping().request(project.id(), secondSshKey.id()),
            fixtures.errors().duplicate());
    api.semaphore()
        .hostConfigs()
        .verifyRejected(
            project.id(),
            fixtures.paddedDuplicateHostMapping().request(project.id(), sshKey.id()),
            fixtures.errors().duplicate());
    api.semaphore()
        .hostConfigs()
        .verifyUpdateRejected(
            project.id(),
            fixtures.hostMapping().updateRequest(urlMapping.id(), project.id(), sshKey.id()),
            fixtures.errors().duplicate());

    assertThat(api.semaphore().hostConfigs().getHostConfigs(project.id()))
        .containsExactlyInAnyOrder(hostMapping, urlMapping);
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("A mapped credential can neither be deleted nor changed to an unusable type")
  void mappedCredentialIsProtected(ApiSteps api, SemaphoreHostConfigFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var sshKey =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.sshKey());
    var mapping =
        api.semaphore()
            .hostConfigs()
            .create(project.id(), fixtures.hostMapping().request(project.id(), sshKey.id()));

    api.semaphore()
        .accessKeys()
        .verifyCannotDelete(project.id(), sshKey.id(), fixtures.errors().credentialInUse());
    api.semaphore()
        .accessKeys()
        .verifyCannotRetype(
            project.id(),
            sshKey.id(),
            fixtures.retypeToLoginPassword(project.id(), sshKey.id()),
            fixtures.errors().hostNeedsSshKey());

    assertThat(api.semaphore().accessKeys().getHostConfigReferrerIds(project.id(), sshKey.id()))
        .containsExactly(mapping.id());

    api.semaphore().hostConfigs().delete(project.id(), mapping.id());

    // The strict cleanup of the key (HTTP 204) proves it is deletable once nothing maps to it.
    assertThat(api.semaphore().accessKeys().getHostConfigReferrerIds(project.id(), sshKey.id()))
        .isEmpty();
  }

  @Test
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_RBAC_USER_EXISTS})
  @DisplayName("Mappings are bound to the credentials of their project and to resource managers")
  void mappingsAreScopedToProjectAndRole(
      ApiSteps api, TestStore store, SemaphoreFixtures core, SemaphoreHostConfigFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var otherProject = api.semaphore().projects().createProject(fixtures.otherProject());
    var sshKey =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.sshKey());
    var foreignKey =
        api.semaphore()
            .accessKeys()
            .createAndVerifyMasked(otherProject.id(), fixtures.secondSshKey());

    api.semaphore()
        .hostConfigs()
        .verifyForeignCredentialRejected(
            project.id(), fixtures.foreignKeyMapping().request(project.id(), foreignKey.id()));

    var mapping =
        api.semaphore()
            .hostConfigs()
            .create(project.id(), fixtures.hostMapping().request(project.id(), sshKey.id()));
    var account = store.semaphoreRbacUser();
    api.semaphore()
        .users()
        .addToProject(project.id(), account.user().id(), core.rbac().taskRunnerRole());
    var session = api.semaphore().auth().loginAs(account);

    assertThat(api.semaphore().hostConfigs().getHostConfigs(session, project.id()))
        .containsExactly(mapping);
    api.semaphore()
        .hostConfigs()
        .verifyCannotCreate(
            session, project.id(), fixtures.urlMapping().request(project.id(), sshKey.id()));
    api.semaphore()
        .hostConfigs()
        .verifyCannotUpdate(
            session,
            project.id(),
            fixtures.updatedHostMapping().updateRequest(mapping.id(), project.id(), sshKey.id()));
    api.semaphore().hostConfigs().verifyCannotDelete(session, project.id(), mapping.id());
    assertThat(api.semaphore().hostConfigs().get(project.id(), mapping.id())).isEqualTo(mapping);
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Mappings travel through a project backup by credential name and restore intact")
  void mappingsSurviveProjectBackupRestore(ApiSteps api, SemaphoreHostConfigFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var sshKey =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.sshKey());
    var loginKey =
        api.semaphore()
            .accessKeys()
            .createAndVerifyMasked(project.id(), fixtures.loginPasswordKey());
    var hostMapping =
        api.semaphore()
            .hostConfigs()
            .create(project.id(), fixtures.hostMapping().request(project.id(), sshKey.id()));
    api.semaphore()
        .hostConfigs()
        .create(project.id(), fixtures.urlMapping().request(project.id(), sshKey.id()));
    api.semaphore()
        .hostConfigs()
        .create(project.id(), fixtures.httpsLoginMapping().request(project.id(), loginKey.id()));

    var backup =
        api.semaphore()
            .backups()
            .exportProjectAndVerifyMasked(project.id(), fixtures.loginPasswordKey());
    SecretAssertions.absent("project backup", backup.toString(), fixtures.sshKey());

    assertThat(api.semaphore().backups().hostConfigsInBackup(backup))
        .containsExactlyInAnyOrder(
            new BackupHostConfig(
                fixtures.hostMapping().type(),
                fixtures.hostMapping().host(),
                fixtures.sshKey().name()),
            new BackupHostConfig(
                fixtures.urlMapping().type(),
                fixtures.urlMapping().host(),
                fixtures.sshKey().name()),
            new BackupHostConfig(
                fixtures.httpsLoginMapping().type(),
                fixtures.httpsLoginMapping().host(),
                fixtures.loginPasswordKey().name()));

    api.semaphore()
        .backups()
        .verifyMissingHostConfigKeyRejected(
            backup, fixtures.backup().missingKeyProjectName(), fixtures.backup().missingKeyName());
    api.semaphore()
        .backups()
        .verifyDuplicateHostConfigRejected(backup, fixtures.backup().duplicateProjectName());
    api.semaphore()
        .backups()
        .verifyMalformedHostConfigRejected(
            backup, fixtures.backup().malformedProjectName(), fixtures.backup().malformedHost());

    var restored =
        api.semaphore().backups().restoreProject(backup, fixtures.backup().restoredProjectName());
    var restoredSshKey =
        api.semaphore().accessKeys().requireByName(restored.id(), fixtures.sshKey().name());
    var restoredLoginKey =
        api.semaphore()
            .accessKeys()
            .requireByName(restored.id(), fixtures.loginPasswordKey().name());
    var restoredHostMapping =
        api.semaphore().hostConfigs().requireByHost(restored.id(), fixtures.hostMapping().host());
    var restoredUrlMapping =
        api.semaphore().hostConfigs().requireByHost(restored.id(), fixtures.urlMapping().host());
    var restoredHttpsMapping =
        api.semaphore()
            .hostConfigs()
            .requireByHost(restored.id(), fixtures.httpsLoginMapping().host());

    assertThat(api.semaphore().hostConfigs().getHostConfigs(restored.id())).hasSize(3);
    assertThat(restoredSshKey.id()).isNotEqualTo(sshKey.id());
    assertThat(restoredHostMapping.id()).isNotEqualTo(hostMapping.id());
    assertThat(restoredHostMapping.projectId()).isEqualTo(restored.id());
    assertThat(restoredHostMapping.type()).isEqualTo(hostMapping.type());
    assertThat(restoredHostMapping.sshKeyId()).isEqualTo(restoredSshKey.id());
    assertThat(restoredUrlMapping.type()).isEqualTo(fixtures.urlMapping().type());
    assertThat(restoredUrlMapping.sshKeyId()).isEqualTo(restoredSshKey.id());
    assertThat(restoredHttpsMapping.sshKeyId()).isEqualTo(restoredLoginKey.id());
  }
}
