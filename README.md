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
snapshot to the map and only send the difference would solve it. We are on the right way! But what is the map is pretty
big, and you don't want every key get re-emitted on service restart. It would happen inevitably since on first 
comparison the in-mem map is empty hence the first diff is as big as whole snapshot. If it is not a big deal for 
your app just ditch this article. Those who stay we are almost there. 

Obviously you have to store latest known snapshot somewhere to load it on start up. Bingo, Hibernate time! Hell no!
If you think of it for a minute the right answer will pop up. Our compacted topic is the latest known snapshot. At first
glance, it may look a bit messy to both read and write to the same topic, but hopefully once visualized it gets
more clear. Well known "impedance mismatch" problem was borrowed from electronics. Shall we look for a solution in the
same realm maybe? This particular designed inspired by voltage follower circuit and using negative feedback loops in
general.