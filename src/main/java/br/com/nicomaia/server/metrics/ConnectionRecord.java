package br.com.nicomaia.server.metrics;

import java.time.LocalTime;

public record ConnectionRecord(
    LocalTime time, String destination, Status status, long bytesUp, long bytesDown) {

  public enum Status {
    OK,
    FAIL
  }
}
