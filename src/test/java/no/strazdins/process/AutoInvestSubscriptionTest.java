package no.strazdins.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import no.strazdins.data.Decimal;
import org.junit.jupiter.api.Test;

class AutoInvestSubscriptionTest {
  @Test
  void testInvalidWithoutInvestmentAmount() {
    assertThrows(IllegalArgumentException.class, () -> new AutoInvestSubscription(1000L, null));
  }

  @Test
  void testInvalidWithZeroOrNegativeInvestmentAmount() {
    assertThrows(IllegalArgumentException.class,
        () -> new AutoInvestSubscription(1000L, Decimal.ZERO));
    final Decimal minusTwo = new Decimal("-2");
    assertThrows(IllegalArgumentException.class, () -> new AutoInvestSubscription(1000L, minusTwo));
  }

  @Test
  void testInvalidWithoutProportions() {
    AutoInvestSubscription subscription = new AutoInvestSubscription(1000L, new Decimal("5"));
    assertFalse(subscription.isValid());
  }

  @Test
  void testInvalidWithProportionsLessThanOne() {
    AutoInvestSubscription subscription = new AutoInvestSubscription(1000L, new Decimal("5"));
    subscription.addAssetProportion("BTC", new Decimal("0.2"));
    subscription.addAssetProportion("LTC", new Decimal("0.2"));
    subscription.addAssetProportion("ETH", new Decimal("0.2"));
    assertFalse(subscription.isValid());

    subscription = new AutoInvestSubscription(1000L, new Decimal("5"));
    subscription.addAssetProportion("BTC", new Decimal("0.99999999"));
    assertFalse(subscription.isValid());
  }

  @Test
  void testInvalidWithProportionsGreaterThanOne() {
    AutoInvestSubscription subscription = new AutoInvestSubscription(1000L, new Decimal("5"));
    subscription.addAssetProportion("BTC", new Decimal("0.4"));
    subscription.addAssetProportion("LTC", new Decimal("0.4"));
    subscription.addAssetProportion("ETH", new Decimal("0.4"));
    assertFalse(subscription.isValid());

    subscription = new AutoInvestSubscription(1000L, new Decimal("5"));
    subscription.addAssetProportion("BTC", new Decimal("1.00000001"));
    assertFalse(subscription.isValid());
  }

  @Test
  void testCantAddSameProportionTwice() {
    AutoInvestSubscription subscription = new AutoInvestSubscription(1000L, new Decimal("5"));
    subscription.addAssetProportion("BTC", Decimal.ONE);
    assertThrows(IllegalArgumentException.class,
        () -> subscription.addAssetProportion("BTC", Decimal.ONE));
  }

  @Test
  void testValidConfigSingleCoin() {
    AutoInvestSubscription subscription = new AutoInvestSubscription(1000L, new Decimal("5"));
    subscription.addAssetProportion("BTC", Decimal.ONE);
    assertTrue(subscription.isValid());
  }

  @Test
  void testValidConfigMultipleCoins() {
    AutoInvestSubscription subscription = new AutoInvestSubscription(1000L, new Decimal("5"));
    subscription.addAssetProportion("BTC", new Decimal("0.5"));
    subscription.addAssetProportion("LTC", new Decimal("0.3"));
    subscription.addAssetProportion("ETH", new Decimal("0.2"));
    assertTrue(subscription.isValid());
  }

  @Test
  void testGetValidInvestmentForAsset() {
    AutoInvestSubscription subscription = new AutoInvestSubscription(1000L, new Decimal("5"));
    subscription.addAssetProportion("BTC", new Decimal("0.5"));
    subscription.addAssetProportion("LTC", new Decimal("0.3"));
    subscription.addAssetProportion("ETH", new Decimal("0.2"));
    assertEquals(new Decimal("2.5"), subscription.getInvestmentForAsset("BTC"));
    assertEquals(new Decimal("1.5"), subscription.getInvestmentForAsset("LTC"));
    assertEquals(new Decimal("1"), subscription.getInvestmentForAsset("ETH"));
  }

  @Test
  void testGetInvalidInvestmentForAsset() {
    // TODO - try subscription.getInvestmentForAsset for asset which is NOT configured
    AutoInvestSubscription subscription = new AutoInvestSubscription(1000L, new Decimal("5"));
    assertThrows(IllegalArgumentException.class, () -> subscription.getInvestmentForAsset("BTC"),
        "Invalid subscription, proportions not registered");
    subscription.addAssetProportion("BTC", new Decimal("0.5"));
    subscription.addAssetProportion("LTC", new Decimal("0.3"));
    assertThrows(IllegalArgumentException.class, () -> subscription.getInvestmentForAsset("BTC"),
        "Invalid subscription, proportion sum < 1");
    subscription.addAssetProportion("ETH", new Decimal("0.2"));
    assertThrows(IllegalArgumentException.class, () -> subscription.getInvestmentForAsset("BNB"),
        "Unknown coin");
  }
}
