package io.bookwright.teardown;

import io.bookwright.junit.NamespaceRegistry;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Method-scoped LIFO queue of cleanup actions. The instance lives in the JUnit Store, so ownership
 * follows the test lifecycle rather than a worker thread.
 */
public class TeardownStorage {

  private static final String STORE_KEY = "teardownStorage";

  public record TeardownAction(String name, Runnable action) {}

  private final Deque<TeardownAction> actions = new ArrayDeque<>();
  private final Deque<TeardownAction> preparations = new ArrayDeque<>();

  public static TeardownStorage getOrCreate(ExtensionContext context) {
    return NamespaceRegistry.methodStore(context)
        .getOrComputeIfAbsent(STORE_KEY, key -> new TeardownStorage(), TeardownStorage.class);
  }

  public static TeardownStorage get(ExtensionContext context) {
    return NamespaceRegistry.methodStore(context).get(STORE_KEY, TeardownStorage.class);
  }

  public void push(String name, Runnable action) {
    actions.addLast(new TeardownAction(name, action));
  }

  /** Runs before any resource deletion, e.g. to cancel all tasks that share executor capacity. */
  public void beforeCleanup(String name, Runnable action) {
    preparations.addLast(new TeardownAction(name, action));
  }

  /**
   * Discards the queued cleanup only after a multi-phase test has successfully created the state
   * that the next phase must inspect.
   */
  public void retainCreatedData() {
    clear();
  }

  /** Discards redundant actions after a test has explicitly removed all resources it created. */
  public void discardAfterExplicitCleanup() {
    clear();
  }

  TeardownAction pollLast() {
    return preparations.isEmpty() ? actions.pollLast() : preparations.pollLast();
  }

  void clear() {
    preparations.clear();
    actions.clear();
  }
}
