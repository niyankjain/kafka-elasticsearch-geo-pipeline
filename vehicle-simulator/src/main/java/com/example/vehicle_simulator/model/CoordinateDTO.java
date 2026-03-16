package com.example.vehicle_simulator.model;

import lombok.Data;
import lombok.Getter;

@Data
public class CoordinateDTO {

  private String vehicleId;
  private Location location;
  private long timestamp;

  @Getter
  public static class Location {
    private double lat;
    private double lon;
  }
}
