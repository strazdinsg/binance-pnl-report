package no.strazdins.testtools;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import no.strazdins.data.AccountType;
import no.strazdins.data.Decimal;
import no.strazdins.data.Operation;
import no.strazdins.data.RawAccountChange;
import no.strazdins.data.WalletSnapshot;
import no.strazdins.transaction.CoinToCoinTransaction;
import no.strazdins.transaction.Transaction;

/**
 * A context for temporary storing data for Coin-to-coin tests, and running the tests.
 */
public class CoinToCoinContext {
  private final WalletSnapshot startSnapshot;
  private String buyAsset;
  private final List<String> buyAmounts = new ArrayList<>();
  private String sellAsset;
  private final List<String> sellAmounts = new ArrayList<>();
  private String feeAsset;
  private final List<String> feeAmounts = new ArrayList<>();

  /**
   * Create test for coin-to-coin transaction.
   *
   * @param startSnapshot The starting wallet snapshot, before the transaction
   */
  public CoinToCoinContext(WalletSnapshot startSnapshot) {
    this.startSnapshot = startSnapshot;
  }

  /**
   * Register the buying asset and it's amounts.
   *
   * @param asset   The asset being bought in this transaction
   * @param amounts The amounts of individual raw changes
   * @return This transaction, for chained calls
   */
  public CoinToCoinContext buy(String asset, String... amounts) {
    buyAsset = asset;
    buyAmounts.addAll(Arrays.asList(amounts));
    return this;
  }

  /**
   * Register the selling asset and it's amounts.
   *
   * @param asset   The asset being sold in this transaction
   * @param amounts The amounts of individual raw changes
   * @return This transaction, for chained calls
   */
  public CoinToCoinContext sell(String asset, String... amounts) {
    sellAsset = asset;
    sellAmounts.addAll(Arrays.asList(amounts));
    return this;
  }

  /**
   * Register the fee asset and it's amounts.
   *
   * @param asset   The asset being used for fees in this transaction
   * @param amounts The amounts of individual raw changes
   * @return This transaction, for chained calls
   */
  public CoinToCoinContext fees(String asset, String... amounts) {
    feeAsset = asset;
    feeAmounts.addAll(Arrays.asList(amounts));
    return this;
  }

  /**
   * Process the buy/sell transaction, based on cached buy-amounts, sell-amounts and fee-amounts.
   *
   * @return WalletSnapshot after processing the buy/sell transaction
   */
  public WalletSnapshot process() {
    long time = System.currentTimeMillis();
    Transaction t = new Transaction(time);
    for (String buyAmount : buyAmounts) {
      t.append(new RawAccountChange(time, AccountType.SPOT, Operation.BUY, buyAsset,
          new Decimal(buyAmount), ""));
    }
    for (String sellAmount : sellAmounts) {
      t.append(new RawAccountChange(time, AccountType.SPOT, Operation.SELL, sellAsset,
          new Decimal(sellAmount), ""));
    }
    for (String feeAmount : feeAmounts) {
      t.append(new RawAccountChange(time, AccountType.SPOT, Operation.FEE, feeAsset,
          new Decimal(feeAmount), ""));
    }
    Transaction coinToCoin = t.clarifyTransactionType();
    assertNotNull(coinToCoin, "Transaction type can't be clarified");
    assertInstanceOf(CoinToCoinTransaction.class, coinToCoin);

    return coinToCoin.process(startSnapshot, null);
  }
}
