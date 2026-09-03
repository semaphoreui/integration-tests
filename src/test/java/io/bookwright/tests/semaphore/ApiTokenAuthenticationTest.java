package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreTokenFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore API tokens")
class ApiTokenAuthenticationTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("API token authenticates requests until it is revoked")
  void apiTokenAuthenticatesUntilRevoked(ApiSteps api, SemaphoreTokenFixtures fixture) {
    var created = api.semaphore().tokens().create(fixture.validRequest());
    var listed = api.semaphore().tokens().requireByName(fixture.tokenName());
    var tokenSession = api.semaphore().tokens().authenticate(created);
    var bearerUser = api.semaphore().users().currentUser(tokenSession);
    var project = api.semaphore().projects().createProject(tokenSession, fixture.projectRequest());

    assertThat(created.id().length()).isGreaterThan(8);
    assertThat(created.userId()).isEqualTo(bearerUser.id());
    assertThat(created.expired()).isFalse();
    assertThat(created.expiresAt()).isNotNull();
    assertThat(listed.id()).isEqualTo(created.prefix());
    assertThat(listed.name()).isEqualTo(created.name());
    assertThat(project.name()).isEqualTo(fixture.projectName());

    api.semaphore().tokens().delete(created);
    api.semaphore().tokens().verifyRejected(tokenSession);
    api.semaphore().tokens().verifyExpiredRequestRejected(fixture.expiredRequest());
  }
}
