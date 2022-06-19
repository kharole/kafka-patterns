package ua.kharole.snapshotpolling;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@EnableKafka
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic topBetsTopic() {
        return TopicBuilder.name("sportsbook.dev.rate-demo")
                .partitions(1)
                .replicas(2)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, "compact")
                .config(TopicConfig.SEGMENT_MS_CONFIG, "3600000")
                .build();
    }

}
