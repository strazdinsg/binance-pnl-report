package no.strazdins.transaction;

import java.util.Objects;
import no.strazdins.data.Decimal;
import no.strazdins.data.ExtraInfoEntry;
import no.strazdins.data.Operation;
import no.strazdins.data.RawAccountChange;
import no.strazdins.data.Wallet;
import no.strazdins.data.WalletSnapshot;
import no.strazdins.tool.TimeConverter;

/**
 * A Sell transaction. Note: only transactions where USDT was obtained are
 * considered sell-transactions. For example, selling LTC in the market LTC/BTC is considered
 * a buy transaction. The reason: for each sell transaction we update the PNL. In the LTC/BTC
 * market we can't find any profit. We get profit or loss only when we get sell a currency for USDT.
 */
public class SellTransaction extends Transaction {
  private RawAccountChange base;
  private RawAccountChange quote;
  private RawAccountChange feeOp;

  public SellTransaction(Transaction transaction) {
    super(transaction);
    initBaseAndQuote();
  }

  private void initBaseAndQuote() {
    base = getFirstSellTypeChange();
    quote = getFirstBuyTypeChange();
    if (base == null || quote == null) {
      throw new IllegalStateException("Can't create a sell when some ops are missing!");
    }
    if (!quote.getAsset().equals(QUOTE_CURR)) {
      throw new IllegalStateException("Sell transactions must have "
          + QUOTE_CURR + " quote currency!");
    }
    baseCurrency = base.getAsset();
    baseCurrencyAmount = base.getAmount();
    quoteAmount = quote.getAmount();
    quoteCurrency = "USDT";
    feeOp = getFirstChangeOfType(Operation.FEE);
    if (feeOp != null) {
      fee = feeOp.getAmount();
      feeCurrency = feeOp.getAsset();
    }
  }

  @Override
  public String toString() {
    return "Sell " + base.getAmount() + " " + base.getAsset() + "/" + quote.getAsset()
        + " @ " + TimeConverter.utcTimeToString(utcTime);
  }

  @Override
  public WalletSnapshot process(WalletSnapshot walletSnapshot, ExtraInfoEntry extraInfo) {
    WalletSnapshot newSnapshot = walletSnapshot.prepareForTransaction(this);
    Wallet w = newSnapshot.getWallet();
    calculateFeeInUsdt(w);
    Decimal receivedUsdt = quoteAmount.add(feeInUsdt); // Fee is negative
    Decimal investedUsdt = base.getAmount().negate().multiply(newSnapshot.getAvgBaseObtainPrice());
    pnl = receivedUsdt.subtract(investedUsdt);
    newSnapshot.addPnl(pnl);

    baseObtainPriceInUsdt = newSnapshot.getAvgBaseObtainPrice();
    avgPriceInUsdt = quoteAmount.divide(baseCurrencyAmount.negate());

    newSnapshot.addAsset(QUOTE_CURR, quote.getAmount(), Decimal.ONE);
    newSnapshot.decreaseAsset(base.getAsset(), base.getAmount().negate());
    if (feeCurrency != null && fee != null && !fee.isZero()) {
      if (fee.isPositive()) {
        throw new IllegalStateException("Fee is expected to be negative!");
      }
      newSnapshot.decreaseAsset(feeCurrency, fee.negate());
    }

    return newSnapshot;
  }

  @Override
  public String getType() {
    return "Sell";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    SellTransaction that = (SellTransaction) o;
    return Objects.equals(base, that.base)
        && Objects.equals(quote, that.quote)
        && Objects.equals(feeOp, that.feeOp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), base, quote, feeOp);
  }
}
