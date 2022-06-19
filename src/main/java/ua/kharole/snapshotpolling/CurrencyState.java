package ua.kharole.snapshotpolling;

import io.vavr.control.Either;
import org.pcollections.PMap;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.pcollections.HashTreePMap.empty;

public record CurrencyState(PMap<String, BigDecimal> snapshot, PMap<String, BigDecimal> diff) {

    public static CurrencyState EMPTY = new CurrencyState(empty(), empty());

    public CurrencyState apply(Either<PMap<String, BigDecimal>, PMap<String, BigDecimal>> diffOrSnapshot) {
        if (diffOrSnapshot.isLeft()) {
            return applyDiff(diffOrSnapshot.left().get());
        } else {
            return applySnapshot(diffOrSnapshot.right().get());
        }
    }

    public CurrencyState applyDiff(PMap<String, BigDecimal> diff) {
        var toDelete = diff.entrySet().stream()
                .filter(e -> e.getValue() == null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        var toAdd = diff.minusAll(toDelete);
        return new CurrencyState(snapshot().minusAll(toDelete).plusAll(toAdd), empty());
    }

    public CurrencyState applySnapshot(PMap<String, BigDecimal> newSnapshot) {
        var toAdd = newSnapshot.minusAll(snapshot().keySet());
        var toUpdate = newSnapshot.entrySet().stream()
                .filter(entry -> snapshot.containsKey(entry.getKey()))
                .filter(entry -> !snapshot.get(entry.getKey()).equals(entry.getValue()))
                .collect(HashMap<String, BigDecimal>::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
        var toDelete = snapshot().minusAll(newSnapshot.keySet())
                .entrySet().stream()
                .collect(HashMap<String, BigDecimal>::new, (m, e) -> m.put(e.getKey(), null), HashMap::putAll);
        return new CurrencyState(snapshot(), toAdd.plusAll(toUpdate).plusAll(toDelete));
    }

}
