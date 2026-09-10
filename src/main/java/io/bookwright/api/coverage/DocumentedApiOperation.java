package io.bookwright.api.coverage;

record DocumentedApiOperation(String component, ApiOperation operation)
    implements Comparable<DocumentedApiOperation> {

  @Override
  public int compareTo(DocumentedApiOperation other) {
    int componentComparison = component.compareTo(other.component);
    return componentComparison != 0 ? componentComparison : operation.compareTo(other.operation);
  }

  int literalSegments() {
    return (int)
        java.util.Arrays.stream(operation.path().split("/"))
            .filter(segment -> !segment.isBlank())
            .filter(segment -> !segment.startsWith("{"))
            .count();
  }
}
