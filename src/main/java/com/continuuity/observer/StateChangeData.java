package com.continuuity.observer;

/**
 *
 */
public interface StateChangeData {
  public long getTimestamp();

  public String getAccountId();

  public String getApplication();

  public String getRunId();

  public String getFlowName();

  public String getPayload();

  public StateChangeType getType();
}
