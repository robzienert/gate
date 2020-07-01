package com.netflix.spinnaker.gate.model.discovery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DiscoveryInstance {
  public static final String HEALTH_TYPE = "Discovery";

  private final String hostName;
  private final Port port;
  private final Port securePort;
  private final String application;
  private final String ipAddress;
  private final String status;
  private final String overriddenStatus;
  private final String state;
  private final String availabilityZone;
  private final String instanceId;
  private final String amiId;
  private final String instanceType;
  private final String healthCheckUrl;
  private final String vipAddress;
  private final Long lastUpdatedTimestamp;
  private final String asgName;

  @JsonCreator
  public static DiscoveryInstance buildInstance(
      @JsonProperty("hostName") String hostName,
      @JsonProperty("port") Port port,
      @JsonProperty("securePort") Port securePort,
      @JsonProperty("app") String app,
      @JsonProperty("ipAddr") String ipAddr,
      @JsonProperty("status") String status,
      @JsonProperty("overriddenstatus") String overriddenstatus,
      @JsonProperty("dataCenterInfo") DataCenterInfo dataCenterInfo,
      @JsonProperty("healthCheckUrl") String healthCheckUrl,
      @JsonProperty("vipAddress") String vipAddress,
      @JsonProperty("lastUpdatedTimestamp") long lastUpdatedTimestamp,
      @JsonProperty("asgName") String asgName) {
    DataCenterMetadata meta = dataCenterInfo.getMetadata();
    return new DiscoveryInstance(
        hostName,
        port,
        securePort,
        app,
        ipAddr,
        status,
        overriddenstatus,
        status,
        (meta == null ? null : meta.getAvailabilityZone()),
        (meta == null ? null : meta.getInstanceId()),
        (meta == null ? null : meta.getAmiId()),
        (meta == null ? null : meta.getInstanceType()),
        healthCheckUrl,
        vipAddress,
        lastUpdatedTimestamp,
        asgName);
  }

  public String getType() {
    return HEALTH_TYPE;
  }
}
