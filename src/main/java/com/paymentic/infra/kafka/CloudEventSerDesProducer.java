package com.paymentic.infra.kafka;

import static io.cloudevents.kafka.CloudEventSerializer.ENCODING_CONFIG;
import static io.cloudevents.kafka.CloudEventSerializer.EVENT_FORMAT_CONFIG;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.message.Encoding;
import io.cloudevents.jackson.JsonFormat;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.cloudevents.kafka.CloudEventSerializer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

@ApplicationScoped
public class CloudEventSerDesProducer {
  @Produces
  public Serde<CloudEvent> cloudEventSerde() {
    Map<String, Object> ceSerializerConfigs = new HashMap<>();
    ceSerializerConfigs.put(ENCODING_CONFIG, Encoding.BINARY);
    ceSerializerConfigs.put(EVENT_FORMAT_CONFIG, JsonFormat.CONTENT_TYPE);
    CloudEventSerializer serializer = new CloudEventSerializer();
    serializer.configure(ceSerializerConfigs, false);
    CloudEventDeserializer deserializer = new CloudEventDeserializer();
    deserializer.configure(ceSerializerConfigs, false);
    return Serdes.serdeFrom(serializer,deserializer);
  }

}
