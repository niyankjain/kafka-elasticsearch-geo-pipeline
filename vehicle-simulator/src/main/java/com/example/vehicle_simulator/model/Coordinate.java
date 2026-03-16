package com.example.vehicle_simulator.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import lombok.Data;

@Data
@Document(indexName = "vehicle-location")
public class Coordinate {

  @Id
  private String id;
  private String vehicleId;
  private GeoPoint location;
  private long timestamp;
}
