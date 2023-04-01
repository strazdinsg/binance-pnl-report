# Report generation logic

This document describes the rules used for generating reports for (Norwegian) tax authorities.

## Requirements

The tax authorities requests the following information:

1. Profit/loss (PNL) report of each transaction
2. The balance of assets (amount of each currency) in the wallet at the end of each year (December
   31st 23:59:59).

The user specifies the home currency (HC), for example: NOK or EUR. Profit and loss (PNL) is always
calculated in the home currency.

## Required transaction information

To calculate the necessary reports, the following transactions must be collected from the
cryptocurrency exchange
(Binance or other):

1. Executed buy-orders
2. Executed sell-orders
3. Deposits
4. Withdrawals
5. Currency purchase with a credit card
6. Interest on savings
7. _Dust collection_
8. Asset dividends
9. Auto-invest

## Stored information

To calculate the necessary reports, the following information is necessary:

* User's home currency (HC)
* List of all transactions
* Wallet snapshot after each transaction

## Data structures

Note: the data structures described here are deprecated and were described some time ago when the
idea was different. However, the structures can be useful in understanding the algorithms described
below.

The necessary information can be stored in the following data structures:

```
// Stores information about a transaction: buy order, sell order, etc
Transaction:
    type: TransactionTypeEnum
    baseCurrency: String
    quoteCurrency: String
    timestamp: long
    baseCurrencyAmount: Decimal
    quoteCurrencyAmount: Decimal
    averagePrice: Decimal
    fee: Decimal
    feeCurrency: String
    profitLossInHC: Decimal

// The different transaction types
TransactionTypeEnum: (BuyOrder, SellOrder, CoinDeposit, CoinWithdrawal, 
    FiatDeposit, FiathWithdrawal, FiatCardPurchase, CoinCardPurchase,
    SavingsInterest, DustCollection, FiatExchange, AutoInvest, AssetDividend)

// Snapshot of the whole wallet after a specific transaction. 
// Contains a list of CurrencyBalanceSnapshot objects
WalletSnapshot:
    transaction: Transaction
    currencySnapshots: List<CurrencyBalanceSnapshot>
    
// Snapshot of one particular currency at a specific time moment
CurrencyBalanceSnapshot:
    timestamp: long
    currency: String
    amount: Decimal
    averageObtainPriceHC: Decimal
```

Note: all money-related amounts are represented as Decimal here, to avoid rounding problems with
double. The specific data type to be used is selected during implementation.

## Calculation algorithm

The following general rules apply:

* Raw account changes are collected from the CSV file (downloaded from Binance)
* The raw changes are grouped as transactions
* Some transactions may need user's manual input:
    * Fiat currency exchange - the used exchange rate
    * Deposit - the obtaining price of the deposited currency, in HC
    * Withdrawal - the realised sell-price of the currency, in HC
* After each transaction, the following is re-calculated:
    * Amount and average obtaining price (in HC) for each asset involved in the transaction
    * Profit/Loss in HC for this specific transaction (if any)
    * New wallet snapshot - all the assets in the wallet
    * Current "running" Profit/loss in HC

In buy-transactions, one currency is purchased (called _base currency_) while another currency is
sold (called _quote currency_). In sell transactions the base currency is sold and quote currency is
purchased.

### Fee calculations

Fee calculations are as follows:

* `feeInHC = transaction.fee * (wallet[transaction.feeCurrency].averageObtainPriceHC)`
* `wallet[transaction.feeCurrency].amount -= transaction.fee`

### Rules for calculation for each transaction type

Calculations are performed for each transaction, based on the transaction type. Each Transaction
type is represented by a separate class in the code. The class implements
method `updateWalletSnapshot(WalletSnapshot)`. The following sections describe the logic of how each
transaction type must update the wallet.

#### Buy order

An executed buy order means that:

* A currency has been purchased (base currency)
* A currency has been sold (quote currency)
* A fee has been paid

Wallet changes:

```
* quoteSpentInTransaction = transaction.baseAmount * transaction.averagePrice
* wallet[quote].amount -= quoteSpentInTransaction
* hcSpentInTransaction = quoteSpentInTransaction * wallet[quote].averageObtainPriceHC + feeInHC
* totalHCSpent = hcSpentInTransaction + (wallet[baseCurrency].averageObtainPriceHc *
  wallet[baseCurrency].amount)
* wallet[baseCurrency].amount += transaction.baseAmount
* wallet[baseCurrency].averageObtainPriceHC = totalHCSpent / wallet[baseCurrency].amount
* transaction.profitLossInHC is unchanged
```

#### Auto-invest

An auto-invest transaction is handled the same way as a buy-transaction - a coin is purchased at
scheduled moments.

#### Sell order

An executed sell order means that:

* A currency has been sold (base currency)
* A currency has been purchased (quote currency)
* A fee has been paid

Wallet changes:

```
* quoteObtainedInTransaction = transaction.baseAmount * transaction.averagePrice
* wallet[quote].amount += quoteObtainedInTransaction
* wallet[baseCurrency].amount -= transaction.baseAmount
* wallet[baseCurrency].averageObtainPriceHC is unchanged
* sellPriceInHC = transaction.averagePrice * wallet[quote].averageObtainPriceHC
* priceDifferenceInHC = sellPriceInHC - wallet[baseCurrency].averageObtainPrice
* transaction.profitLossInHC = transaction.amount * priceDifferenceInHC
```

#### Deposit

The user has deposited a cryptocurrency in the exchange.

It is not known where and how the currency was obtained (mining, or purchase on another platform).

User must enter manually enter the transaction.averagePrice - the obtain-price for the
cryptocurrency, in HC.

Wallet changes:

```
* transaction.quoteCurrency = homeCurrency
* transaction.averagePrice = [manualUserInput]
* wallet[baseCurrency].amount += transaction.baseAmount
* hcSpentInTransaction = transaction.baseAmount * transaction.averagePrice
* totalHcSpent = hcSpentInTransaction + (wallet[baseCurrency].averageObtainPriceHC *
  wallet[baseCurrency].amount)
* wallet[baseCurrency].averageObtainPriceHC = totalHcSpent / wallet[baseCurrency].amount
* transaction.profitLossInHC is unchanged
```

#### Withdrawal

The user has withdrawn a cryptocurrency from the exchange.

It is now know what happens to the currency after the withdrawal, hence

* We assume that it was converted to the users home-currency (HC) immediately after withdrawal
* The user must manually specify the realised price (in HC) at which the withdrawn currency was
  converted

Wallet changes:

```
* transaction.quoteCurrency = homeCurrency
* transaction.averagePrice = [manualUserInput]
* wallet[baseCurrency].amount -= transaction.baseAmount
* hcObtainedInTransaction = transaction.baseAmount * transaction.averagePrice
* wallet[baseCurrency].averageObtainPriceHC is unchanged
* priceDifferenceInHC = transaction.averagePrice - wallet[baseCurrency].averageObtainPrice
* transaction.profitLossInHC = transaction.amount * priceDifferenceInHC
```

#### Credit-card purchase

The user has purchased a currency, using credit card.

Wallet changes:

```
* transaction.quoteCurrency = homeCurrency
* wallet[baseCurrency].amount += transaction.baseAmount
* hcSpentInTransaction = transaction.baseAmount * transaction.averagePrice
* totalHcSpent = hcSpentInTransaction + (wallet[baseCurrency].averageObtainPriceHC *
  wallet[baseCurrency].amount)
* wallet[baseCurrency].averageObtainPriceHC = totalHcSpent / wallet[baseCurrency].amount
* transaction.profitLossInHC is unchanged
```

#### Savings interest

Interest on savings generates new currency where the "average obtaining price" for that new currency
is zero.

Wallet changes:

```
* totalHCSpent = (wallet[baseCurrency].averageObtainPriceHc * wallet[baseCurrency].amount)
* wallet[baseCurrency].amount += transaction.baseAmount
* wallet[baseCurrency].averageObtainPriceHC = totalHCSpent / wallet[baseCurrency].amount
* transaction.profitLossInHC is unchanged
```

#### Dust collection

Dust collection means that the user converted a small amount of a coin into BNB coin. It is
essentially a sell-order in the coin/BNB market.

#### Asset dividends

Asset dividend means that the user gets a "free token" because of her holding a specific asset in
the wallet for a specific amount of time. The algorithm for wallet updates is the same as for the
savings interest, because we get an extra amount of asset (coin) for free.

#### Other transactions

Binance transaction export includes the following additional transaction types:

* Commission Rebate
* Cashback Voucher
* Distribution
* Simple Earn Flexible Redemption
* Simple Earn Flexible Subscription
* Auto-invest transactions (new in 2022)

These transactions also must be handled.