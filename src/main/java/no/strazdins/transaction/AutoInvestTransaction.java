package no.strazdins.transaction;

import no.strazdins.data.Operation;
import no.strazdins.data.RawAccountChange;
import no.strazdins.process.AutoInvestSubscription;
import no.strazdins.tool.TimeConverter;

/**
 * Auto-invest transaction. It is a bit special, because its parts (operations) can have
 * different timestamps. First the money is spent (USD), then individual coins are bought at a
 * later time.
 */
public class AutoInvestTransaction extends Transaction {
  private AutoInvestSubscription subscription;

  public AutoInvestTransaction(Transaction transaction, AutoInvestSubscription subscription) {
    super(transaction);
    this.subscription = subscription;
  }

  /**
   * Check whether the raw change represents part of an auto-invest operation.
   *
   * @param change The change to check
   * @return True if it belongs to an auto-invest operation
   */
  public static boolean isAutoInvestOperation(RawAccountChange change) {
    return change.getOperation().equals(Operation.AUTO_INVEST);
  }

  /**
   * Get the asset bought in this transaction.
   *
   * @return The bought asset or null if no asset was bought (acquired) here
   */
  public String getBoughtAsset() {
    String boughtAsset = null;
    RawAccountChange invest = getFirstChangeOfType(Operation.AUTO_INVEST);
    if (invest != null && invest.getAmount().isPositive()) {
      boughtAsset = invest.getAsset();
    }
    return boughtAsset;
  }

  public void setSubscription(AutoInvestSubscription subscription) {
    this.subscription = subscription;
  }

  @Override
  public String getType() {
    return "Auto-invest";
  }

  @Override
  public String toString() {
    RawAccountChange op = getFirstChangeOfType(Operation.AUTO_INVEST);
    String opDetails = op != null ? (op.getAmount().getNiceString() + " " + op.getAsset()) : "";
    return "Auto-Invest " + opDetails + " @ "
        + TimeConverter.utcTimeToString(utcTime);
  }
  // TODO - if invest and acquire (USDT and coin) in the same second - throw error
}