package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.api.model.semaphore.ApiToken;
import io.bookwright.fixtures.semaphore.SemaphoreTokenFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreTokenFixtures.Authentication;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Api
@OwnerDanil
@Feature("Semaphore API-token owner isolation")
@Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_TOKEN_OWNERS_EXIST})
class ApiTokenOwnerIsolationTest {

  @ParameterizedTest(name = "{0}")
  @EnumSource(Authentication.class)
  @DisplayName("Each user lists only their own token, without disclosing its secret")
  void tokenListsAreOwnerScoped(
      Authentication authentication,
      ApiSteps api,
      TestStore store,
      SemaphoreTokenFixtures fixture) {
    var owner = api.semaphore().auth().loginAs(store.semaphoreTokenOwner());
    var other = api.semaphore().auth().loginAs(store.semaphoreOtherTokenOwner());
    var ownerToken = api.semaphore().tokens().create(owner, fixture.validRequest());
    var otherToken = api.semaphore().tokens().create(other, fixture.secondValidRequest());

    assertOnlyToken(
        api.semaphore()
            .tokens()
            .getTokens(
                authentication == Authentication.COOKIE
                    ? owner
                    : api.semaphore().tokens().authenticate(ownerToken)),
        ownerToken);
    assertOnlyToken(
        api.semaphore()
            .tokens()
            .getTokens(
                authentication == Authentication.COOKIE
                    ? other
                    : api.semaphore().tokens().authenticate(otherToken)),
        otherToken);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Authentication.class)
  @DisplayName("Deleting another user's token is a no-op and preserves both owners' credentials")
  void foreignTokenCannotBeRevoked(
      Authentication authentication,
      ApiSteps api,
      TestStore store,
      SemaphoreTokenFixtures fixture) {
    var owner = api.semaphore().auth().loginAs(store.semaphoreTokenOwner());
    var other = api.semaphore().auth().loginAs(store.semaphoreOtherTokenOwner());
    var ownerToken = api.semaphore().tokens().create(owner, fixture.validRequest());
    var otherToken = api.semaphore().tokens().create(other, fixture.secondValidRequest());
    var ownerBearer = api.semaphore().tokens().authenticate(ownerToken);
    var otherBearer = api.semaphore().tokens().authenticate(otherToken);

    assertThat(api.semaphore().users().currentUser(ownerBearer).id())
        .isEqualTo(store.semaphoreTokenOwner().user().id());
    assertThat(api.semaphore().users().currentUser(otherBearer).id())
        .isEqualTo(store.semaphoreOtherTokenOwner().user().id());

    // DELETE is intentionally idempotent: a foreign prefix returns 204 without deleting anything.
    api.semaphore()
        .tokens()
        .delete(authentication == Authentication.COOKIE ? owner : ownerBearer, otherToken);

    assertOnlyToken(api.semaphore().tokens().getTokens(owner), ownerToken);
    assertOnlyToken(api.semaphore().tokens().getTokens(other), otherToken);
    assertThat(api.semaphore().users().currentUser(ownerBearer).id())
        .isEqualTo(store.semaphoreTokenOwner().user().id());
    assertThat(api.semaphore().users().currentUser(otherBearer).id())
        .isEqualTo(store.semaphoreOtherTokenOwner().user().id());

    // Positive control: the rightful owner can revoke the very same token.
    api.semaphore().tokens().delete(other, otherToken);
    api.semaphore().tokens().verifyRejected(otherBearer);
    assertThat(api.semaphore().tokens().getTokens(other)).isEmpty();
    assertThat(api.semaphore().users().currentUser(ownerBearer).id())
        .isEqualTo(store.semaphoreTokenOwner().user().id());
  }

  private void assertOnlyToken(List<ApiToken> listed, ApiToken expected) {
    assertThat(listed)
        .singleElement()
        .satisfies(
            token -> {
              // Compare as a boolean so a server leaking a full credential cannot print it here.
              assertThat(expected.prefix().equals(token.id()))
                  .as("Token listing exposes only the expected owner's public prefix")
                  .isTrue();
              assertThat(token.userId()).isEqualTo(expected.userId());
              assertThat(token.name()).isEqualTo(expected.name());
            });
  }
}
