package com.netflix.spinnaker.gate.model.discovery;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import java.util.List;
import lombok.Data;

@Data
@JsonRootName("applications")
public class DiscoveryApplications {
  @JsonProperty("application")
  private List<DiscoveryApplication> applications;
}
