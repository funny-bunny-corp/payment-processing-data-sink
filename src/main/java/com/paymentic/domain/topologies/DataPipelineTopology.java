package com.paymentic.domain.topologies;

import io.cloudevents.CloudEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.List;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.TopicNameExtractor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DataPipelineTopology {

  @ConfigProperty(name = "quarkus.kafka-streams.topics")
  List<String> sourceTopic;

  @Produces
  public Topology totalOrderSellerPerDay(Serde<CloudEvent> ceSerde) {
    var builder = new StreamsBuilder();
    final KStream<String, CloudEvent> paymentProcessingStream = builder.stream(this.sourceTopic,
        Consumed.with(Serdes.String(), ceSerde));
    final TopicNameExtractor<String, CloudEvent> topicName = (key, value, recordContext) -> value.getType() + "-sink"  ;
    paymentProcessingStream.to(topicName, Produced.with(Serdes.String(), ceSerde));
    return builder.build();
  }
}
