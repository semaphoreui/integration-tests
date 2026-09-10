package io.bookwright.api.coverage;

record ApiCoverageObservation(ApiOperation operation, int status) {

  static ApiCoverageObservation parse(String line) {
    String[] fields = line.split("\\t", 3);
    if (fields.length != 3) {
      throw new IllegalArgumentException("Invalid API coverage observation: " + line);
    }
    return new ApiCoverageObservation(
        new ApiOperation(fields[1], fields[2]), Integer.parseInt(fields[0]));
  }

  boolean successful() {
    return status >= 200 && status < 400;
  }

  String serialize() {
    return "%d\t%s\t%s%n".formatted(status, operation.method(), operation.path());
  }
}
