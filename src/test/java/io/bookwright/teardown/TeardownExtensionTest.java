package io.bookwright.teardown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeardownExtensionTest {

  @Test
  void executesActionsInReverseOrder() {
    TeardownStorage storage = new TeardownStorage();
    List<String> executionOrder = new ArrayList<>();
    storage.push("first", () -> executionOrder.add("first"));
    storage.push("second", () -> executionOrder.add("second"));

    TeardownExtension.execute(storage, true, false);

    assertThat(executionOrder).containsExactly("second", "first");
  }

  @Test
  void failsSuccessfulTestWhenConfigured() {
    TeardownStorage storage = failingStorage();

    assertThatThrownBy(() -> TeardownExtension.execute(storage, true, false))
        .isInstanceOf(TeardownException.class)
        .hasMessage("1 teardown action(s) failed")
        .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));
  }

  @Test
  void allPreparationsRunBeforeAnyResourceDeletion() {
    TeardownStorage storage = new TeardownStorage();
    List<String> order = new ArrayList<>();
    storage.push("project", () -> order.add("delete project"));
    storage.beforeCleanup("running task", () -> order.add("stop running"));
    storage.push("running task", () -> order.add("delete running"));
    storage.beforeCleanup("queued task", () -> order.add("stop queued"));
    storage.push("queued task", () -> order.add("delete queued"));

    TeardownExtension.execute(storage, true, false);

    assertThat(order)
        .containsExactly(
            "stop queued", "stop running", "delete queued", "delete running", "delete project");
  }

  @Test
  void preservesPrimaryTestFailure() {
    assertThatCode(() -> TeardownExtension.execute(failingStorage(), true, true))
        .doesNotThrowAnyException();
  }

  @Test
  void canLogCleanupFailureWithoutFailingTest() {
    assertThatCode(() -> TeardownExtension.execute(failingStorage(), false, false))
        .doesNotThrowAnyException();
  }

  @Test
  void retainedMultiPhaseDataIsNotCleanedUp() {
    TeardownStorage storage = new TeardownStorage();
    List<String> deleted = new ArrayList<>();
    storage.push("upgrade fixture", () -> deleted.add("upgrade fixture"));
    storage.beforeCleanup("upgrade task", () -> deleted.add("stopped upgrade task"));

    storage.retainCreatedData();
    TeardownExtension.execute(storage, true, false);

    assertThat(deleted).isEmpty();
  }

  @Test
  void explicitCleanupDiscardsPreparationsAndDeletions() {
    TeardownStorage storage = new TeardownStorage();
    List<String> actions = new ArrayList<>();
    storage.beforeCleanup("stop", () -> actions.add("stop"));
    storage.push("delete", () -> actions.add("delete"));

    storage.discardAfterExplicitCleanup();
    TeardownExtension.execute(storage, true, false);

    assertThat(actions).isEmpty();
  }

  private TeardownStorage failingStorage() {
    TeardownStorage storage = new TeardownStorage();
    storage.push(
        "broken cleanup",
        () -> {
          throw new IllegalStateException("cleanup failed");
        });
    return storage;
  }
}
