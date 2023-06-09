package no.strazdins.transaction;

import java.util.Objects;
import no.strazdins.data.Decimal;
import no.strazdins.data.ExtraInfoEntry;
import no.strazdins.data.Operation;
import no.strazdins.data.RawAccountChange;
import no.strazdins.data.WalletSnapshot;
import no.strazdins.tool.TimeConverter;

/**
 * A Buy-transaction.
 */
public class BuyTransaction extends Transaction {
  protected RawAccountChange base;
  protected RawAccountChange quote;
  protected RawAccountChange feeOp;


  /**
   * Create a Buy-transaction.
   *
   * @param transaction The base transaction data
   * @throws IllegalStateException When some information is missing
   */
  public BuyTransaction(Transaction transaction) throws IllegalStateException {
    super(transaction);
    initBaseAndQuote();
  }

  private void initBaseAndQuote() throws IllegalStateException {
    base = getFirstBuyTypeChange();
    quote = getFirstSellTypeChange();
    if (base == null || quote == null) {
      throw new IllegalStateException("Can't create a buy when some ops are missing!");
    }
    feeOp = getFirstChangeOfType(Operation.FEE);
    if (feeOp != null) {
      fee = feeOp.getAmount();
      feeCurrency = feeOp.getAsset();
    }
    quoteCurrency = quote.getAsset();
    quoteAmount = quote.getAmount();
    baseCurrency = base.getAsset();
    baseCurrencyAmount = base.getAmount();
  }

  @Override
  public String toString() {
    return "Buy " + base.getAmount() + " " + base.getAsset() + "/" + quote.getAsset()
        + " @ " + TimeConverter.utcTimeToString(utcTime);
  }

  @Override
  public String getType() {
    return "Buy";
  }

  @Override
  public WalletSnapshot process(WalletSnapshot walletSnapshot, ExtraInfoEntry extraInfo) {
    WalletSnapshot newSnapshot = walletSnapshot.prepareForTransaction(this);
    calculateFeeInUsdt(newSnapshot.getWallet());

    if (!quote.getAsset().equals("USDT")) {
      throw new UnsupportedOperationException("Support Buy in markets /X, where X != USDT");
    }

    if (Decimal.ZERO.equals(feeInUsdt) && isFeeCurrency("BNB") && base.getAsset().equals("BNB")) {
      return processFirstBnbBuy(newSnapshot);
    } else if (isFeeCurrency("BNB")) {
      return processBuyWithBnbFee(newSnapshot);
    } else if (isFeeCurrency("USDT")) {
      return processBuyWithUsdtFee(newSnapshot, feeInUsdt);
    } else if (feeOp == null) {
      return processBuyWithUsdtFee(newSnapshot, Decimal.ZERO);
    } else {
      throw new UnsupportedOperationException("Unknown type of buy transaction: " + this);
    }
  }

  private boolean isFeeCurrency(String expectedFeeCurrency) {
    return feeOp != null && feeOp.getAsset().equals(expectedFeeCurrency);
  }

  private WalletSnapshot processFirstBnbBuy(WalletSnapshot newSnapshot) {
    Decimal quoteUsedInTransaction = quoteAmount.negate();
    newSnapshot.decreaseAsset(quoteCurrency, quoteUsedInTransaction);

    Decimal quoteObtainPrice = newSnapshot.getWallet().getAvgObtainPrice(quote.getAsset());

    avgPriceInUsdt = quoteUsedInTransaction.divide(baseCurrencyAmount).multiply(quoteObtainPrice);

    Decimal obtainedBnb = base.getAmount().subtract(fee.negate());
    Decimal avgBnbPrice = quoteUsedInTransaction.divide(obtainedBnb).multiply(quoteObtainPrice);
    newSnapshot.addAsset("BNB", obtainedBnb, avgBnbPrice);

    baseObtainPriceInUsdt = avgBnbPrice;
    feeInUsdt = fee.multiply(avgBnbPrice).negate();

    return newSnapshot;
  }

  private WalletSnapshot processBuyWithBnbFee(WalletSnapshot newSnapshot) {
    Decimal usdtUsedInTransaction = quote.getAmount().negate();
    newSnapshot.decreaseAsset(quote.getAsset(), usdtUsedInTransaction);
    Decimal usdtValueOfAsset = usdtUsedInTransaction.add(feeInUsdt.negate());
    baseObtainPriceInUsdt = usdtValueOfAsset.divide(base.getAmount());
    avgPriceInUsdt = usdtUsedInTransaction.divide(baseCurrencyAmount);
    newSnapshot.addAsset(base.getAsset(), base.getAmount(), baseObtainPriceInUsdt);
    newSnapshot.decreaseAsset("BNB", fee.negate());

    return newSnapshot;
  }

  private WalletSnapshot processBuyWithUsdtFee(WalletSnapshot newSnapshot, Decimal usdFee) {
    Decimal usdtUsedInTransaction = (quote.getAmount().add(usdFee)).negate();
    newSnapshot.decreaseAsset("USDT", usdtUsedInTransaction);

    Decimal avgBuyPrice = usdtUsedInTransaction.divide(base.getAmount());
    newSnapshot.addAsset(base.getAsset(), base.getAmount(), avgBuyPrice);

    avgPriceInUsdt = usdtUsedInTransaction.divide(baseCurrencyAmount);
    baseObtainPriceInUsdt = avgBuyPrice;
    return newSnapshot;
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
    BuyTransaction that = (BuyTransaction) o;
    return Objects.equals(base, that.base)
        && Objects.equals(quote, that.quote)
        && Objects.equals(feeOp, that.feeOp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), base, quote, feeOp);
  }
}
