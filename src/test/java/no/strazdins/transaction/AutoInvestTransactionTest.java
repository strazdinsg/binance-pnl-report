package no.strazdins.transaction;

import static no.strazdins.testtools.TestTools.createAutoInvestExtraInfo;
import static no.strazdins.testtools.TestTools.createAutoInvestments;
import static no.strazdins.testtools.TestTools.createExpectedAutoInvestExtraInfo;
import static no.strazdins.testtools.TestTools.expectAcquisition;
import static no.strazdins.testtools.TestTools.expectInvestment;
import static no.strazdins.testtools.TestTools.expectNotSameSubscription;
import static no.strazdins.testtools.TestTools.expectSameSubscription;
import static no.strazdins.testtools.TestTools.expectWalletState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import no.strazdins.data.AccountType;
import no.strazdins.data.Decimal;
import no.strazdins.data.ExtraInfo;
import no.strazdins.data.ExtraInfoEntry;
import no.strazdins.data.ExtraInfoType;
import no.strazdins.data.Operation;
import no.strazdins.data.RawAccountChange;
import no.strazdins.data.WalletSnapshot;
import org.junit.jupiter.api.Test;

class AutoInvestTransactionTest {
  @Test
  void testBoughtAsset() {
    List<Transaction> transactions = createAutoInvestments(
        "-5", "USDT",
        "0.02", "BNB"
    );
    assertEquals(2, transactions.size());
    AutoInvestTransaction invest = (AutoInvestTransaction) transactions.get(0);
    AutoInvestTransaction acquire = (AutoInvestTransaction) transactions.get(1);
    assertNull(invest.getBoughtAsset());
    assertEquals("BNB", acquire.getBoughtAsset());
  }

  @Test
  void testInvestedAsset() {
    List<Transaction> transactions = createAutoInvestments(
        "-5", "USDT",
        "0.02", "BNB"
    );
    assertEquals(2, transactions.size());
    AutoInvestTransaction invest = (AutoInvestTransaction) transactions.get(0);
    AutoInvestTransaction acquire = (AutoInvestTransaction) transactions.get(1);
    assertNull(acquire.getInvestedAsset());
    assertEquals("USDT", invest.getInvestedAsset());
  }

  @Test
  void testIsAutoInvestOperation() {
    RawAccountChange change = new RawAccountChange(0, AccountType.SPOT, Operation.AUTO_INVEST,
        "USDT", new Decimal("-5"), "");
    assertTrue(AutoInvestTransaction.isAutoInvestOperation(change));
  }

  @Test
  void testClarifiedAsAutoInvest() {
    List<Transaction> transactions = createAutoInvestments(
        "-5", "USDT",
        "0.00141986", "BNB",
        "0.00018789", "BTC",
        "0.00030772", "ETH"
    );
    assertEquals(4, transactions.size());
    expectInvestment(transactions.get(0), "-5", "USDT");
    expectSameSubscription(transactions);
    AutoInvestTransaction invest = (AutoInvestTransaction) transactions.get(0);
    assertEquals(new Decimal("5"), invest.getSubscription().getInvestmentAmount());
  }

  @Test
  void testTwoAutoInvestments() {
    List<Transaction> transactions = createAutoInvestments(
        "-5", "USDT",
        "0.00141986", "BNB",
        "0.00018789", "BTC",
        "0.00030772", "ETH",
        "-5", "USDT",
        "0.00019", "BTC",
        "0.0014", "BNB",
        "0.0003", "ETH"
    );
    assertEquals(8, transactions.size());
    expectInvestment(transactions.get(0), "-5", "USDT");
    expectAcquisition(transactions.get(1), "0.00141986", "BNB");
    expectAcquisition(transactions.get(2), "0.00018789", "BTC");
    expectAcquisition(transactions.get(3), "0.00030772", "ETH");
    expectInvestment(transactions.get(4), "-5", "USDT");
    expectAcquisition(transactions.get(5), "0.00019", "BTC");
    expectAcquisition(transactions.get(6), "0.0014", "BNB");
    expectAcquisition(transactions.get(7), "0.0003", "ETH");
    expectSameSubscription(transactions);
  }

  @Test
  void testAutoInvestSubscriptionChanges() {
    List<Transaction> transactions = createAutoInvestments(
        "-5", "USDT",
        "0.00141986", "BNB",
        "0.00018789", "BTC",
        "0.00030772", "ETH",
        "-5", "USDT",
        "0.00019", "BTC",
        "0.0003", "ETH",
        // We need to have one more instance of the second auto-invest, otherwise
        // the change won't be detected
        "-5", "USDT",
        "0.00019", "BTC",
        "0.0003", "ETH"
    );
    assertEquals(10, transactions.size());
    expectNotSameSubscription(transactions.get(0), transactions.get(4));
    expectSameSubscription(transactions.subList(0, 4));
    expectSameSubscription(transactions.subList(4, 10));
  }


  @Test
  void testAutoInvestSubscriptionChangeByUsdAmount() {
    List<Transaction> transactions = createAutoInvestments(
        "-5", "USDT",
        "0.00141986", "BNB",
        "0.00018789", "BTC",
        "0.00030772", "ETH",
        "-10", "USDT",
        "0.0028", "BNB",
        "0.0004", "BTC",
        "0.0006", "ETH",
        // We need to have one more instance of the second auto-invest, otherwise
        // the change won't be detected
        "-10", "USDT",
        "0.0028", "BNB",
        "0.0004", "BTC",
        "0.0006", "ETH"
    );
    assertEquals(12, transactions.size());
    expectNotSameSubscription(transactions.get(0), transactions.get(4));
    expectSameSubscription(transactions.subList(0, 4));
    expectSameSubscription(transactions.subList(4, 12));
  }

  @Test
  void testProcess() {
    List<Transaction> transactions = createAutoInvestments(
        "-5", "USDT",
        "0.01", "BNB",
        "0.0002", "BTC",
        "0.0004", "ETH",
        "-10", "USDT",
        "0.0004", "BTC",
        "0.0008", "ETH",
        "-10", "USDT",
        "0.00065", "BTC",
        "0.001", "ETH",
        "-5", "USDT",
        "0.0002", "BTC",
        "-5", "USDT",
        "0.0002", "BTC"
    );
    WalletSnapshot walletSnapshot = WalletSnapshot.createEmpty();
    walletSnapshot.addAsset("USDT", new Decimal("100"), Decimal.ONE);
    Transaction t0 = transactions.get(0);
    Transaction t4 = transactions.get(4);
    Transaction t10 = transactions.get(10);
    ExtraInfo ei = new ExtraInfo();
    ei.add(createAutoInvestExtraInfo(t0.getUtcTime(), "BTC", "0.5", "BNB", "0.3", "ETH", "0.2"));
    ei.add(createAutoInvestExtraInfo(t4.getUtcTime(), "BTC", "0.5", "ETH", "0.5"));
    ei.add(createAutoInvestExtraInfo(t10.getUtcTime(), "BTC", "1"));

    walletSnapshot = t0.process(walletSnapshot, ei.getAtTime(t0.getUtcTime()));
    expectWalletState(walletSnapshot, "0", "0", "95", "USDT", "1");

    Transaction t1 = transactions.get(1);
    walletSnapshot = t1.process(walletSnapshot, ei.getAtTime(t1.getUtcTime()));
    expectWalletState(walletSnapshot, "0", "0", "95", "USDT", "1", "0.01", "BNB", "150");

    walletSnapshot = transactions.get(2).process(walletSnapshot, null);
    expectWalletState(walletSnapshot, "0", "0",
        "95", "USDT", "1",
        "0.01", "BNB", "150",
        "0.0002", "BTC", "12500"
    );

    walletSnapshot = transactions.get(3).process(walletSnapshot, null);
    expectWalletState(walletSnapshot, "0", "0",
        "95", "USDT", "1",
        "0.01", "BNB", "150",
        "0.0002", "BTC", "12500",
        "0.0004", "ETH", "2500"
    );

    walletSnapshot = t4.process(walletSnapshot, ei.getAtTime(t4.getUtcTime()));
    expectWalletState(walletSnapshot, "0", "0",
        "85", "USDT", "1",
        "0.01", "BNB", "150",
        "0.0002", "BTC", "12500",
        "0.0004", "ETH", "2500"
    );

    walletSnapshot = transactions.get(5).process(walletSnapshot, null);
    expectWalletState(walletSnapshot, "0", "0",
        "85", "USDT", "1",
        "0.01", "BNB", "150",
        "0.0006", "BTC", "12500",
        "0.0004", "ETH", "2500"
    );

    walletSnapshot = transactions.get(6).process(walletSnapshot, null);
    expectWalletState(walletSnapshot, "0", "0",
        "85", "USDT", "1",
        "0.01", "BNB", "150",
        "0.0006", "BTC", "12500",
        "0.0012", "ETH", "5000"
    );

    walletSnapshot = transactions.get(7).process(walletSnapshot, null);
    expectWalletState(walletSnapshot, "0", "0",
        "75", "USDT", "1",
        "0.01", "BNB", "150",
        "0.0006", "BTC", "12500",
        "0.0012", "ETH", "5000"
    );

    walletSnapshot = transactions.get(8).process(walletSnapshot, null);
    expectWalletState(walletSnapshot, "0", "0",
        "75", "USDT", "1",
        "0.01", "BNB", "150",
        "0.00125", "BTC", "10000",
        "0.0012", "ETH", "5000"
    );

    walletSnapshot = transactions.get(9).process(walletSnapshot, null);
    expectWalletState(walletSnapshot, "0", "0",
        "75", "USDT", "1",
        "0.01", "BNB", "150",
        "0.00125", "BTC", "10000",
        "0.0022", "ETH", "5000"
    );

    walletSnapshot = transactions.get(10).process(walletSnapshot, ei.getAtTime(t10.getUtcTime()));
    expectWalletState(walletSnapshot, "0", "0",
        "70", "USDT", "1",
        "0.01", "BNB", "150",
        "0.00125", "BTC", "10000",
        "0.0022", "ETH", "5000"
    );

    walletSnapshot = transactions.get(11).process(walletSnapshot, null);
    expectWalletState(walletSnapshot, "0", "0",
        "70", "USDT", "1",
        "0.01", "BNB", "150",
        "0.00145", "BTC", "12068.96551724",
        "0.0022", "ETH", "5000"
    );

    walletSnapshot = transactions.get(12).process(walletSnapshot, null);
    expectWalletState(walletSnapshot, "0", "0",
        "65", "USDT", "1",
        "0.01", "BNB", "150",
        "0.00145", "BTC", "12068.96551724",
        "0.0022", "ETH", "5000"
    );
    walletSnapshot = transactions.get(13).process(walletSnapshot, null);
    expectWalletState(walletSnapshot, "0", "0",
        "65", "USDT", "1",
        "0.01", "BNB", "150",
        "0.00165", "BTC", "13636.36363636",
        "0.0022", "ETH", "5000"
    );
  }

  @Test
  void testSubscriptionTimestamps() {
    List<Transaction> transactions = createAutoInvestments(
        "-5", "USDT",
        "0.001", "BNB",
        "0.0002", "BTC",
        "0.0004", "ETH",
        "-10", "USDT",
        "0.0004", "BTC",
        "0.0008", "ETH",
        "-10", "USDT",
        "0.0005", "BTC",
        "0.001", "ETH",
        "-5", "USDT",
        "0.0002", "BTC",
        "-5", "USDT",
        "0.0002", "BTC"
    );
    AutoInvestTransaction t0 = (AutoInvestTransaction) transactions.get(0);
    AutoInvestTransaction t4 = (AutoInvestTransaction) transactions.get(4);
    AutoInvestTransaction t10 = (AutoInvestTransaction) transactions.get(10);
    assertEquals(t0.getUtcTime(), t0.getSubscription().getUtcTime());
    assertEquals(t4.getUtcTime(), t4.getSubscription().getUtcTime());
    assertEquals(t10.getUtcTime(), t10.getSubscription().getUtcTime());
  }

  @Test
  void testNecessaryExtraInfo() {
    List<Transaction> transactions = createAutoInvestments(
        "-5", "USDT",
        "0.001", "BNB",
        "0.0002", "BTC",
        "0.0004", "ETH",
        "-10", "USDT",
        "0.0004", "BTC",
        "0.0008", "ETH",
        "-10", "USDT",
        "0.0005", "BTC",
        "0.001", "ETH",
        "-5", "USDT",
        "0.0002", "BTC",
        "-5", "USDT",
        "0.0002", "BTC"
    );
    for (int i = 0; i < transactions.size(); ++i) {
      Transaction t = transactions.get(i);
      ExtraInfoEntry expectedExtraInfo = switch (i) {
        case 0 -> createExpectedAutoInvestExtraInfo(t, "BTC", "BNB", "ETH");
        case 4 -> createExpectedAutoInvestExtraInfo(t, "BTC", "ETH");
        case 10 -> createExpectedAutoInvestExtraInfo(t, "BTC");
        default -> null;
      };
      assertEquals(expectedExtraInfo, t.getNecessaryExtraInfo(),
          "Wrong extra info for transaction #" + i);
    }
  }

  @Test
  void testMultiAutoInvestChangesFail() {
    List<Transaction> transactions = createAutoInvestments(
        "-5", "USDT",
        "0.001", "BNB"
    );
    assertInstanceOf(AutoInvestTransaction.class, transactions.get(0));
    AutoInvestTransaction t0 = (AutoInvestTransaction) transactions.get(0);
    t0.append(new RawAccountChange(t0.getUtcTime(), AccountType.SPOT, Operation.AUTO_INVEST,
        "BTC", new Decimal("0.0001"),
        "Should fail to add BTC in the same second as USDT is spent")
    );
    WalletSnapshot walletSnapshot = WalletSnapshot.createEmpty();
    walletSnapshot.addAsset("USDT", new Decimal("20"), Decimal.ONE);
    ExtraInfo ei = new ExtraInfo();
    ei.add(new ExtraInfoEntry(t0.getUtcTime(), ExtraInfoType.AUTO_INVEST_PROPORTIONS,
        "BNB", "1.0"));
    ExtraInfoEntry extraInfoEntry = ei.getAtTime(t0.getUtcTime());
    assertThrows(IllegalStateException.class, () -> t0.process(walletSnapshot, extraInfoEntry));
  }
}
