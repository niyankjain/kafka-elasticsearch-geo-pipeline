package com.example.vehicle_simulator.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.example.vehicle_simulator.model.Coordinate;

public interface CoordinateRepository
    extends ElasticsearchRepository<Coordinate, String> {
}
