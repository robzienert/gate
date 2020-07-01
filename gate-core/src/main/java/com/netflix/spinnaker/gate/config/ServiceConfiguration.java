/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.config;

import static java.lang.String.format;

import com.google.common.base.Strings;
import com.google.common.collect.Streams;
import java.util.*;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import retrofit.Endpoint;
import retrofit.Endpoints;

@Component
@ConfigurationProperties
public class ServiceConfiguration {

  private List<String> healthCheckableServices;
  private List<String> discoveryHosts;
  private Map<String, Service> services = new HashMap<>();
  private Map<String, Service> integrations = new HashMap<>();

  @Autowired private ApplicationContext ctx;

  @PostConstruct
  public void postConstruct() {
    // this check is done in a @PostConstruct to avoid Spring's list merging in
    // @ConfigurationProperties (vs. overriding)
    if (!hasHealthcheckableServices()) {
      healthCheckableServices =
          Arrays.asList(
              "orca", "clouddriver", "echo", "igor", "flex", "front50", "mahe", "mine", "keel");
    }
  }

  public Optional<Service> getService(String name) {
    return Streams.concat(services.entrySet().stream(), integrations.entrySet().stream())
        .filter(it -> it.getKey().equals(name))
        .findFirst()
        .map(Map.Entry::getValue);
  }

  public Service getRequiredService(String name) {
    return getService(name)
        .orElseThrow(() -> new IllegalArgumentException(format("Unknown service '%s'", name)));
  }

  public Endpoint getServiceEndpoint(String serviceName, String dynamicName) {
    Service service = getRequiredService(serviceName);

    Endpoint endpoint;
    if (dynamicName == null) {
      // TODO: move Netflix-specific logic out of the OSS implementation
      endpoint =
          hasDiscoveryHosts() && !Strings.isNullOrEmpty(service.getVipAddress())
              ? Endpoints.newFixedEndpoint("niws://" + service.getVipAddress())
              : Endpoints.newFixedEndpoint(service.getBaseUrl());
    } else {
      if (!service.getConfig().containsKey("dynamicEndpoints")) {
        throw new IllegalArgumentException(
            format("Unknown dynamicEndpoint '%s' for service '%s'", dynamicName, serviceName));
      }

      Map<String, String> dynamicEndpoints =
          (Map<String, String>) service.getConfig().get("dynamicEndpoints");

      endpoint = Endpoints.newFixedEndpoint(dynamicEndpoints.get(dynamicName));
    }

    return endpoint;
  }

  private boolean hasHealthcheckableServices() {
    return healthCheckableServices != null && !healthCheckableServices.isEmpty();
  }

  private boolean hasDiscoveryHosts() {
    return discoveryHosts != null && !discoveryHosts.isEmpty();
  }
}
