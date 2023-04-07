package no.strazdins.transaction;

import no.strazdins.data.Decimal;
import no.strazdins.data.ExtraInfoEntry;
import no.strazdins.data.Operation;
import no.strazdins.data.RawAccountChange;
import no.strazdins.data.WalletSnapshot;
import no.strazdins.tool.Converter;

/**
 * A Buy-transaction.
 */
public class BuyTransaction extends Transaction {
  private RawAccountChange base;
  private RawAccountChange quote;
  private RawAccountChange feeOp;

  public BuyTransaction(Transaction transaction) {
    super(transaction);
    initBaseAndQuote();
  }

  private void initBaseAndQuote() {
    base = getFirstChangeOfType(Operation.BUY);
    quote = getFirstChangeOfType(Operation.TRANSACTION_RELATED);
    feeOp = getFirstChangeOfType(Operation.FEE);
    baseCurrencyAmount = base.getAmount();
    if (base == null || quote == null || feeOp == null) {
      throw new IllegalStateException("Can't create a buy when some ops are missing!");
    }
    fee = feeOp.getAmount();
    feeCurrency = feeOp.getAsset();
    quoteCurrency = quote.getAsset();
    quoteAmount = quote.getAmount();
    baseCurrency = base.getAsset();
  }

  @Override
  public String toString() {
    return "Buy " + base.getAmount() + " " + base.getAsset() + "/" + quote.getAsset()
        + " @ " + Converter.utcTimeToString(utcTime);
  }

  @Override
  public String getType() {
    return "Buy";
  }

  @Override
  public WalletSnapshot process(WalletSnapshot walletSnapshot, ExtraInfoEntry extraInfo) {
    WalletSnapshot newSnapshot = walletSnapshot.prepareForTransaction(this);
    calculateFeeInUsdt(newSnapshot.getWallet());

    if (feeInUsdt.isZero() && feeOp.getAsset().equals("BNB") && base.getAsset().equals("BNB")) {
      return processFirstBnbBuy(newSnapshot);
    } else if (feeOp.getAsset().equals("BNB")) {
      return processBuyWithBnbFee(newSnapshot);
    } else if (feeOp.getAsset().equals("USDT")) {
      return processBuyWithUsdtFee(newSnapshot);
    } else {
      throw new UnsupportedOperationException("Unknown type of buy transaction: " + this);
    }
  }

  private WalletSnapshot processFirstBnbBuy(WalletSnapshot newSnapshot) {
    Decimal usdtUsedInTransaction = quote.getAmount().negate();
    newSnapshot.decreaseAsset(quote.getAsset(), usdtUsedInTransaction);

    Decimal obtainedBnb = base.getAmount().subtract(fee.negate());
    Decimal avgBnbPrice = usdtUsedInTransaction.divide(obtainedBnb).multiply(
        newSnapshot.getWallet().getAvgObtainPrice(quote.getAsset())
    );
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
    newSnapshot.addAsset(base.getAsset(), base.getAmount(), baseObtainPriceInUsdt);
    newSnapshot.decreaseAsset("BNB", fee.negate());

    return newSnapshot;
  }

  private WalletSnapshot processBuyWithUsdtFee(WalletSnapshot newSnapshot) {
    Decimal usdtUsedInTransaction = (quote.getAmount().add(feeInUsdt)).negate();
    newSnapshot.decreaseAsset("USDT", usdtUsedInTransaction);

    Decimal avgBuyPrice = usdtUsedInTransaction.divide(base.getAmount());
    newSnapshot.addAsset(base.getAsset(), base.getAmount(), avgBuyPrice);

    baseObtainPriceInUsdt = avgBuyPrice;
    return newSnapshot;
  }
}
