package com.netflix.spinnaker.gate.retrofit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit.RestAdapter;

public class Slf4jRetrofitLogger implements RestAdapter.Log {
  public Slf4jRetrofitLogger(Class type) {
    this(LoggerFactory.getLogger(type));
  }

  public Slf4jRetrofitLogger(Logger logger) {
    this.logger = logger;
  }

  @Override
  public void log(String message) {
    logger.info(message);
  }

  private final Logger logger;
}
