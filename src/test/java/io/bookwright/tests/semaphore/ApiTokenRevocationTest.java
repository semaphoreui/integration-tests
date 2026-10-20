package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreTokenFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore API-token revocation isolation")
class ApiTokenRevocationTest {

  @Test
  @Preconditions({
    Precondition.SEMAPHORE_ADMIN_SESSION,
    Precondition.SEMAPHORE_PROJECT_ISOLATION_ACTOR_EXISTS
  })
  @DisplayName("Revoking one token preserves another token and the owner's cookie session")
  void revocationDoesNotAffectOtherCredentials(
      ApiSteps api, TestStore store, SemaphoreTokenFixtures fixture) {
    var owner = api.semaphore().auth().loginAs(store.semaphoreIsolationUser());
    var first = api.semaphore().tokens().create(owner, fixture.validRequest());
    var second = api.semaphore().tokens().create(owner, fixture.secondValidRequest());
    var firstBearer = api.semaphore().tokens().authenticate(first);
    var secondBearer = api.semaphore().tokens().authenticate(second);
    var project = store.semaphoreAccessibleProject();

    api.semaphore().users().verifyProjectReadable(firstBearer, project.id());
    api.semaphore().users().verifyProjectReadable(secondBearer, project.id());
    api.semaphore().tokens().delete(owner, first);

    api.semaphore().tokens().verifyRejected(firstBearer);
    api.semaphore().users().verifyProjectReadable(secondBearer, project.id());
    api.semaphore().users().verifyProjectReadable(owner, project.id());
    assertThat(api.semaphore().tokens().getTokens(owner))
        .extracting(token -> token.id())
        .containsExactly(second.prefix());
  }
}
