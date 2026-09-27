package io.bookwright.tests.framework;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.steps.ApiSteps;
import io.bookwright.steps.restfulbooker.auth.AuthSteps;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.TestData;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StoreIsolationTest {

  private static final Set<ApiSteps> API_FACADES = ConcurrentHashMap.newKeySet();
  private static final Set<AuthSteps> AUTH_STEPS = ConcurrentHashMap.newKeySet();
  private static final Set<Long> TEST_SEEDS = ConcurrentHashMap.newKeySet();
  private static final Set<TeardownStorage> PARAMETERIZED_TEARDOWNS = ConcurrentHashMap.newKeySet();
  private static final Set<Integer> COMPLETED_INVOCATIONS = ConcurrentHashMap.newKeySet();

  @Test
  void firstMethodOwnsItsStore(TestData data, ApiSteps api) {
    recordIsolation(data, api);
  }

  @Test
  void secondMethodOwnsItsStore(TestData data, ApiSteps api) {
    recordIsolation(data, api);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void parameterizedInvocationsOwnTheirStore(
      int invocation, TestData data, ApiSteps api, TeardownStorage teardown) {
    recordIsolation(data, api);
    PARAMETERIZED_TEARDOWNS.add(teardown);
    teardown.push(
        "Record completed invocation " + invocation, () -> COMPLETED_INVOCATIONS.add(invocation));
  }

  @AfterAll
  static void storesAreInvocationScoped() {
    assertThat(API_FACADES).hasSize(4);
    assertThat(AUTH_STEPS).hasSize(4);
    assertThat(TEST_SEEDS).hasSize(4);
    assertThat(PARAMETERIZED_TEARDOWNS).hasSize(2);
    assertThat(COMPLETED_INVOCATIONS).containsExactlyInAnyOrder(1, 2);
  }

  private void recordIsolation(TestData data, ApiSteps api) {
    API_FACADES.add(api);
    AUTH_STEPS.add(api.restfulBooker().auth());
    TEST_SEEDS.add(data.testSeed());
  }
}
