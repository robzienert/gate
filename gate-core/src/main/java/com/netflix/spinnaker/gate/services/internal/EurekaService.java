package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.gate.model.discovery.DiscoveryApplications;
import retrofit.http.GET;
import retrofit.http.Headers;
import retrofit.http.Path;

public interface EurekaService {
  @Headers("Accept: application/json")
  @GET("/discovery/v2/vips/{vipAddress}")
  DiscoveryApplications getVips(@Path("vipAddress") String vipAddress);

  @Headers("Accept: application/json")
  @GET("/discovery/v2/svips/{secureVipAddress}")
  DiscoveryApplications getSecureVips(@Path("secureVipAddress") String secureVipAddress);
}
