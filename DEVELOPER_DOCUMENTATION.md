# Payment Processing Data Sink - Developer Documentation

## 1. OVERVIEW

### Purpose and Primary Functionality

The Payment Processing Data Sink is a **Quarkus-based Kafka Streams application** that serves as a data routing and transformation component in the payment processing ecosystem. This module:

- **Consumes CloudEvents** from configured Kafka topics
- **Routes events** to appropriate destination topics based on event type
- **Provides event serialization/deserialization** using CloudEvents standard
- **Processes streaming data** in real-time for payment processing workflows

### When to Use This Component vs. Alternatives

**Use this component when:**
- You need to route payment events to different downstream systems based on event type
- Your architecture follows CloudEvents specification for event-driven communication
- You require real-time stream processing with Kafka Streams
- You need a lightweight, cloud-native solution with fast startup times (Quarkus)

**Alternative approaches:**
- **Apache Camel** - For complex routing and transformation scenarios
- **Spring Cloud Stream** - For Spring-based ecosystems
- **Direct Kafka Producer/Consumer** - For simple point-to-point messaging
- **Event Sourcing frameworks** - For event store and replay capabilities

### Architectural Context

```
┌─────────────────┐    ┌──────────────────────────┐    ┌─────────────────┐
│   Payment       │    │  Payment Processing      │    │  Downstream     │
│   Services      │───▶│  Data Sink               │───▶│  Services       │
│                 │    │  (This Module)           │    │                 │
└─────────────────┘    └──────────────────────────┘    └─────────────────┘
                                     │
                                     ▼
                              ┌──────────────┐
                              │    Kafka     │
                              │   Cluster    │
                              └──────────────┘
```

This module sits between payment processing services and downstream consumers, acting as an intelligent event router that:
- Receives events from upstream payment services
- Routes events to topic-specific sinks based on event type
- Maintains event integrity using CloudEvents standard

## 2. TECHNICAL SPECIFICATION

### API Reference

#### Core Components

##### `DataPipelineTopology`
**Location:** `com.paymentic.domain.topologies.DataPipelineTopology`

**Purpose:** Defines the Kafka Streams topology for event processing and routing.

**Methods:**

| Method | Parameters | Return Type | Description |
|--------|------------|-------------|-------------|
| `totalOrderSellerPerDay()` | `Serde<CloudEvent> ceSerde` | `Topology` | Creates the main processing topology |

**Configuration Properties:**
- `quarkus.kafka-streams.topics`: List of source topics to consume from

**Stream Processing Logic:**
```java
// Consumes from configured source topics
KStream<String, CloudEvent> paymentProcessingStream = builder.stream(sourceTopic, Consumed.with(Serdes.String(), ceSerde));

// Routes to destination topics based on event type
TopicNameExtractor<String, CloudEvent> topicName = (key, value, recordContext) -> value.getType() + "-sink";
paymentProcessingStream.to(topicName, Produced.with(Serdes.String(), ceSerde));
```

##### `CloudEventSerDesProducer`
**Location:** `com.paymentic.infra.kafka.CloudEventSerDesProducer`

**Purpose:** Provides CloudEvent serialization/deserialization configuration.

**Methods:**

| Method | Parameters | Return Type | Description |
|--------|------------|-------------|-------------|
| `cloudEventSerde()` | None | `Serde<CloudEvent>` | Produces configured CloudEvent Serde |

**Configuration:**
- **Encoding:** Binary encoding for CloudEvents
- **Format:** Jackson JSON format
- **Serializer:** CloudEventSerializer with binary encoding
- **Deserializer:** CloudEventDeserializer with binary encoding

### Parameters and Configuration

#### Environment Variables

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `BOOTSTRAP_SERVERS` | Yes | Kafka cluster bootstrap servers | `localhost:9092` |
| `SOURCE_TOPIC` | Yes | Comma-separated source topics | `payment-events,order-events` |

#### Application Properties

| Property | Default | Description |
|----------|---------|-------------|
| `kafka-streams.cache.max.bytes.buffering` | `10240` | Stream caching buffer size |
| `kafka-streams.commit.interval.ms` | `1000` | Commit interval for stream processing |
| `kafka-streams.metadata.max.age.ms` | `500` | Metadata refresh interval |
| `kafka-streams.auto.offset.reset` | `earliest` | Offset reset strategy |
| `kafka-streams.metrics.recording.level` | `INFO` | Metrics recording level |
| `quarkus.http.port` | `6666` | HTTP server port |

### State Management

- **Stateless Processing:** Current implementation is stateless
- **No State Stores:** Does not maintain local state stores
- **Event Routing:** Pure event routing without aggregation or windowing

### Events Emitted/Listened For

#### Input Events (CloudEvents)
- **Source:** Configured source topics (`SOURCE_TOPIC`)
- **Format:** CloudEvents with binary encoding
- **Key:** String (typically event ID or correlation ID)
- **Value:** CloudEvent object

#### Output Events
- **Destination:** Dynamic topic based on event type (`{eventType}-sink`)
- **Format:** Same as input (CloudEvents with binary encoding)
- **Routing Logic:** `event.getType() + "-sink"`

**Example Routing:**
- Input event type: `payment.processed` → Output topic: `payment.processed-sink`
- Input event type: `order.created` → Output topic: `order.created-sink`

## 3. IMPLEMENTATION EXAMPLES

### Basic Usage Example

#### 1. Environment Setup
```bash
# Set required environment variables
export BOOTSTRAP_SERVERS=localhost:9092
export SOURCE_TOPIC=payment-events

# Run in development mode
./mvnw compile quarkus:dev
```

#### 2. Sending Test Events
```bash
# Send a CloudEvent to the source topic
kafka-console-producer.sh --broker-list localhost:9092 --topic payment-events --property "parse.key=true" --property "key.separator=:"

# Example message (key:value format)
payment-123:{"specversion":"1.0","type":"payment.processed","source":"payment-service","id":"payment-123","time":"2023-01-01T12:00:00Z","datacontenttype":"application/json","data":{"amount":100.00,"currency":"USD"}}
```

#### 3. Verify Output
```bash
# Check output topic
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic payment.processed-sink --from-beginning
```

### Advanced Configuration Example

#### Custom Application Properties
```properties
# application.properties
kafka.bootstrap.servers=kafka-cluster:9092
kafka-streams.cache.max.bytes.buffering=20480
kafka-streams.commit.interval.ms=500
kafka-streams.metadata.max.age.ms=300
kafka-streams.auto.offset.reset=latest
kafka-streams.metrics.recording.level=DEBUG
quarkus.kafka-streams.topics=payment-events,order-events,refund-events
quarkus.kafka-streams.bootstrap-servers=kafka-cluster:9092
quarkus.http.port=8080

# Additional Kafka Streams configuration
kafka-streams.num.stream.threads=4
kafka-streams.processing.guarantee=exactly_once
kafka-streams.replication.factor=3
```

#### Docker Compose Setup
```yaml
# docker-compose.yml
version: '3.8'
services:
  payment-data-sink:
    image: payment-processing-data-sink:latest
    environment:
      BOOTSTRAP_SERVERS: kafka:9092
      SOURCE_TOPIC: payment-events,order-events
    depends_on:
      - kafka
    ports:
      - "8080:8080"
  
  kafka:
    image: confluentinc/cp-kafka:latest
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    depends_on:
      - zookeeper
```

### Customization Scenarios

#### 1. Custom Event Routing Logic
```java
@ApplicationScoped
public class CustomDataPipelineTopology {
    
    @Produces
    public Topology customRouting(Serde<CloudEvent> ceSerde) {
        var builder = new StreamsBuilder();
        
        KStream<String, CloudEvent> stream = builder.stream(sourceTopic, 
            Consumed.with(Serdes.String(), ceSerde));
        
        // Custom routing based on event source and type
        final TopicNameExtractor<String, CloudEvent> customTopicName = 
            (key, value, recordContext) -> {
                String source = value.getSource().toString();
                String type = value.getType();
                return source.replace("://", "_") + "_" + type + "_sink";
            };
        
        stream.to(customTopicName, Produced.with(Serdes.String(), ceSerde));
        return builder.build();
    }
}
```

#### 2. Event Filtering
```java
@Produces
public Topology filteringTopology(Serde<CloudEvent> ceSerde) {
    var builder = new StreamsBuilder();
    
    KStream<String, CloudEvent> stream = builder.stream(sourceTopic, 
        Consumed.with(Serdes.String(), ceSerde));
    
    // Filter events based on criteria
    stream
        .filter((key, event) -> event.getType().startsWith("payment."))
        .filter((key, event) -> event.getSource().toString().contains("payment-service"))
        .to(topicName, Produced.with(Serdes.String(), ceSerde));
    
    return builder.build();
}
```

### Common Patterns and Best Practices

#### 1. Error Handling
```java
@Produces
public Topology errorHandlingTopology(Serde<CloudEvent> ceSerde) {
    var builder = new StreamsBuilder();
    
    KStream<String, CloudEvent> stream = builder.stream(sourceTopic, 
        Consumed.with(Serdes.String(), ceSerde));
    
    // Split stream for error handling
    KStream<String, CloudEvent>[] branches = stream.branch(
        (key, event) -> isValidEvent(event),  // Valid events
        (key, event) -> true                  // Invalid events (catch-all)
    );
    
    // Process valid events
    branches[0].to(topicName, Produced.with(Serdes.String(), ceSerde));
    
    // Send invalid events to error topic
    branches[1].to("error-events", Produced.with(Serdes.String(), ceSerde));
    
    return builder.build();
}
```

#### 2. Monitoring and Metrics
```java
// Add custom metrics
@Produces
public Topology monitoredTopology(Serde<CloudEvent> ceSerde) {
    var builder = new StreamsBuilder();
    
    KStream<String, CloudEvent> stream = builder.stream(sourceTopic, 
        Consumed.with(Serdes.String(), ceSerde));
    
    // Add processing metrics
    stream
        .peek((key, event) -> {
            // Log processing metrics
            log.info("Processing event: type={}, source={}", 
                event.getType(), event.getSource());
        })
        .to(topicName, Produced.with(Serdes.String(), ceSerde));
    
    return builder.build();
}
```

## 4. TROUBLESHOOTING

### Common Errors and Solutions

#### 1. **Kafka Connection Issues**
```
ERROR: Connection to node -1 could not be established
```
**Solution:**
- Verify `BOOTSTRAP_SERVERS` environment variable
- Check Kafka cluster availability
- Ensure network connectivity to Kafka brokers

#### 2. **Topic Does Not Exist**
```
ERROR: Topic 'payment-events' does not exist
```
**Solution:**
```bash
# Create topic manually
kafka-topics.sh --create --topic payment-events --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

#### 3. **Serialization Errors**
```
ERROR: Failed to deserialize CloudEvent
```
**Solution:**
- Verify CloudEvent format in source messages
- Check CloudEvent headers and structure
- Ensure binary encoding is used consistently

#### 4. **Application Won't Start**
```
ERROR: Could not create ApplicationContext
```
**Solution:**
- Check all required environment variables are set
- Verify application.properties configuration
- Ensure no port conflicts (default port 6666)

### Debugging Strategies

#### 1. Enable Debug Logging
```properties
# application.properties
quarkus.log.category."com.paymentic".level=DEBUG
quarkus.log.category."org.apache.kafka".level=DEBUG
kafka-streams.metrics.recording.level=DEBUG
```

#### 2. Monitor Stream Processing
```bash
# Check Kafka consumer group
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group payment-processing-data-sink

# Monitor topic contents
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic payment-events --from-beginning
```

#### 3. Health Checks
```bash
# Check application health
curl http://localhost:6666/q/health

# Check Kafka Streams state
curl http://localhost:6666/q/health/ready
```

### Performance Considerations

#### 1. **Throughput Optimization**
```properties
# Increase stream threads
kafka-streams.num.stream.threads=4

# Increase batch size
kafka-streams.batch.size=16384

# Optimize caching
kafka-streams.cache.max.bytes.buffering=20480
```

#### 2. **Memory Management**
```properties
# JVM memory settings
quarkus.native.additional-build-args=-J-Xmx2g,-J-Xms1g

# Kafka Streams memory
kafka-streams.buffered.records.per.partition=1000
```

#### 3. **Network Optimization**
```properties
# Reduce network calls
kafka-streams.commit.interval.ms=5000
kafka-streams.metadata.max.age.ms=30000
```

## 5. RELATED COMPONENTS

### Dependencies

#### Core Dependencies
- **Quarkus Platform** (`3.7.1`) - Application framework
- **Kafka Streams** - Stream processing engine
- **CloudEvents** (`2.5.0`) - Event specification and serialization
- **Jackson** - JSON processing for CloudEvents

#### Maven Dependencies
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-kafka-streams</artifactId>
</dependency>
<dependency>
    <groupId>io.cloudevents</groupId>
    <artifactId>cloudevents-kafka</artifactId>
    <version>2.5.0</version>
</dependency>
```

### Components Commonly Used Alongside

#### 1. **Upstream Components**
- **Payment Processing Services** - Generate payment events
- **Order Management Systems** - Generate order events
- **API Gateway** - Routes requests to payment services

#### 2. **Downstream Components**
- **Payment Analytics Service** - Consumes `payment.*-sink` topics
- **Notification Service** - Consumes event notifications
- **Audit Service** - Consumes all events for compliance
- **Data Warehouse** - Consumes events for analytics

#### 3. **Infrastructure Components**
- **Kafka Cluster** - Message broker
- **Schema Registry** - Event schema management
- **Monitoring Stack** - Prometheus, Grafana for metrics
- **Service Discovery** - Consul or Eureka for service registration

### Alternative Approaches

#### 1. **Apache Camel Integration**
```java
// Alternative using Camel routes
@Component
public class PaymentEventRouter extends RouteBuilder {
    @Override
    public void configure() {
        from("kafka:payment-events")
            .choice()
                .when(header("eventType").isEqualTo("payment.processed"))
                    .to("kafka:payment-processed-sink")
                .when(header("eventType").isEqualTo("order.created"))
                    .to("kafka:order-created-sink")
            .end();
    }
}
```

#### 2. **Spring Cloud Stream**
```java
@StreamListener("input")
@SendTo("output")
public CloudEvent processEvent(CloudEvent event) {
    // Route to appropriate topic
    return event;
}
```

#### 3. **Direct Kafka Producer Pattern**
```java
@Service
public class EventRouter {
    @Autowired
    private KafkaTemplate<String, CloudEvent> kafkaTemplate;
    
    public void routeEvent(CloudEvent event) {
        String targetTopic = event.getType() + "-sink";
        kafkaTemplate.send(targetTopic, event);
    }
}
```

---

## Quick Start Guide

1. **Set Environment Variables:**
   ```bash
   export BOOTSTRAP_SERVERS=localhost:9092
   export SOURCE_TOPIC=payment-events
   ```

2. **Start the Application:**
   ```bash
   ./mvnw compile quarkus:dev
   ```

3. **Send Test Event:**
   ```bash
   # Use Kafka console producer to send CloudEvent
   ```

4. **Verify Routing:**
   ```bash
   # Check output topics for routed events
   ```

For more detailed information, refer to the specific sections above or consult the [Quarkus Kafka Streams Guide](https://quarkus.io/guides/kafka-streams).