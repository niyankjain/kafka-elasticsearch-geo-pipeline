package com.example.vehicle_simulator.consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.example.vehicle_simulator.model.Coordinate;
import com.example.vehicle_simulator.model.CoordinateDTO;
import com.example.vehicle_simulator.repository.CoordinateRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class CoordinateConsumer {

  @Autowired
  private CoordinateRepository repository;

  private final ObjectMapper mapper = new ObjectMapper();

  @KafkaListener(topics = "vehicle-location", groupId = "vehicle-group")
  public void consume(String message) {

    CoordinateDTO dto = mapper.readValue(message, CoordinateDTO.class);

    Coordinate coordinate = new Coordinate();

    coordinate.setVehicleId(dto.getVehicleId());
    coordinate.setLocation(new GeoPoint(dto.getLocation().getLat(), dto.getLocation().getLon()));
    coordinate.setTimestamp(dto.getTimestamp());

    repository.save(coordinate);
    System.out.println("Saved to Elasticsearch: " + coordinate);
  }
}
