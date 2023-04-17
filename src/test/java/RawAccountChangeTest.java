import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import no.strazdins.data.AccountType;
import no.strazdins.data.Decimal;
import no.strazdins.data.Operation;
import no.strazdins.data.RawAccountChange;
import org.junit.jupiter.api.Test;

class RawAccountChangeTest {
  @Test
  void testMergeDifferentOpTypes() {
    long thisTime = System.currentTimeMillis();
    final List<RawAccountChange> changes = new LinkedList<>();
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.SELL, "BTC", Decimal.ONE, ""));
    assertThrows(IllegalArgumentException.class, () -> RawAccountChange.merge(changes));

    changes.clear();
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.SELL, "BTC", Decimal.ONE, ""));
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    assertThrows(IllegalArgumentException.class, () -> RawAccountChange.merge(changes));

    changes.clear();
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.SELL, "BTC", Decimal.ONE, ""));
    assertThrows(IllegalArgumentException.class, () -> RawAccountChange.merge(changes));

    changes.clear();
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.SELL, "BTC", Decimal.ONE, ""));
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    assertThrows(IllegalArgumentException.class, () -> RawAccountChange.merge(changes));
  }

  @Test
  void testMergeDifferentOpAssets() {
    long thisTime = System.currentTimeMillis();

    final List<RawAccountChange> changes = new LinkedList<>();
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "LTC", Decimal.ONE, ""));
    assertThrows(IllegalArgumentException.class, () -> RawAccountChange.merge(changes));

    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    assertThrows(IllegalArgumentException.class, () -> RawAccountChange.merge(changes));

    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    assertThrows(IllegalArgumentException.class, () -> RawAccountChange.merge(changes));

    changes.add(0, new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "LTC", Decimal.ONE, ""));
    assertThrows(IllegalArgumentException.class, () -> RawAccountChange.merge(changes));
  }

  @Test
  void testMergeEmptyList() {
    List<RawAccountChange> emptyList = new LinkedList<>();
    assertThrows(IllegalArgumentException.class, () -> RawAccountChange.merge(emptyList));
  }

  @Test
  void testMergeNull() {
    assertThrows(IllegalArgumentException.class, () -> RawAccountChange.merge(null));
  }

  @Test
  void testMergeOne() {
    long thisTime = System.currentTimeMillis();
    RawAccountChange originalChange = new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, "");
    RawAccountChange merged = RawAccountChange.merge(Collections.singletonList(originalChange));
    assertEquals(originalChange, merged);
  }

  @Test
  void testMergeTwo() {
    long thisTime = System.currentTimeMillis();
    List<RawAccountChange> originalChanges = new LinkedList<>();
    originalChanges.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("0.1"), ""));
    originalChanges.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("0.2"), ""));

    RawAccountChange merged = RawAccountChange.merge(originalChanges);
    RawAccountChange expected = new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("0.3"), "");
    assertEquals(expected, merged);
  }


  @Test
  void testMergeMultiple() {
    long thisTime = System.currentTimeMillis();
    List<RawAccountChange> originalChanges = new LinkedList<>();
    originalChanges.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("0.1"), ""));
    originalChanges.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("0.2"), ""));
    originalChanges.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("0.3"), ""));
    originalChanges.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("0.4"), ""));

    RawAccountChange merged = RawAccountChange.merge(originalChanges);
    RawAccountChange expected = new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("1.0"), "");
    assertEquals(expected, merged);
  }

  @Test
  void testMergeNegative() {
    long thisTime = System.currentTimeMillis();
    List<RawAccountChange> originalChanges = new LinkedList<>();
    originalChanges.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("0.1"), ""));
    originalChanges.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("-0.2"), ""));

    RawAccountChange merged = RawAccountChange.merge(originalChanges);
    RawAccountChange expected = new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("-0.1"), "");
    assertEquals(expected, merged);

    originalChanges.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("0.5"), ""));
    originalChanges.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("-0.1"), ""));

    merged = RawAccountChange.merge(originalChanges);
    expected = new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", new Decimal("0.3"), "");
    assertEquals(expected, merged);
  }

  @Test
  void testMergeDifferentTimestamps() {
    long thisTime = System.currentTimeMillis();
    final List<RawAccountChange> changes = new LinkedList<>();
    changes.add(new RawAccountChange(thisTime, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    changes.add(new RawAccountChange(thisTime + 1000, AccountType.SPOT,
        Operation.BUY, "BTC", Decimal.ONE, ""));
    assertThrows(IllegalArgumentException.class, () -> RawAccountChange.merge(changes));
  }
}
