package io.bitsquare.api;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import io.bitsquare.api.api.*;
import io.bitsquare.api.api.Currency;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.pricefeed.PriceFeedService;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.CryptoCurrencyAccount;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.SameBankAccount;
import io.bitsquare.payment.SpecificBanksAccount;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OfferBookService;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.User;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * This class is a proxy for all bitsquare features the api will use.
 * <p>
 * No methods/representations used in the interface layers (REST/Socket/...) should be used in this class.
 */
@Slf4j
public class BitsquareProxy {
    @Inject
    private WalletService walletService;
    @Inject
    private User user;
    @Inject
    private TradeManager tradeManager;
    @Inject
    private OpenOfferManager openOfferManager;
    @Inject
    private OfferBookService offerBookService;
    @Inject
    private P2PService p2PService;
    @Inject
    private KeyRing keyRing;
    @Inject
    private PriceFeedService priceFeedService;

    public BitsquareProxy(WalletService walletService, TradeManager tradeManager, OpenOfferManager openOfferManager,
                          OfferBookService offerBookService, P2PService p2PService, KeyRing keyRing,
                          PriceFeedService priceFeedService, User user) {
        this.walletService = walletService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.offerBookService = offerBookService;
        this.p2PService = p2PService;
        this.keyRing = keyRing;
        this.priceFeedService = priceFeedService;
        this.user = user;
    }

    public CurrencyList getCurrencyList() {
        CurrencyList currencyList = new CurrencyList();
        CurrencyUtil.getAllSortedCryptoCurrencies().forEach(cryptoCurrency -> currencyList.add(cryptoCurrency.getCode(), cryptoCurrency.getName(), "crypto"));
        CurrencyUtil.getAllSortedFiatCurrencies().forEach(cryptoCurrency -> currencyList.add(cryptoCurrency.getSymbol(), cryptoCurrency.getName(), "fiat"));
        Collections.sort(currencyList.currencies, (Currency p1, Currency p2) -> p1.name.compareTo(p2.name));
        return currencyList;
    }

    public MarketList getMarketList() {
        MarketList marketList = new MarketList();
        CurrencyList currencyList = getCurrencyList(); // we calculate this twice but only at startup
        //currencyList.getCurrencies().stream().flatMap(currency -> marketList.getMarkets().forEach(currency1 -> cur))
        List<Market> btc = CurrencyUtil.getAllSortedCryptoCurrencies().stream().filter(cryptoCurrency -> !(cryptoCurrency.getCode().equals("BTC"))).map(cryptoCurrency -> new Market(cryptoCurrency.getCode(), "BTC")).collect(toList());
        marketList.markets.addAll(btc);
        btc = CurrencyUtil.getAllSortedFiatCurrencies().stream().map(cryptoCurrency -> new Market("BTC", cryptoCurrency.getCode())).collect(toList());
        marketList.markets.addAll(btc);
        Collections.sort(currencyList.currencies, (Currency p1, Currency p2) -> p1.name.compareTo(p2.name));
        return marketList;
    }


    public AccountList getAccountList() {
        AccountList accountList = new AccountList();
        accountList.accounts = user.getPaymentAccounts().stream()
                .map(paymentAccount -> new Account(paymentAccount)).collect(Collectors.toSet());
        return accountList;
    }

    public boolean offerCancel(String offerId) {
        if (Strings.isNullOrEmpty(offerId)) {
            return false;
        }
        Optional<Offer> offer = offerBookService.getOffers().stream().filter(offer1 -> offerId.equals(offer1.getId())).findAny();
        if (!offer.isPresent()) {
            return false;
        }
        // do something more intelligent here, maybe block till handler is called.
        offerBookService.removeOffer(offer.get(), () -> log.info("offer removed"), (err) -> log.error("Error removing offer: " + err));
        return true;
    }

    public Optional<OfferData> getOfferDetail(String offerId) {
        if (Strings.isNullOrEmpty(offerId)) {
            return Optional.empty();
        }
        Optional<Offer> offer = offerBookService.getOffers().stream().filter(offer1 -> offerId.equals(offer1.getId())).findAny();
        if (!offer.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new OfferData(offer.get()));
    }

    public List<OfferData> getOfferList() {
        List<OfferData> offer = offerBookService.getOffers().stream().map(offer1 -> new OfferData(offer1)).collect(toList());
        return offer;

    }

    public boolean offerMake(String market, String accountId, String direction, BigDecimal amount, BigDecimal minAmount,
                          boolean marketPriceMargin, double marketPriceMarginParam, String currencyCode, String fiatPrice) {
        // TODO: detect bad direction, bad market, no paymentaccount for user
        // PaymentAccountUtil.isPaymentAccountValidForOffer
        Optional<Account> optionalAccount = getAccountList().getPaymentAccounts().stream()
                .filter(account1 -> account1.getPayment_account_id().equals(accountId)).findFirst();
        if(!optionalAccount.isPresent()) {
            // return an error
            return false;
        }
        Account account = optionalAccount.get();
        PaymentAccount paymentAccount = user.getPaymentAccount(account.getPayment_account_id());
        ArrayList<String> acceptedBanks = null;
        if (paymentAccount instanceof SpecificBanksAccount) {
            acceptedBanks = new ArrayList<>(((SpecificBanksAccount) paymentAccount).getAcceptedBanks());
        } else if (paymentAccount instanceof SameBankAccount) {
            acceptedBanks = new ArrayList<>();
            acceptedBanks.add(((SameBankAccount) paymentAccount).getBankId());
        }

        Offer offer = new Offer(UUID.randomUUID().toString(),
                p2PService.getAddress(),
                keyRing.getPubKeyRing(),
                Offer.Direction.valueOf(direction),
                Long.valueOf(fiatPrice),
                marketPriceMarginParam, //marketPriceMarginParam,
                marketPriceMargin, //useMarketBasedPrice.get(),
                amount.longValueExact(),
                minAmount.longValueExact(),
                currencyCode,  // currencycode
                (ArrayList<NodeAddress>) user.getAcceptedArbitratorAddresses(),
                account.getContract_data().getPayment_method_id(),
                account.getPayment_account_id(), //paymentAccount.getId(),
                account.getCountry().toString(), //countryCode,
                account.getAccepted_country_codes(), //acceptedCountryCodes,
                account.getBank_id(), //bankId,
                acceptedBanks, // acceptedBanks,
                priceFeedService); // priceFeedService);

//        offerBookService.addOffer(offer, () -> log.info("offer removed"), (err) -> log.error("Error removing offer: " + err));
        openOfferManager.placeOffer(offer, Coin.valueOf(amount.longValue()).subtract(FeePolicy.getCreateOfferFee()),
                true, (transaction) -> log.info("Result is "+transaction));
        return true;

        // TODO use openoffermanager.placeoffer instead
    }

    public WalletDetails getWalletDetails() {
        Wallet wallet = walletService.getWallet();
        if (wallet == null) {
            return null;
        }

        Coin availableBalance = wallet.getBalance(Wallet.BalanceType.AVAILABLE);
        Coin reservedBalance = wallet.getBalance(Wallet.BalanceType.ESTIMATED_SPENDABLE);
        return new WalletDetails(availableBalance.toPlainString(), reservedBalance.toPlainString());
    }

    public WalletTransactions getWalletTransactions(long start, long end, long limit) {
        boolean includeDeadTransactions = false;
        Set<Transaction> transactions = walletService.getWallet().getTransactions(includeDeadTransactions);
        WalletTransactions walletTransactions = new WalletTransactions();
        List<io.bitsquare.api.api.WalletTransaction> transactionList = walletTransactions.getTransactions();

        for (Transaction t : transactions) {
//            transactionList.add(new io.bitsquare.api.api.WalletTransaction(t.getValue(walletService.getWallet().getTransactionsByTime())))
        }
        return null;
    }

    public List<WalletAddress> getWalletAddresses() {
        return user.getPaymentAccounts().stream()
                .filter(paymentAccount -> paymentAccount instanceof CryptoCurrencyAccount)
                .map(paymentAccount -> (CryptoCurrencyAccount) paymentAccount)
                .map(paymentAccount -> new WalletAddress(((CryptoCurrencyAccount) paymentAccount).getId(), paymentAccount.getPaymentMethod().toString(), ((CryptoCurrencyAccount) paymentAccount).getAddress()))
                .collect(toList());
    }
}
