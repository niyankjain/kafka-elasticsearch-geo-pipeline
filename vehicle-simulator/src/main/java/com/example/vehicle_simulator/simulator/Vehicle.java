package com.example.vehicle_simulator.simulator;

public class Vehicle {

  private String vehicleId;
  private double lat;
  private double lon;
  private long timestamp;

  public Vehicle(String vehicleId, double lat, double lon, long timestamp) {
    this.vehicleId = vehicleId;
    this.lat = lat;
    this.lon = lon;
    this.timestamp = timestamp;
  }

}
