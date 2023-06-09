package no.strazdins.process;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import no.strazdins.data.Operation;
import no.strazdins.data.RawAccountChange;
import no.strazdins.transaction.AutoInvestTransaction;
import no.strazdins.transaction.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logic for report generation. Has no connection with any files.
 */
public class ReportLogic {
  private static final Logger logger = LogManager.getLogger(ReportLogic.class);

  private AutoInvestSubscription autoInvestSubscription;
  private final List<AutoInvestTransaction> autoInvestTransactions = new LinkedList<>();
  private final List<AutoInvestTransaction> previousAutoInvestTransactions = new LinkedList<>();

  /**
   * Check all the raw account changes, group those by timestamp, merge as transactions.
   *
   * @param accountChanges Raw account changes. It is assumed that the timestamps are in
   *                       increasing order
   * @return List of higher-level transactions, also ordered by timestamp
   */
  public List<Transaction> groupTransactionsByTimestamp(List<RawAccountChange> accountChanges) {
    RawAccountChange lastChange = null;
    Transaction transaction = null;
    List<Transaction> transactions = new ArrayList<>();
    for (RawAccountChange change : accountChanges) {
      if (lastChange == null || lastChange.getUtcTime() != change.getUtcTime()) {
        transaction = new Transaction(change.getUtcTime());
        transactions.add(transaction);
      }
      if (AutoInvestTransaction.isAutoInvestOperation(change)) {
        Transaction updatedTransaction = updateAutoInvest(change, transaction);
        if (updatedTransaction != transaction) {
          replaceLastTransactionWith(transactions, updatedTransaction);
          transaction = updatedTransaction;
        }
      }
      transaction.append(change);
      lastChange = change;
    }
    return transactions;
  }

  private Transaction updateAutoInvest(RawAccountChange change,
                                       Transaction transaction) {
    if (isAutoInvestSpendOperation(change)) {
      if (isNewAutoInvestSubscription()) {
        autoInvestSubscription = new AutoInvestSubscription(
            getTimestampOfFirstCachedAutoInvestOrDefault(transaction.getUtcTime()),
            change.getAmount().negate());
        updateSubscriptionForCachedAutoInvestTransactions();
      }
      rememberLastAutoInvestTransactions();
    } else if (!isAutoInvestAcquireOperation(change)) {
      throw new IllegalStateException("Auto-invest but neither invest, nor acquire: " + change);
    }

    if (!(transaction instanceof AutoInvestTransaction)) {
      transaction = new AutoInvestTransaction(transaction, autoInvestSubscription);
      autoInvestTransactions.add((AutoInvestTransaction) transaction);
    }

    if (!Transaction.isUsdLike(change.getAsset())) {
      autoInvestSubscription.registerAcquiredAsset(change.getAsset());
    }

    return transaction;
  }

  private long getTimestampOfFirstCachedAutoInvestOrDefault(long defaultValue) {
    return !autoInvestTransactions.isEmpty()
        ? autoInvestTransactions.get(0).getUtcTime()
        : defaultValue;
  }

  private void rememberLastAutoInvestTransactions() {
    previousAutoInvestTransactions.clear();
    previousAutoInvestTransactions.addAll(autoInvestTransactions);
    autoInvestTransactions.clear();
  }

  private void updateSubscriptionForCachedAutoInvestTransactions() {
    for (AutoInvestTransaction a : autoInvestTransactions) {
      a.setSubscription(autoInvestSubscription);
    }
  }

  /**
   * This returns true when the current auto-invest operation signals start of a new
   * subscription where either the amounts or money distribution among coins has changed.
   *
   * @return True if new auto-invest subscription has started
   */
  private boolean isNewAutoInvestSubscription() {
    if (autoInvestSubscription == null) {
      return true;
    }
    if (previousAutoInvestTransactions.isEmpty()) {
      return false;
    }
    Set<String> prevCoins = getCoinsForComparison(previousAutoInvestTransactions);
    Set<String> currentCoins = getCoinsForComparison(autoInvestTransactions);
    return !prevCoins.equals(currentCoins);
  }

  /**
   * Get the coins involved in the last auto-invest transaction - used for comparison.
   *
   * @param transactions The transactions for the previous auto-invest
   * @return The bought coins
   */
  private static Set<String> getCoinsForComparison(List<AutoInvestTransaction> transactions) {
    Set<String> coins = new HashSet<>();
    for (AutoInvestTransaction t : transactions) {
      String boughtAsset = t.getBoughtAsset();
      if (boughtAsset != null) {
        coins.add(boughtAsset);
      } else {
        // Include invested USD and its amount
        String investment = t.getAmount().getNiceString() + " " + t.getInvestedAsset();
        coins.add(investment);
      }
    }
    return coins;
  }

  private boolean isAutoInvestSpendOperation(RawAccountChange change) {
    return change.getOperation().equals(Operation.AUTO_INVEST)
        && "USDT".equals(change.getAsset())
        && change.getAmount().isNegative();
  }

  private boolean isAutoInvestAcquireOperation(RawAccountChange change) {
    return change.getOperation().equals(Operation.AUTO_INVEST)
        && !"USDT".equals(change.getAsset())
        && change.getAmount().isPositive();
  }

  private void replaceLastTransactionWith(List<Transaction> transactions,
                                          Transaction newTransaction) {
    transactions.remove(transactions.size() - 1);
    transactions.add(newTransaction);
  }

  /**
   * Go through a list of raw transactions, look at their atomic changes, decide the type of each
   * transaction: Deposit, Buy, Saving interest, etc.
   *
   * @param rawTransactions Raw transactions
   * @return List of the same transactions, but with specific types
   */
  public List<Transaction> clarifyTransactionTypes(List<Transaction> rawTransactions) {
    List<Transaction> transactions = new LinkedList<>();
    for (Transaction rawTransaction : rawTransactions) {
      Transaction transaction = rawTransaction.clarifyTransactionType();
      if (transaction != null) {
        transactions.add(transaction);
      } else {
        logger.error("Unknown transaction: {} {}", rawTransaction,
            rawTransaction.getOperationMultiSet());
        throw new IllegalStateException("Unknown transaction: " + rawTransaction);
      }
    }
    return transactions;
  }

  /**
   * Check all the assets, rename all the LDxxx assets to xxx
   * (example: LDUSDT -> USDT, LDBTC -> BTC).
   *
   * @param changes Raw account changes to check and update in-place
   */
  public void updateLendingAssets(List<RawAccountChange> changes) {
    for (RawAccountChange change : changes) {
      String asset = change.getAsset();
      if (asset != null && asset.length() >= 3 && asset.startsWith("LD")) {
        change.setAsset(asset.substring(2));
      }
    }
  }
}
