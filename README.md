# Kafka Patterns
## Snapshot polling as compacted topic

Once started working with Kafka you might have noticed some mismatch between conventional data sources 
like REST services (data snapshot) and Kafka (data in motion). But what exactly could you do
to solve it?

Obviously REST service have no means to push changes to client, so you have to poll service periodically. 
This gives you series of snapshots but how to transform it to something native to Kafka? Let's take as 
example some key-value data (it can be currency with its rate, city and its temperature etc.). 
In terms of Kafka it can be implemented as a compacted topic. 
Of course, there is straight forward solution - just dump each snapshot 
to topic and let compaction strategy do the rest - remove duplicate keys and only leave the latest currency rate. 
Can be it is not enough. If you 
combine a dozen of different topics you may end up with the result that emitting values blindly at 
luridicous rate even though nothing have changed really (let say you have to rebuild whole goods catalogue 
with prices since currency on each rate change). So you may need to apply some kind of change detections to 
original topic.

One may say it is a trivial problem. A service with Kafka listener, HashMap along with some code that compares 
snapshot to the content map and only send the difference would solve it. We are on the right way! But what is the
map is pretty big, and you don't want every key get re-emitted on service restart. It would happen inevitably since 
on first comparison the in-mem map is empty hence the first diff is as big as whole snapshot. If it is not a big
deal for your app just ditch this article. Those who stay we are almost there. 

Obviously you have to store latest known snapshot somewhere to load it on start up. Bingo, Hibernate time! Hell no!
If you think of it for a minute the right answer will pop up. Our compacted topic is the latest known snapshot. At first
glance, it may look a bit messy to both read and write to the same topic, but hopefully once visualized it gets
more clear. Well known "impedance mismatch" problem was borrowed from electronics. Shall we look for a solution in the
same realm maybe? This particular designed inspired by
[voltage follower](http://www.learningaboutelectronics.com/Articles/Voltage-follower) circuit and using [negative 
feedback loops](https://en.wikipedia.org/wiki/Negative_feedback) in general.

![telegram-cloud-photo-size-2-5314377457744329937-y](https://user-images.githubusercontent.com/2360882/175809107-af5bef5c-7542-43fe-9ac5-9273922c52de.jpg)

If you have at least some basing experience with electronics (yes, sticking plug into socket count) then thinking of 
fluxes pumping data between topics, services and processing stages as wires connecting various electronic components
makes things much more clear. So what components and wires and wire do we have on our circuit:
- oscillator - interval flux
```
Flux.interval(Duration.of(30, SECONDS))
```
- source of signal - REST service
```
.concatMap(tick -> getCurrenciesMapMono())
```
- opamp - difference calculator
```
        var recordsToSendFlux = 
                Flux.merge(receiverFlux, currenciesFlux)
                .scan(CurrencyState.EMPTY, CurrencyState::apply)
                .skip(1)
                .map(CurrencyState::diff)
                .concatMap(diff -> fromStream(diff.entrySet().stream()))
                .map(this::asRecord);
```
- feedback loop - Kafka topic
```
        sender.send(recordsToSendFlux)
                .subscribe(result -> log.info("currency-rate: sent: {}", result.correlationMetadata()));

```
- bunch of probes - p1, p2, p3 attached to an oscilloscope - debugger

Now you can proceed with the most pleasureful part - attach the batter and let it run. But be careful and observe polarity.  
