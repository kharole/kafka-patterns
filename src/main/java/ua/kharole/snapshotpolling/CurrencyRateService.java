package ua.kharole.snapshotpolling;

import io.vavr.control.Either;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static org.pcollections.HashTreePMap.from;
import static org.slf4j.LoggerFactory.getLogger;
import static reactor.core.publisher.Flux.fromStream;

@Service
public class CurrencyRateService {

    private final Logger log = getLogger(getClass());

    private static final String topic = "sportsbook.dev.rate-demo";

    protected KafkaFactory kafkaFactory;

    protected KafkaSender<String, String> sender;

    private WebClient client;

    @PostConstruct
    public void init() {
        client = WebClient.builder().build();

        sender = kafkaFactory.sender(topic);

        var receiverFlux = kafkaFactory
                .receiver(topic)
                .receive()
                .map(this::left)
                .doOnNext(t -> log.info("currency-rate: received: {}", t.left().get()));

        var currenciesFlux =
                Flux.interval(Duration.of(30, SECONDS))
                .concatMap(tick -> getCurrenciesMapMono())
                .doOnNext(ms -> log.info("currency-rate: background update: size={}", ms.size()))
                .map(this::right);

        var recordsToSendFlux =
                Flux.merge(receiverFlux, currenciesFlux)
                .scan(CurrencyState.EMPTY, CurrencyState::apply)
                .skip(1)
                .map(CurrencyState::diff)
                .concatMap(diff -> fromStream(diff.entrySet().stream()))
                .map(this::asRecord);

        sender.send(recordsToSendFlux)
                .subscribe(result -> log.info("currency-rate: sent: {}", result.correlationMetadata()));
    }

    private Mono<PMap<String, BigDecimal>> getCurrenciesMapMono() {
        ParameterizedTypeReference<RatesData> typeRef = new ParameterizedTypeReference<>() {
        };
        return client.get()
                .uri("https://api.coinlore.net/api/tickers/")
                .retrieve()
                .bodyToMono(typeRef)
                .doFirst(() -> log.info("mono connected"))
                .doOnError(ex -> log.error("mono error {}", ex.getMessage()))
                .doOnTerminate(() -> log.info("mono disconnected"))
                .map(ratesData -> from(ratesData.getData().stream().collect(toMap(r -> r.getSymbol(), r -> r.getPrice_usd()))));
    }

    private SenderRecord<String, String, Object> asRecord(Map.Entry<String, BigDecimal> entry) {
        var value = entry.getValue() == null ? null : entry.getValue().toString();
        return SenderRecord.create(new ProducerRecord<>(topic, entry.getKey(), value),
                entry.getKey() + "->" + entry.getValue());
    }

    private Either<PMap<String, BigDecimal>, PMap<String, BigDecimal>> left(ReceiverRecord<String, String> record) {
        return Either.left(HashTreePMap.<String, BigDecimal>empty().plus(record.key(), new BigDecimal(record.value())));
    }

    private Either<PMap<String, BigDecimal>, PMap<String, BigDecimal>> right(PMap<String, BigDecimal> m) {
        return Either.right(m);
    }

    @Autowired
    public void setKafkaFactory(KafkaFactory kafkaFactory) {
        this.kafkaFactory = kafkaFactory;
    }
}
