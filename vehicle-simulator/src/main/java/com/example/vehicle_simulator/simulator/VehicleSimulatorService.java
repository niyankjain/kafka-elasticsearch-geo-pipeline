package com.example.vehicle_simulator.simulator;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class VehicleSimulatorService {

  private final KafkaTemplate<String, String> kafkaTemplate;

  public VehicleSimulatorService(KafkaTemplate<String, String> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  List<Map<String,Object>> vehicles = new ArrayList<>();

  @PostConstruct
  public void startSimulation() {

    for(int i=0;i<1000;i++){
      Map<String,Object> v=new HashMap<>();
      v.put("vehicleId","CAR_"+i);
      v.put("lat",18.5204+ThreadLocalRandom.current().nextDouble(-0.5,0.5));
      v.put("lon",73.8567+ThreadLocalRandom.current().nextDouble(-0.5,0.5));
      vehicles.add(v);
    }

    new Thread(() -> {

      while(true){

        for(Map<String,Object> v:vehicles){

          double lat=(double)v.get("lat");
          double lon=(double)v.get("lon");

          lat+=ThreadLocalRandom.current().nextDouble(-0.0005,0.0005);
          lon+=ThreadLocalRandom.current().nextDouble(-0.0005,0.0005);

          v.put("lat",lat);
          v.put("lon",lon);

          String json = String.format(
              "{\"vehicleId\":\"%s\",\"location\":{\"lat\":%f,\"lon\":%f},\"timestamp\":%d}",
              v.get("vehicleId"),
              lat,
              lon,
              System.currentTimeMillis()
          );

          kafkaTemplate.send("vehicle-location",json);
        }

        try{
          Thread.sleep(1000);
        }catch(Exception e){}

      }

    }).start();
  }
}