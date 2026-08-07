package io.bookwright.junit;

import lombok.experimental.UtilityClass;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Creates the shared JUnit store namespaces. Store keys live next to the fixture or lifecycle
 * component that owns the corresponding state, so this class does not become a domain registry.
 * Class scope holds reusable resources; method scope remains isolated per test.
 */
@UtilityClass
public class NamespaceRegistry {

  public ExtensionContext.Store classStore(ExtensionContext context) {
    Class<?> testClass = context.getRequiredTestClass();
    ExtensionContext classContext = context;
    while (classContext.getTestMethod().isPresent()) {
      classContext =
          classContext
              .getParent()
              .orElseThrow(() -> new IllegalStateException("Test class context is not available"));
    }
    return classContext.getStore(
        ExtensionContext.Namespace.create(NamespaceRegistry.class, testClass));
  }

  public ExtensionContext.Store methodStore(ExtensionContext context) {
    return context.getStore(
        ExtensionContext.Namespace.create(
            NamespaceRegistry.class,
            context.getRequiredTestClass(),
            context.getRequiredTestMethod()));
  }
}
