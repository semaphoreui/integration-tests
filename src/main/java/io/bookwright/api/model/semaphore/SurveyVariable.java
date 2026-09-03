package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SurveyVariable(
    String name,
    String title,
    boolean required,
    String type,
    String target,
    String description,
    List<SurveyVariableValue> values,
    @JsonProperty("default_value") String defaultValue) {

  public SurveyVariable {
    type = type == null ? "" : type;
    target = target == null ? "" : target;
    values = values == null ? List.of() : List.copyOf(values);
  }
}
