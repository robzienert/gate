package com.netflix.spinnaker.gate.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.gate.config.ServiceConfiguration;
import com.netflix.spinnaker.gate.model.discovery.DiscoveryApplication;
import com.netflix.spinnaker.gate.model.discovery.DiscoveryApplications;
import com.netflix.spinnaker.gate.retrofit.Slf4jRetrofitLogger;
import com.netflix.spinnaker.gate.services.internal.EurekaService;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import okhttp3.OkHttpClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import retrofit.Endpoint;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.converter.JacksonConverter;

@Component
public class EurekaLookupService {

  private static final Map<String, CachedDiscoveryApplication> instanceCache =
      new ConcurrentHashMap<>();
  private static final Map<String, EurekaService> eurekaServiceCache = new ConcurrentHashMap<>();

  private ServiceConfiguration serviceConfiguration;
  private OkHttpClient okHttpClient;

  public EurekaLookupService(ServiceConfiguration serviceConfiguration, OkHttpClient okHttpClient) {
    this.serviceConfiguration = serviceConfiguration;
    this.okHttpClient = okHttpClient;
  }

  @Scheduled(fixedRate = 30_000)
  public void cacheApplications() {
    instanceCache
        .keySet()
        .forEach(
            vip -> {
              CachedDiscoveryApplication cache = instanceCache.get(vip);
              if (cache.isExpired()) {
                getApplications(vip);
              }
            });
  }

  public List<DiscoveryApplication> getApplications(String vip) {
    if (instanceCache.containsKey(vip) && !instanceCache.get(vip).isExpired()) {
      return instanceCache.get(vip).getApplications();
    }

    List<String> hosts = new ArrayList<>(serviceConfiguration.getDiscoveryHosts());
    Collections.shuffle(hosts);

    DiscoveryApplications app = null;
    for (String host : hosts) {
      EurekaService eureka = getEurekaService(host);
      try {
        app = eureka.getVips(vip);
        if (app != null && !app.getApplications().isEmpty()) {
          instanceCache.put(vip, new CachedDiscoveryApplication(app.getApplications()));
          break;
        }

      } catch (RetrofitError e) {
        if (e.getResponse().getStatus() != 404) {
          throw e;
        }
      }
    }

    if (app == null) {
      return null;
    }

    return app.getApplications();
  }

  private EurekaService getEurekaService(String host) {
    return eurekaServiceCache.computeIfAbsent(
        host,
        s -> {
          Endpoint endpoint = Endpoints.newFixedEndpoint(host);
          return new RestAdapter.Builder()
              .setEndpoint(endpoint)
              .setConverter(
                  new JacksonConverter(
                      new ObjectMapper()
                          .configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true)
                          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                          .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)))
              .setClient(new Ok3Client(okHttpClient))
              .setLogLevel(RestAdapter.LogLevel.BASIC)
              .setLog(new Slf4jRetrofitLogger(EurekaService.class))
              .build()
              .create(EurekaService.class);
        });
  }

  @Data
  public static class CachedDiscoveryApplication {

    private final List<DiscoveryApplication> applications;

    private final Long ttl = TimeUnit.SECONDS.toMillis(60);
    private final Long cacheTime = System.currentTimeMillis();

    public CachedDiscoveryApplication(List<DiscoveryApplication> applications) {
      this.applications = applications;
    }

    public boolean isExpired() {
      return (System.currentTimeMillis() - cacheTime) > ttl;
    }
  }
}
