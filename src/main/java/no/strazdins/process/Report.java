package no.strazdins.process;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import no.strazdins.data.Decimal;
import no.strazdins.data.ExtraInfo;
import no.strazdins.data.ExtraInfoEntry;
import no.strazdins.data.ExtraInfoType;
import no.strazdins.data.Wallet;
import no.strazdins.data.WalletDiff;
import no.strazdins.data.WalletSnapshot;
import no.strazdins.tool.BinanceApiClient;
import no.strazdins.tool.ReportHelper;
import no.strazdins.tool.TimeConverter;
import no.strazdins.transaction.AutoInvestTransaction;
import no.strazdins.transaction.SavingsRedemptionTransaction;
import no.strazdins.transaction.SavingsSubscriptionTransaction;
import no.strazdins.transaction.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Profit-and-loss report.
 */
public class Report implements Iterable<WalletSnapshot> {
  private static final Logger logger = LogManager.getLogger(Report.class);

  private final ExtraInfo extraInfo;
  private boolean extraInfoUpdated = false;
  private final List<WalletSnapshot> walletSnapshots = new LinkedList<>();
  private WalletSnapshot currentWalletSnapshot;

  private final BinanceApiClient apiClient = new BinanceApiClient();

  public Report(ExtraInfo extraInfo) {
    this.extraInfo = extraInfo;
    this.currentWalletSnapshot = WalletSnapshot.createEmpty();
  }

  /**
   * Process the transaction, generate a new wallet snapshot.
   *
   * @param transaction The transaction to process
   */
  public void process(Transaction transaction) {
    WalletSnapshot newSnapshot = transaction.process(currentWalletSnapshot,
        getExtraInfo(transaction));
    WalletDiff snapshotDiff = newSnapshot.getDiffFrom(currentWalletSnapshot);
    WalletDiff rawOperationDiff = transaction.getOperationDiff();
    if (!snapshotDiff.equals(rawOperationDiff) && !isDiffDiscrepancyAllowed(transaction)) {
      if (transaction instanceof AutoInvestTransaction) {
        logger.debug("Wallet changes for {} differ from operation changes:", transaction);
        logger.debug("   Operation diff: {}", rawOperationDiff);
        logger.debug("   Snapshot  diff: {}", snapshotDiff);
      } else {
        logger.warn("Wallet changes for {} differ from operation changes:", transaction);
        logger.warn("   Operation diff: {}", rawOperationDiff);
        logger.warn("   Snapshot  diff: {}", snapshotDiff);
      }
    }
    walletSnapshots.add(newSnapshot);
    currentWalletSnapshot = newSnapshot;
  }

  /**
   * Return true if it is OK for the transaction to have a wallet diff other than the sum of
   * raw operation changes. This can be OK, for example, for savings subscriptions and
   * savings withdrawals operations.
   *
   * @param transaction The transaction to check
   * @return true if it is OK for this transaction to have a diff which is not the same as sum
   *     of individual operation diffs
   */
  private boolean isDiffDiscrepancyAllowed(Transaction transaction) {
    return transaction instanceof SavingsSubscriptionTransaction
        || transaction instanceof SavingsRedemptionTransaction;
  }

  private ExtraInfoEntry getExtraInfo(Transaction transaction) {
    return extraInfo.getAtTime(transaction.getUtcTime());
  }

  @Override
  public Iterator<WalletSnapshot> iterator() {
    return walletSnapshots.iterator();
  }

  public List<AnnualReport> createAnnualReports() {
    List<WalletSnapshot> yearEndSnapshots = getYearEndSnapshots();
    return yearEndSnapshots.stream().map(this::createYearEndReport).toList();
  }

  private List<WalletSnapshot> getYearEndSnapshots() {
    return ReportHelper.filterYearEndSnapshots(walletSnapshots);
  }

  private AnnualReport createYearEndReport(WalletSnapshot snapshot) {
    long yearEndTimestamp = TimeConverter.getYearEndTimestamp(snapshot.getYear());
    Decimal exchangeRate = getExchangeRateAt(yearEndTimestamp);
    Decimal pnlUsd = snapshot.getPnl();
    Decimal pnlHc = pnlUsd.multiply(exchangeRate);
    Decimal walletValueUsd = getTotalWalletValueAt(snapshot.getWallet(), yearEndTimestamp);
    Decimal walletValueHc = walletValueUsd.multiply(exchangeRate);
    return new AnnualReport(yearEndTimestamp, pnlUsd, exchangeRate, pnlHc,
        walletValueUsd, walletValueHc);
  }

  /**
   * Get Total wallet value at the given time moment.
   *
   * @param wallet    The wallet to check
   * @param timestamp The timestamp of the time moment of interest, including milliseconds
   * @return The total wallet value at the given time moment, in USD currency
   */
  public Decimal getTotalWalletValueAt(Wallet wallet, long timestamp) {
    Decimal totalValue = Decimal.ZERO;
    for (String asset : wallet) {
      Decimal assetValue = wallet.getAssetAmount(asset).multiply(getAssetPriceAt(asset, timestamp));
      totalValue = totalValue.add(assetValue);
    }
    return totalValue;
  }

  private Decimal getAssetPriceAt(String asset, long timestamp) {
    Decimal assetPrice;

    if (asset.equals("USDT")) {
      assetPrice = Decimal.ONE;
    } else {
      assetPrice = extraInfo.getAssetPriceAtTime(timestamp, asset);
    }

    if (assetPrice == null) {
      logger.info("No {} price found in extra info, checking Binance REST API", asset);
      assetPrice = apiClient.getDailyClosePrice(asset, timestamp);
      if (assetPrice != null) {
        appendPriceToExtraInfo(timestamp, asset, assetPrice);
      }
    }

    if (assetPrice == null) {
      throw new IllegalStateException("Missing " + asset + " price at " + timestamp
          + " (" + TimeConverter.utcTimeToString(timestamp) + ")");
    }

    return assetPrice;
  }

  private void appendPriceToExtraInfo(long utcTimestamp, String asset, Decimal price) {
    extraInfo.add(new ExtraInfoEntry(utcTimestamp, ExtraInfoType.ASSET_PRICE,
        asset, price.getNiceString()));
    extraInfoUpdated = true;
  }

  private Decimal getExchangeRateAt(long timestamp) {
    ExtraInfoEntry ei = extraInfo.getAtTime(timestamp);
    if (ei == null) {
      throw new IllegalStateException("Did not find exchange rate at "
          + TimeConverter.utcTimeToString(timestamp));
    }
    return new Decimal(ei.value());
  }

  /**
   * Get the extra provided information (not part of Binance transaction CSV).
   *
   * @return Extra info collection
   */
  public ExtraInfo getExtras() {
    return extraInfo;
  }

  /**
   * Check whether extra info has been updated with some prices from Binance API.
   *
   * @return True when extra info has been updated, false if extra info contains only previously
   *     provided values (those loaded at the start of the script)
   */
  public boolean isExtraInfoUpdated() {
    return extraInfoUpdated;
  }
}
