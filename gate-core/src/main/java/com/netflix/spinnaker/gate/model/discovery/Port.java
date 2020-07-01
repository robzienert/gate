package com.netflix.spinnaker.gate.model.discovery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Port {

  private final boolean enabled;
  private final int port;

  @JsonCreator
  public static Port buildPort(
      @JsonProperty("@enabled") boolean enabled, @JsonProperty("$") int port) {
    return new Port(enabled, port);
  }
}
