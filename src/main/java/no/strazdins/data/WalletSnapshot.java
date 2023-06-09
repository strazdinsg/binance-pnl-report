package no.strazdins.data;

import java.util.Objects;
import no.strazdins.tool.TimeConverter;
import no.strazdins.transaction.Transaction;

/**
 * A snapshot of the wallet.
 */
public class WalletSnapshot {
  private final Transaction transaction;
  private Wallet wallet;
  private Decimal pnl;

  /**
   * Create a wallet snapshot.
   *
   * @param transaction The transaction after which this snapshot is created
   * @param pnl         Total running Profit & Loss (PNL) accumulated so far
   */
  public WalletSnapshot(Transaction transaction, Decimal pnl) {
    this.transaction = transaction;
    this.wallet = new Wallet();
    this.pnl = pnl;
  }

  /**
   * Create a snapshot of an empty wallet.
   *
   * @return An empty-wallet snapshot
   */
  public static WalletSnapshot createEmpty() {
    return new WalletSnapshot(null, Decimal.ZERO);
  }

  /**
   * Create a new wallet transaction which has the same data as this, and is ready to be
   * used as a template for "snapshot after transaction t".
   *
   * @param transaction The transaction for which the new snapshot will be created
   * @return A snapshot - copy of the current one, with the given transaction
   */
  public WalletSnapshot prepareForTransaction(Transaction transaction) {
    WalletSnapshot ws = new WalletSnapshot(transaction, new Decimal(pnl));
    ws.wallet = new Wallet(this.wallet);
    return ws;
  }

  public void addAsset(String asset, Decimal amount, Decimal obtainPrice) {
    wallet.addAsset(asset, amount, obtainPrice);
  }

  /**
   * Decrease the amount of given asset in the wallet.
   *
   * @param asset  The asset to decrease
   * @param amount The decrease amount
   */
  public void decreaseAsset(String asset, Decimal amount) {
    wallet.decreaseAsset(asset, amount);
  }


  @Override
  public String toString() {
    return wallet.getAssetCount() + " assets, PNL=" + pnl.getNiceString()
        + (transaction != null ? (" after " + transaction) : "");
  }

  public Wallet getWallet() {
    return wallet;
  }

  /**
   * Get Profit & Loss (PNL), in USDT.
   *
   * @return PNL in USDT
   */
  public Decimal getPnl() {
    return pnl;
  }

  /**
   * Add a PNL of one transaction to the total running PNL.
   *
   * @param transactionPnl PNL of a single transaction
   */
  public void addPnl(Decimal transactionPnl) {
    pnl = pnl.add(transactionPnl);
  }

  /**
   * Get Unix timestamp when the snapshot was created.
   *
   * @return Unix timestamp, including milliseconds
   */
  public long getTimestamp() {
    return transaction.getUtcTime();
  }

  public Transaction getTransaction() {
    return transaction;
  }

  /**
   * Get the amount of the base currency accumulated in the wallet so far.
   *
   * @return The amount of the base currency (main currency of this transaction) accumulated
   */
  public Decimal getBaseCurrencyAmountInWallet() {
    return wallet.getAssetAmount(transaction.getBaseCurrency());
  }

  /**
   * Get the average purchase price (obtain price) of the main currency of this transaction.
   * The price is calculated over all the transactions so far.
   *
   * @return The average obtain-price of the main asset of this transaction
   *     or Decimal.ZERO if the asset is not found in the wallet
   */
  public Decimal getAvgBaseObtainPrice() {
    return wallet.getAvgObtainPrice(transaction.getBaseCurrency());
  }

  /**
   * Get the average purchase price (obtain price) of the quote currency of this transaction.
   * The price is calculated over all the transactions so far.
   *
   * @return The average obtain-price of the quote currency of this transaction
   *     or Decimal.ZERO if the asset is not found in the wallet
   */
  public Decimal getAvgQuoteObtainPrice() {
    return wallet.getAvgObtainPrice(transaction.getQuoteCurrency());
  }

  /**
   * Get year of this snapshot, as an integer.
   *
   * @return The year, for example, 2023
   */
  public int getYear() {
    return TimeConverter.getUtcYear(getTimestamp());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WalletSnapshot snapshot = (WalletSnapshot) o;
    return Objects.equals(transaction, snapshot.transaction)
        && Objects.equals(wallet, snapshot.wallet)
        && Objects.equals(pnl, snapshot.pnl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(transaction, wallet, pnl);
  }

  /**
   * Get wallet difference between this and the old snapshot.
   *
   * @param oldSnapshot The old snapshot to compare against
   * @return The wallet difference: the change of amount for each asset
   */
  public WalletDiff getDiffFrom(WalletSnapshot oldSnapshot) {
    return wallet.getDiffFrom(oldSnapshot.wallet);
  }
}
