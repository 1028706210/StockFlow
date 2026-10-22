package io.github.stockflow.order;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.stockflow.order.OrderLines.*;

class OrderLinesTest {
    @Test void mergesAndSortsWithoutChangingTheInput() {
        var input = new ArrayList<>(List.of(new Line(9, 2), new Line(2, 1), new Line(9, 3)));
        assertEquals(List.of(new Line(2, 1), new Line(9, 5)), normalize(input));
        assertEquals(3, input.size());
    }
    @Test void rejectsAnEmptyOrNullOrder() {
        assertThrows(IllegalArgumentException.class, () -> normalize(List.of()));
        assertThrows(IllegalArgumentException.class, () -> normalize(null));
    }
    @Test void enforcesTheMergedQuantityLimit() {
        assertEquals(List.of(new Line(1, 99)), normalize(List.of(new Line(1, 49), new Line(1, 50))));
        assertThrows(IllegalArgumentException.class, () -> normalize(List.of(new Line(1, 60), new Line(1, 40))));
    }
    @Test void rejectsInvalidSkuIdsQuantitiesAndNullLines() {
        for (var line : List.of(new Line(0, 1), new Line(-1, 1), new Line(1, 0), new Line(1, -1), new Line(1, 100)))
            assertThrows(IllegalArgumentException.class, () -> normalize(List.of(line)));
        assertThrows(IllegalArgumentException.class, () -> normalize(java.util.Arrays.asList((Line) null)));
    }
    @Test void capsDistinctSkusAfterMerging() {
        var ten = LongStream.rangeClosed(1, 10).mapToObj(id -> new Line(id, 1)).toList();
        assertEquals(10, normalize(ten).size());
        var eleven = LongStream.rangeClosed(1, 11).mapToObj(id -> new Line(id, 1)).toList();
        assertThrows(IllegalArgumentException.class, () -> normalize(eleven));
    }
    @Test void equivalentRequestsHaveTheSameFingerprintAndChangesDoNot() {
        var a = normalize(List.of(new Line(9, 2), new Line(2, 1), new Line(9, 3)));
        var b = normalize(List.of(new Line(9, 5), new Line(2, 1)));
        assertEquals(fingerprint(a), fingerprint(b));
        assertNotEquals(fingerprint(a), fingerprint(normalize(List.of(new Line(9, 4), new Line(2, 1)))));
        assertEquals(64, fingerprint(a).length());
    }
}
