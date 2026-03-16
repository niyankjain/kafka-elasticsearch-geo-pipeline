# Kafka-Elasticsearch Geo-Pipeline

A real-time vehicle tracking system that generates 10,000 coordinate points per second, processes them through Kafka, and visualizes them in Kibana with live map tracking capabilities.

## 🚗 Architecture Overview

This system demonstrates a complete real-time data pipeline:
- **Vehicle Simulator**: Generates realistic vehicle coordinates (10,000 vehicles, 1 update/second = 10,000 coordinates/second)
- **Apache Kafka**: High-throughput message broker for data streaming
- **Consumer Options**: 
  - Spring Boot Consumer (for development/low throughput)
  - Logstash (recommended for production)
  - Fluentd (lightweight alternative)
- **Elasticsearch**: Stores and indexes geo-location data with geo-point mapping
- **Kibana**: Real-time map visualization with live tracking capabilities

## 📊 Performance Metrics

- **Throughput**: 10,000 coordinates/second
- **Vehicles Tracked**: 10,000 simultaneous vehicles
- **Update Frequency**: 1 update per vehicle per second
- **Data Format**: JSON with vehicle ID, geo-coordinates, and timestamp

## 🏛️ System Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Vehicle        │    │                 │    │                 │    │                 │    │                 │
│  Simulator      │───▶│   Apache Kafka  │───▶│   Consumer(s)   │───▶│  Elasticsearch  │───▶│    Kibana       │
│  (Producer)     │    │                 │    │  Spring Boot/   │    │                 │    │   (Maps UI)     │
│                 │    │                 │    │  Logstash/      │    │                 │    │                 │
│                 │    │                 │    │  Fluentd        │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Consumer Options Comparison

| Consumer | Resource Usage | Performance | Complexity | Use Case |
|----------|----------------|-------------|------------|----------|
| **Spring Boot** | High (JVM ~512MB) | Medium | Low | Development, testing |
| **Logstash** | Medium (JVM ~256MB) | High | Medium | Production, high throughput |
| **Fluentd** | Low (Ruby ~50MB) | High | Low-Medium | Resource-constrained, cloud-native |

## 🚀 Quick Start

### Prerequisites

- **Java 21+** (for Spring Boot option)
- **Gradle 7.0+** (for Spring Boot option)
- **Docker & Docker Compose** (recommended for infrastructure)
- **Kafka** (message broker)
- **Elasticsearch** (data storage)
- **Kibana** (visualization)

### Choose Your Consumer Setup

#### Option 1: Spring Boot Consumer (Development)
- **Resource Usage**: ~512MB RAM
- **Best for**: Development, testing, low throughput
- **Setup**: See "Spring Boot Consumer Setup" below

#### Option 2: Logstash (Production Recommended)
- **Resource Usage**: ~256MB RAM  
- **Best for**: Production, high throughput, better performance
- **Setup**: See "Logstash Setup" below

#### Option 3: Fluentd (Resource-Constrained)
- **Resource Usage**: ~50MB RAM
- **Best for**: Resource-constrained environments, cloud-native
- **Setup**: See "Fluentd Setup" below

### 1. Start Infrastructure Services

```bash
# Start Kafka, Zookeeper, Elasticsearch, and Kibana
docker-compose up -d
```

Create `docker-compose.yml`:

```yaml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms1g -Xmx1g"
    ports:
      - "9200:9200"
    volumes:
      - es_data:/usr/share/elasticsearch/data

  kibana:
    image: docker.elastic.co/kibana/kibana:8.11.0
    depends_on:
      - elasticsearch
    ports:
      - "5601:5601"
    environment:
      ELASTICSEARCH_HOSTS: http://elasticsearch:9200

volumes:
  es_data:
```

### 2A. Spring Boot Consumer Setup (Development)

```bash
# Navigate to vehicle simulator directory
cd vehicle-simulator

# Build the application
./gradlew build

# Run the Spring Boot application
./gradlew bootRun
```

### 2B. Logstash Setup (Production Recommended)

Create `logstash.conf`:

```ruby
input {
  kafka {
    bootstrap_servers => "localhost:9092"
    topics => ["vehicle-location"]
    group_id => "logstash-consumer"
    consumer_threads => 4
    fetch_max_bytes => 1048576
    max_poll_records => 500
    codec => "json"
  }
}

filter {
  # Convert timestamp to proper format
  if [timestamp] {
    date {
      match => [ "timestamp", "UNIX_MS" ]
      target => "@timestamp"
    }
  }
  
  # Ensure geo-point format for Elasticsearch
  if [location] {
    mutate {
      rename => { "location" => "[location]" }
    }
  }
}

output {
  elasticsearch {
    hosts => ["localhost:9200"]
    index => "vehicle-location-%{+YYYY.MM.dd}"
    document_type => "_doc"
    template_name => "vehicle-location"
    template => "/path/to/vehicle-location-template.json"
    template_overwrite => true
  }
  
  # Optional: Debug output
  stdout {
    codec => "rubydebug"
  }
}
```

Run Logstash:

```bash
# Using Docker
docker run -d --name logstash \
  -v "$(pwd)/logstash.conf:/usr/share/logstash/pipeline/logstash.conf" \
  -p 5044:5044 \
  docker.elastic.co/logstash/logstash:8.11.0

# Or install locally
logstash -f logstash.conf
```

### 2C. Fluentd Setup (Resource-Constrained)

Create `fluent.conf`:

```xml
<source>
  @type kafka
  brokers localhost:9092
  topics vehicle-location
  format json
  @id kafka_input
</source>

<filter vehicle-location>
  @type record_transformer
  <record>
    timestamp ${Time.at(record["timestamp"].to_f / 1000).utc}
  </record>
</filter>

<match vehicle-location>
  @type elasticsearch
  host localhost
  port 9200
  index_name vehicle-location
  type_name _doc
  include_timestamp true
  <buffer>
    @type file
    path /var/log/fluentd-buffers/kubernetes.system.buffer
    flush_mode interval
    retry_type exponential_backoff
    flush_thread_count 2
    flush_interval 5s
    retry_forever
    retry_max_interval 30
    chunk_limit_size 2M
    queue_limit_length 8
    overflow_action block
  </buffer>
</match>
```

Run Fluentd:

```bash
# Using Docker
docker run -d --name fluentd \
  -v "$(pwd)/fluent.conf:/fluentd/etc/fluent.conf" \
  -p 24224:24224 \
  fluent/fluentd:v1.16-debian-1

# Or install locally
fluentd -c fluent.conf
```

### 3. Start Vehicle Simulator

```bash
# Run the Spring Boot application (producer only)
cd vehicle-simulator
./gradlew bootRun
```

### 4. Verify Setup

1. **Kafka Topic**: The application automatically creates the `vehicle-location` topic
2. **Elasticsearch Index**: Check `vehicle-location` index at http://localhost:9200/vehicle-location
3. **Kibana Dashboard**: Access Kibana at http://localhost:5601
4. **Consumer Monitoring**: Check logs of your chosen consumer

## 📱 Kibana Map Visualization Setup

### 1. Create Index Pattern

1. Go to **Stack Management** → **Index Patterns**
2. Create new pattern: `vehicle-location*`
3. Select `@timestamp` as time field

### 2. Configure Map Visualization

1. Go to **Analytics** → **Maps**
2. Click **Create map** → **Add layer**
3. Select **Documents** layer
4. Configure:
   - **Index pattern**: `vehicle-location*`
   - **Geo field**: `location`
   - **Time filter**: Enable for real-time updates

### 3. Add Live Updates

1. In map settings, enable **Auto-refresh**
2. Set refresh interval to **1 second**
3. Add filters for specific vehicles if needed

### 4. Custom Styling

- **Color by**: `vehicleId` (different colors per vehicle)
- **Symbol size**: Small dots for better performance
- **Heat map**: Optional for density visualization

## 🔧 Configuration

### Application Properties

```properties
# Kafka Configuration
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.acks=1
spring.kafka.producer.batch-size=32768
spring.kafka.producer.linger-ms=20

# Elasticsearch Configuration
spring.elasticsearch.uris=http://localhost:9200

# Application
spring.application.name=vehicle-simulator
```

### Performance Tuning

#### Kafka Producer Optimization
```properties
# Higher throughput settings
spring.kafka.producer.batch-size=65536
spring.kafka.producer.linger-ms=5
spring.kafka.producer.compression-type=snappy
spring.kafka.producer.buffer.memory=33554432
```

#### Elasticsearch Optimization
```yaml
# Elasticsearch settings for high write throughput
index.refresh_interval: 30s
index.translog.flush_threshold_size: 512mb
index.merge.policy.max_merged_segment_size: 1gb
```

## 📊 Data Format

### Vehicle Location Message

```json
{
  "vehicleId": "CAR_42",
  "location": {
    "lat": 18.520430,
    "lon": 73.856740
  },
  "timestamp": 1710648200000
}
```

### Elasticsearch Document

```json
{
  "_id": "generated-id",
  "vehicleId": "CAR_42",
  "location": {
    "lat": 18.520430,
    "lon": 73.856740
  },
  "timestamp": 1710648200000
}
```

## 🛠️ Project Structure

```
vehicle-simulator/
├── src/main/java/com/example/vehicle_simulator/
│   ├── VehicleSimulatorApplication.java     # Main application class
│   ├── simulator/
│   │   ├── VehicleSimulatorService.java     # Generates vehicle coordinates
│   │   └── Vehicle.java                     # Vehicle model
│   ├── consumer/
│   │   └── CoordinateConsumer.java          # Kafka consumer and ES indexer
│   ├── model/
│   │   ├── Coordinate.java                  # Elasticsearch document
│   │   └── CoordinateDTO.java               # Kafka message DTO
│   └── repository/
│       └── CoordinateRepository.java        # Elasticsearch repository
└── src/main/resources/
    └── application.properties                # Configuration
```

## 🔍 Monitoring and Debugging

### Kafka Monitoring

```bash
# List topics
kafka-topics.sh --list --bootstrap-server localhost:9092

# Monitor topic messages
kafka-console-consumer.sh --topic vehicle-location --from-beginning --bootstrap-server localhost:9092

# Check topic lag
kafka-consumer-groups.sh --describe --group vehicle-group --bootstrap-server localhost:9092
```

### Elasticsearch Monitoring

```bash
# Check cluster health
curl -X GET "localhost:9200/_cluster/health?pretty"

# Get index stats
curl -X GET "localhost:9200/vehicle-location/_stats?pretty"

# Search recent documents
curl -X GET "localhost:9200/vehicle-location/_search?pretty" -H 'Content-Type: application/json' -d'
{
  "query": { "match_all": {} },
  "sort": [{ "timestamp": { "order": "desc" } }],
  "size": 10
}'
```

### Application Logs

```bash
# Monitor application logs
tail -f logs/application.log

# Check Kafka consumer offsets
grep "Saved to Elasticsearch" logs/application.log | wc -l
```

## 🚨 Performance Considerations

### High-Volume Throughput (10,000 Vehicles)

1. **Batch Processing**: Kafka producer uses batching for efficiency
2. **Async Processing**: Consumer processes messages asynchronously  
3. **Connection Pooling**: Reuse connections to Elasticsearch
4. **Memory Management**: Monitor heap usage with 10K TPS

### Consumer Performance Comparison

| Metric | Spring Boot | Logstash | Fluentd |
|--------|-------------|----------|---------|
| **Memory Usage** | 512MB+ | 256MB | 50MB |
| **CPU Usage** | High | Medium | Low |
| **Throughput** | ~5K msg/s | ~15K msg/s | ~12K msg/s |
| **Startup Time** | ~30s | ~15s | ~5s |
| **Configuration** | Java Code | Ruby Config | XML Config |

### Scaling Recommendations

#### Horizontal Scaling
- **Multiple Consumers**: Run multiple instances with same consumer group
- **Kafka Partitions**: Increase topic partitions for parallelism (recommended: 10+ partitions for 10K vehicles)
- **Elasticsearch Cluster**: Multi-node cluster for high availability

#### Vertical Scaling
- **Kafka Broker**: Increase heap to 2GB+ for 10K vehicles
- **Elasticsearch**: Allocate 2GB+ heap memory
- **Application**: Increase Spring Boot memory to 1GB+

### Resource Optimization for 10K Vehicles

#### For Spring Boot Consumer
```properties
# Increase JVM heap
JAVA_OPTS=-Xms1g -Xmx2g

# Kafka consumer optimization
spring.kafka.consumer.max-poll-records=1000
spring.kafka.consumer.fetch-max-bytes=2097152
spring.kafka.consumer.fetch-min-bytes=1024
```

#### For Logstash
```ruby
# Increase worker threads
pipeline.workers: 4
pipeline.batch.size: 500
pipeline.batch.delay: 10
```

#### For Fluentd
```xml
# Increase buffer size
<buffer>
  @type file
  path /var/log/fluentd-buffers/vehicle.buffer
  flush_mode interval
  flush_interval 1s
  chunk_limit_size 10M
  queue_limit_length 32
</buffer>
```

## 🔒 Security Considerations

### Production Setup

1. **Kafka Security**:
   - Enable SASL authentication
   - Configure SSL encryption
   - Use ACLs for topic access

2. **Elasticsearch Security**:
   - Enable X-Pack security
   - Configure user authentication
   - Set up index-level security

3. **Network Security**:
   - Use VPN or private networks
   - Configure firewall rules
   - Monitor access logs

## 🐛 Troubleshooting

### Common Issues

#### Kafka Connection Issues
```bash
# Check Kafka broker status
docker logs kafka_container_name

# Verify topic creation
kafka-topics.sh --describe --topic vehicle-location --bootstrap-server localhost:9092
```

#### Elasticsearch Index Issues
```bash
# Check index mapping
curl -X GET "localhost:9200/vehicle-location/_mapping?pretty"

# Verify cluster status
curl -X GET "localhost:9200/_cluster/health?pretty"
```

#### Performance Issues
- **High Latency**: Check Kafka producer batch settings
- **Memory Issues**: Monitor JVM heap usage
- **Missing Data**: Verify consumer group offsets

## 📈 Consumer Selection Guide

### When to Use Each Consumer

#### 🚀 Spring Boot Consumer
**Best for:**
- Development and testing environments
- When you need custom business logic in the consumer
- Small to medium workloads (< 5K vehicles)
- Teams comfortable with Java development

**Pros:**
- Full control over processing logic
- Easy integration with existing Spring ecosystem
- Good debugging and testing support
- Type-safe configuration

**Cons:**
- High memory usage (512MB+)
- Slower startup time
- Requires Java development skills
- More verbose configuration

#### ⚡ Logstash (Production Recommended)
**Best for:**
- Production environments with high throughput
- When you need data transformation and filtering
- Medium to large workloads (5K-20K vehicles)
- Teams preferring configuration over coding

**Pros:**
- Excellent performance (15K+ msg/s)
- Built-in data transformation capabilities
- Easy to scale horizontally
- Good monitoring and management tools

**Cons:**
- Medium memory usage (256MB)
- Ruby-based configuration learning curve
- Limited custom logic compared to code
- Requires Logstash expertise

#### 🪶 Fluentd (Resource-Constrained)
**Best for:**
- Resource-constrained environments
- Edge computing or IoT gateways
- Large workloads with limited resources (20K+ vehicles)
- Cloud-native deployments

**Pros:**
- Very low memory usage (50MB)
- Fast startup time
- Excellent for containerized environments
- Wide plugin ecosystem

**Cons:**
- XML configuration can be complex
- Less mature Elasticsearch plugin than Logstash
- Steeper learning curve for advanced features
- Limited built-in monitoring

### Configuration Comparison

| Feature | Spring Boot | Logstash | Fluentd |
|---------|-------------|----------|---------|
| **Data Transformation** | Java code | Ruby filters | Ruby plugins |
| **Error Handling** | Exception handling | Dead letter queue | Retry mechanisms |
| **Monitoring** | Spring Actuator | Logstash API | Fluentd UI |
| **Scaling** | Multiple instances | Pipeline workers | Worker processes |
| **Deployment** | JAR/Container | Container/Binary | Container/Binary |

### Migration Path

1. **Start with Spring Boot** for development
2. **Move to Logstash** for production scaling
3. **Consider Fluentd** for resource optimization

## 🔧 Implementation Examples

### Complete Logstash Configuration for 10K Vehicles

```ruby
# logstash-10k.conf
input {
  kafka {
    bootstrap_servers => "localhost:9092"
    topics => ["vehicle-location"]
    group_id => "logstash-10k-consumer"
    consumer_threads => 8
    fetch_max_bytes => 2097152
    max_poll_records => 1000
    session_timeout_ms => 300000
    max_partition_fetch_bytes => 1048576
    codec => "json"
  }
}

filter {
  # Convert timestamp
  if [timestamp] {
    date {
      match => [ "timestamp", "UNIX_MS" ]
      target => "@timestamp"
    }
  }
  
  # Geo-point validation
  if [location] and [location][lat] and [location][lon] {
    mutate {
      convert => { "[location][lat]" => "float" }
      convert => { "[location][lon]" => "float" }
    }
    
    # Validate coordinate ranges
    if ([location][lat] < -90 or [location][lat] > 90) or 
       ([location][lon] < -180 or [location][lon] > 180) {
      drop { }
    }
  }
  
  # Add processing metadata
  mutate {
    add_field => { 
      "[@metadata][processed_at]" => "%{+ISO8601}"
      "[@metadata][consumer]" => "logstash"
    }
  }
}

output {
  elasticsearch {
    hosts => ["localhost:9200"]
    index => "vehicle-location-%{+YYYY.MM.dd}"
    document_type => "_doc"
    template_name => "vehicle-location"
    template_pattern => "vehicle-location-*"
    template => {
      "index_patterns" => ["vehicle-location-*"],
      "settings" => {
        "number_of_shards" => 3,
        "number_of_replicas" => 1,
        "refresh_interval" => "30s"
      },
      "mappings" => {
        "properties" => {
          "vehicleId" => { "type" => "keyword" },
          "location" => { "type" => "geo_point" },
          "timestamp" => { "type" => "date" }
        }
      }
    }
    template_overwrite => true
    flush_size => 500
    idle_flush_time => 5
  }
  
  # Performance monitoring
  if [@metadata][processed_at] {
    stdout {
      codec => "dots"
    }
  }
}
```

### Complete Fluentd Configuration for 10K Vehicles

```xml
<!-- fluentd-10k.conf -->
<source>
  @type kafka
  brokers localhost:9092
  topics vehicle-location
  consumer_group fluentd-10k-consumer
  format json
  @id kafka_input
  max_bytes 2097152
  max_messages 1000
  fetch_interval 1
  offset_commit_interval 5
</source>

<filter vehicle-location>
  @type record_transformer
  <record>
    timestamp ${Time.at(record["timestamp"].to_f / 1000).utc}
  </record>
</filter>

<filter vehicle-location>
  @type geoip_filter
  geoip_lookup_keys location.lon,location.lat
  skip_adding_null_record true
  backend_library geoip2_cities
</filter>

<match vehicle-location>
  @type elasticsearch
  host localhost
  port 9200
  index_name vehicle-location
  type_name _doc
  include_timestamp true
  <buffer>
    @type file
    path /var/log/fluentd-buffers/vehicle.buffer
    flush_mode interval
    flush_interval 1s
    retry_type exponential_backoff
    flush_thread_count 4
    retry_forever
    retry_max_interval 30
    chunk_limit_size 10M
    queue_limit_length 64
    overflow_action block
  </buffer>
</match>
```

## 🎯 Use Cases

This pipeline is ideal for:
- **Fleet Management**: Real-time vehicle tracking (10K+ vehicles)
- **Logistics**: Package delivery monitoring with high throughput
- **Public Transport**: Bus/train tracking systems with resource constraints
- **Ride-sharing**: Uber/Lyft-style applications requiring low latency
- **IoT Sensors**: Any geo-location sensor data processing

## 📚 API Reference

### REST Endpoints (Optional Extensions)

```java
// Add to VehicleSimulatorApplication for monitoring
@RestController
public class MonitoringController {
    
    @GetMapping("/api/vehicles/count")
    public ResponseEntity<Long> getVehicleCount() {
        return ResponseEntity.ok(vehicleSimulatorService.getVehicleCount());
    }
    
    @GetMapping("/api/coordinates/recent")
    public ResponseEntity<List<Coordinate>> getRecentCoordinates() {
        return ResponseEntity.ok(coordinateRepository.findTop100ByOrderByTimestampDesc());
    }
}
```

## 🤝 Contributing

1. Fork the repository
2. Create feature branch
3. Add tests for new functionality
4. Submit pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙋‍♂️ Support

For questions and support:
- Create an issue in the repository
- Check the troubleshooting section
- Review the application logs

---

**Built with ❤️ using Spring Boot, Apache Kafka, and Elasticsearch**