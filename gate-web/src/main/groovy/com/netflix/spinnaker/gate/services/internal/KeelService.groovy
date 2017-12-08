/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.gate.services.internal

import retrofit.http.Body
import retrofit.http.DELETE
import retrofit.http.GET
import retrofit.http.Headers
import retrofit.http.POST
import retrofit.http.Path
import retrofit.http.Query

interface KeelService {

  @GET("/v1/intents/{id}")
  Map getIntent(@Path("id") String intentId)

  @GET("/v1/intents")
  List<Map> getIntents(@Query("status") List<String> status)

  @DELETE("/v1/intents/{id}")
  Map deleteIntent(@Path("id") String id)

  @Headers("Content-type: application/json")
  @POST("/v1/intents")
  List<Map> upsertIntent(@Body Map upsertIntentRequest)

  @GET("/v1/intents/{id}/history")
  List<String> getHistory(@Path("id") String id)

  @GET("/v1/intents/{id}/traces")
  List<Map> getTraces(@Path("id") String id)
}
