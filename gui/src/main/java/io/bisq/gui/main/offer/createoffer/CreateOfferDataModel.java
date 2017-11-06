/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.offer.createoffer;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.btc.listeners.BalanceListener;
import io.bisq.core.btc.wallet.BsqBalanceListener;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.TradeWalletService;
import io.bisq.core.filter.FilterManager;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.offer.OfferUtil;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.payment.*;
import io.bisq.core.payment.payload.BankAccountPayload;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.handlers.TransactionResultHandler;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.gui.common.model.ActivatableDataModel;
import io.bisq.gui.main.overlays.notifications.Notification;
import io.bisq.gui.util.BSFormatter;
import io.bisq.network.p2p.P2PService;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class CreateOfferDataModel extends ActivatableDataModel {
    private final OpenOfferManager openOfferManager;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final Preferences preferences;
    private final User user;
    private final KeyRing keyRing;
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    final String shortOfferId;
    private final FilterManager filterManager;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final TradeWalletService tradeWalletService;
    private final FeeService feeService;
    private final BSFormatter formatter;
    private final String offerId;
    private final AddressEntry addressEntry;
    private final BalanceListener btcBalanceListener;
    private final BsqBalanceListener bsqBalanceListener;
    private final SetChangeListener<PaymentAccount> paymentAccountsChangeListener;

    private OfferPayload.Direction direction;

    private TradeCurrency tradeCurrency;

    private final StringProperty tradeCurrencyCode = new SimpleStringProperty();

    private final BooleanProperty isBtcWalletFunded = new SimpleBooleanProperty();
    private final BooleanProperty useMarketBasedPrice = new SimpleBooleanProperty();
    //final BooleanProperty isMainNet = new SimpleBooleanProperty();
    //final BooleanProperty isFeeFromFundingTxSufficient = new SimpleBooleanProperty();

    // final ObjectProperty<Coin> feeFromFundingTxProperty = new SimpleObjectProperty(Coin.NEGATIVE_SATOSHI);
    private final ObjectProperty<Coin> amount = new SimpleObjectProperty<>();
    private final ObjectProperty<Coin> minAmount = new SimpleObjectProperty<>();
    private final ObjectProperty<Price> price = new SimpleObjectProperty<>();
    private final ObjectProperty<Volume> volume = new SimpleObjectProperty<>();
    private final ObjectProperty<Coin> buyerSecurityDeposit = new SimpleObjectProperty<>();
    private final Coin sellerSecurityDeposit;
    private final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    private final ObjectProperty<Coin> missingCoin = new SimpleObjectProperty<>(Coin.ZERO);
    private final ObjectProperty<Coin> balance = new SimpleObjectProperty<>();

    private final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();

    PaymentAccount paymentAccount;
    boolean isTabSelected;
    private Notification walletFundedNotification;
    private boolean useSavingsWallet;
    Coin totalAvailableBalance;
    private double marketPriceMargin = 0;
    private Coin txFeeFromFeeService;
    private boolean marketPriceAvailable;
    private int feeTxSize = 260; // size of typical tx with 1 input
    private int feeTxSizeEstimationRecursionCounter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    CreateOfferDataModel(OpenOfferManager openOfferManager, BtcWalletService btcWalletService, BsqWalletService bsqWalletService,
                         Preferences preferences, User user, KeyRing keyRing, P2PService p2PService,
                         PriceFeedService priceFeedService, FilterManager filterManager,
                         AccountAgeWitnessService accountAgeWitnessService, TradeWalletService tradeWalletService,
                         FeeService feeService, BSFormatter formatter) {
        this.openOfferManager = openOfferManager;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.preferences = preferences;
        this.user = user;
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.filterManager = filterManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.tradeWalletService = tradeWalletService;
        this.feeService = feeService;
        this.formatter = formatter;

        offerId = Utilities.getRandomPrefix(5, 8) + "-" +
                UUID.randomUUID().toString() + "-" +
                Version.VERSION.replace(".", "");
        shortOfferId = Utilities.getShortId(offerId);
        addressEntry = btcWalletService.getOrCreateAddressEntry(offerId, AddressEntry.Context.OFFER_FUNDING);

        useMarketBasedPrice.set(preferences.isUsePercentageBasedPrice());
        buyerSecurityDeposit.set(preferences.getBuyerSecurityDepositAsCoin());
        sellerSecurityDeposit = Restrictions.getSellerSecurityDeposit();

        btcBalanceListener = new BalanceListener(getAddressEntry().getAddress()) {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance();

               /* if (isMainNet.get()) {
                    SettableFuture<Coin> future = blockchainService.requestFee(tx.getHashAsString());
                    Futures.addCallback(future, new FutureCallback<Coin>() {
                        public void onSuccess(Coin fee) {
                            UserThread.execute(() -> feeFromFundingTxProperty.set(fee));
                        }

                        public void onFailure(@NotNull Throwable throwable) {
                            UserThread.execute(() -> new Popup<>()
                                    .warning("We did not get a response for the request of the mining fee used " +
                                            "in the funding transaction.\n\n" +
                                            "Are you sure you used a sufficiently high fee of at least " +
                                            formatter.formatCoinWithCode(FeePolicy.getMinRequiredFeeForFundingTx()) + "?")
                                    .actionButtonText("Yes, I used a sufficiently high fee.")
                                    .onAction(() -> feeFromFundingTxProperty.set(FeePolicy.getMinRequiredFeeForFundingTx()))
                                    .closeButtonText("No. Let's cancel that payment.")
                                    .onClose(() -> feeFromFundingTxProperty.set(Coin.ZERO))
                                    .show());
                        }
                    });
                }*/
            }
        };

        bsqBalanceListener = (availableBalance, unverifiedBalance) -> updateBalance();

        paymentAccountsChangeListener = change -> fillPaymentAccounts();
    }

    @Override
    protected void activate() {
        addListeners();

        if (isTabSelected)
            priceFeedService.setCurrencyCode(tradeCurrencyCode.get());

        updateBalance();
    }

    @Override
    protected void deactivate() {
        removeListeners();
    }

    private void addListeners() {
        btcWalletService.addBalanceListener(btcBalanceListener);
        if (BisqEnvironment.isBaseCurrencySupportingBsq())
            bsqWalletService.addBsqBalanceListener(bsqBalanceListener);
        user.getPaymentAccountsAsObservable().addListener(paymentAccountsChangeListener);
    }


    private void removeListeners() {
        btcWalletService.removeBalanceListener(btcBalanceListener);
        if (BisqEnvironment.isBaseCurrencySupportingBsq())
            bsqWalletService.removeBsqBalanceListener(bsqBalanceListener);
        user.getPaymentAccountsAsObservable().removeListener(paymentAccountsChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // called before activate()
    boolean initWithData(OfferPayload.Direction direction, TradeCurrency tradeCurrency) {
        this.direction = direction;
        this.tradeCurrency = tradeCurrency;

        fillPaymentAccounts();

        PaymentAccount account;
        PaymentAccount lastSelectedPaymentAccount = preferences.getSelectedPaymentAccountForCreateOffer();
        if (lastSelectedPaymentAccount != null &&
                user.getPaymentAccounts() != null &&
                user.getPaymentAccounts().contains(lastSelectedPaymentAccount)) {
            account = lastSelectedPaymentAccount;
        } else {
            account = user.findFirstPaymentAccountWithCurrency(tradeCurrency);
        }

        if (account != null && isNotUSBankAccount(account)) {
            this.paymentAccount = account;
        } else {
            Optional<PaymentAccount> paymentAccountOptional = paymentAccounts.stream().findAny();
            if (paymentAccountOptional.isPresent()) {
                this.paymentAccount = paymentAccountOptional.get();

            } else {
                log.warn("PaymentAccount not available. Should never get called as in offer view you should not be able to open a create offer view");
                return false;
            }
        }

        setTradeCurrencyFromPaymentAccount(paymentAccount);
        tradeCurrencyCode.set(this.tradeCurrency.getCode());

        priceFeedService.setCurrencyCode(tradeCurrencyCode.get());

        // We request to get the actual estimated fee
        requestTxFee();

        // Set the default values (in rare cases if the fee request was not done yet we get the hard coded default values)
        // But offer creation happens usually after that so we should have already the value from the estimation service.
        txFeeFromFeeService = feeService.getTxFee(feeTxSize);

        calculateVolume();
        calculateTotalToPay();
        updateBalance();

        return true;
    }

    void onTabSelected(boolean isSelected) {
        this.isTabSelected = isSelected;
        if (isTabSelected)
            priceFeedService.setCurrencyCode(tradeCurrencyCode.get());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("ConstantConditions")
    Offer createAndGetOffer() {
        final boolean useMarketBasedPriceValue = marketPriceAvailable && useMarketBasedPrice.get();
        long priceAsLong = price.get() != null && !useMarketBasedPriceValue ? price.get().getValue() : 0L;
        String currencyCode = tradeCurrencyCode.get();
        boolean isCryptoCurrency = CurrencyUtil.isCryptoCurrency(currencyCode);
        String baseCurrencyCode = isCryptoCurrency ? currencyCode : Res.getBaseCurrencyCode();
        String counterCurrencyCode = isCryptoCurrency ? Res.getBaseCurrencyCode() : currencyCode;

        double marketPriceMarginParam = useMarketBasedPriceValue ? marketPriceMargin : 0;
        long amount = this.amount.get() != null ? this.amount.get().getValue() : 0L;
        long minAmount = this.minAmount.get() != null ? this.minAmount.get().getValue() : 0L;

        ArrayList<String> acceptedCountryCodes = null;
        if (paymentAccount instanceof SepaAccount) {
            acceptedCountryCodes = new ArrayList<>();
            acceptedCountryCodes.addAll(((SepaAccount) paymentAccount).getAcceptedCountryCodes());
        } else if (paymentAccount instanceof CountryBasedPaymentAccount) {
            acceptedCountryCodes = new ArrayList<>();
            acceptedCountryCodes.add(((CountryBasedPaymentAccount) paymentAccount).getCountry().code);
        }

        ArrayList<String> acceptedBanks = null;
        if (paymentAccount instanceof SpecificBanksAccount) {
            acceptedBanks = new ArrayList<>(((SpecificBanksAccount) paymentAccount).getAcceptedBanks());
        } else if (paymentAccount instanceof SameBankAccount) {
            acceptedBanks = new ArrayList<>();
            acceptedBanks.add(((SameBankAccount) paymentAccount).getBankId());
        }

        String bankId = paymentAccount instanceof BankAccount ? ((BankAccount) paymentAccount).getBankId() : null;

        // That is optional and set to null if not supported (AltCoins, OKPay,...)
        String countryCode = paymentAccount instanceof CountryBasedPaymentAccount ? ((CountryBasedPaymentAccount) paymentAccount).getCountry().code : null;

        checkNotNull(p2PService.getAddress(), "Address must not be null");
        checkNotNull(getMakerFee(), "makerFee must not be null");

        long maxTradeLimit = getMaxTradeLimit();
        long maxTradePeriod = paymentAccount.getPaymentMethod().getMaxTradePeriod();

        // reserved for future use cases
        // Use null values if not set
        boolean isPrivateOffer = false;
        boolean useAutoClose = false;
        boolean useReOpenAfterAutoClose = false;
        long lowerClosePrice = 0;
        long upperClosePrice = 0;
        String hashOfChallenge = null;
        HashMap<String, String> extraDataMap = null;
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            extraDataMap = new HashMap<>();
            final String myWitnessHashAsHex = accountAgeWitnessService.getMyWitnessHashAsHex(paymentAccount.getPaymentAccountPayload());
            extraDataMap.put(OfferPayload.ACCOUNT_AGE_WITNESS_HASH, myWitnessHashAsHex);
        }

        Coin buyerSecurityDepositAsCoin = buyerSecurityDeposit.get();
        checkArgument(buyerSecurityDepositAsCoin.compareTo(Restrictions.getMaxBuyerSecurityDeposit()) <= 0,
                "securityDeposit must be not exceed " +
                        Restrictions.getMaxBuyerSecurityDeposit().toFriendlyString());
        checkArgument(buyerSecurityDepositAsCoin.compareTo(Restrictions.getMinBuyerSecurityDeposit()) >= 0,
                "securityDeposit must be not be less than " +
                        Restrictions.getMinBuyerSecurityDeposit().toFriendlyString());

        checkArgument(!filterManager.isCurrencyBanned(currencyCode),
                Res.get("offerbook.warning.currencyBanned"));
        checkArgument(!filterManager.isPaymentMethodBanned(paymentAccount.getPaymentMethod()),
                Res.get("offerbook.warning.paymentMethodBanned"));

        OfferPayload offerPayload = new OfferPayload(offerId,
                new Date().getTime(),
                p2PService.getAddress(),
                keyRing.getPubKeyRing(),
                OfferPayload.Direction.valueOf(direction.name()),
                priceAsLong,
                marketPriceMarginParam,
                useMarketBasedPriceValue,
                amount,
                minAmount,
                baseCurrencyCode,
                counterCurrencyCode,
                Lists.newArrayList(user.getAcceptedArbitratorAddresses()),
                Lists.newArrayList(user.getAcceptedMediatorAddresses()),
                paymentAccount.getPaymentMethod().getId(),
                paymentAccount.getId(),
                null,
                countryCode,
                acceptedCountryCodes,
                bankId,
                acceptedBanks,
                Version.VERSION,
                btcWalletService.getLastBlockSeenHeight(),
                txFeeFromFeeService.value,
                getMakerFee().value,
                isCurrencyForMakerFeeBtc(),
                buyerSecurityDepositAsCoin.value,
                sellerSecurityDeposit.value,
                maxTradeLimit,
                maxTradePeriod,
                useAutoClose,
                useReOpenAfterAutoClose,
                upperClosePrice,
                lowerClosePrice,
                isPrivateOffer,
                hashOfChallenge,
                extraDataMap,
                Version.TRADE_PROTOCOL_VERSION);
        Offer offer = new Offer(offerPayload);
        offer.setPriceFeedService(priceFeedService);
        return offer;
    }

    // This works only if have already funds in the wallet
    public void estimateTxSize() {
        txFeeFromFeeService = feeService.getTxFee(feeTxSize);
        Address fundingAddress = btcWalletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress();
        Address reservedForTradeAddress = btcWalletService.getOrCreateAddressEntry(offerId, AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
        Address changeAddress = btcWalletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress();

        Coin reservedFundsForOffer = getSecurityDeposit();
        if (!isBuyOffer())
            reservedFundsForOffer = reservedFundsForOffer.add(amount.get());

        checkNotNull(user.getAcceptedArbitrators(), "user.getAcceptedArbitrators() must not be null");
        checkArgument(!user.getAcceptedArbitrators().isEmpty(), "user.getAcceptedArbitrators() must not be empty");
        String dummyArbitratorAddress = user.getAcceptedArbitrators().get(0).getBtcAddress();
        try {
            log.info("We create a dummy tx to see if our estimated size is in the accepted range. feeTxSize={}," +
                            " txFee based on feeTxSize: {}, recommended txFee is {} sat/byte",
                    feeTxSize, txFeeFromFeeService.toFriendlyString(), feeService.getTxFeePerByte());
            Transaction tradeFeeTx = tradeWalletService.estimateBtcTradingFeeTxSize(
                    fundingAddress,
                    reservedForTradeAddress,
                    changeAddress,
                    reservedFundsForOffer,
                    true,
                    getMakerFee(),
                    txFeeFromFeeService,
                    dummyArbitratorAddress);

            final int txSize = tradeFeeTx.bitcoinSerialize().length;
            // use feeTxSizeEstimationRecursionCounter to avoid risk for endless loop
            if (txSize > feeTxSize * 1.2 && feeTxSizeEstimationRecursionCounter < 10) {
                feeTxSizeEstimationRecursionCounter++;
                log.info("txSize is {} bytes but feeTxSize used for txFee calculation was {} bytes. We try again with an " +
                        "adjusted txFee to reach the target tx fee.", txSize, feeTxSize);
                feeTxSize = txSize;
                txFeeFromFeeService = feeService.getTxFee(feeTxSize);
                // lets try again with the adjusted txSize and fee.
                estimateTxSize();
            } else {
                log.info("feeTxSize {} bytes", feeTxSize);
                log.info("txFee based on estimated size: {}, recommended txFee is {} sat/byte",
                        txFeeFromFeeService.toFriendlyString(), feeService.getTxFeePerByte());
            }
        } catch (InsufficientMoneyException e) {
            // If we need to fund from an external wallet we can assume we only have 1 input (260 bytes).
            log.warn("We cannot do the fee estimation because there are not enough funds in the wallet. This is expected " +
                    "if the user pays from an external wallet. In that case we use an estimated tx size of 260 bytes.");
            feeTxSize = 260;
            txFeeFromFeeService = feeService.getTxFee(feeTxSize);
            log.info("feeTxSize {} bytes", feeTxSize);
            log.info("txFee based on estimated size: {}, recommended txFee is {} sat/byte",
                    txFeeFromFeeService.toFriendlyString(), feeService.getTxFeePerByte());
        }
    }

    void onPlaceOffer(Offer offer, TransactionResultHandler resultHandler) {
        checkNotNull(getMakerFee(), "makerFee must not be null");

        Coin reservedFundsForOffer = getSecurityDeposit();
        if (!isBuyOffer())
            reservedFundsForOffer = reservedFundsForOffer.add(amount.get());

        openOfferManager.placeOffer(offer,
                reservedFundsForOffer,
                useSavingsWallet,
                resultHandler,
                log::error);
    }

    void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        if (paymentAccount != null && !this.paymentAccount.equals(paymentAccount)) {
            volume.set(null);
            price.set(null);
            marketPriceMargin = 0;
            preferences.setSelectedPaymentAccountForCreateOffer(paymentAccount);
            this.paymentAccount = paymentAccount;

            setTradeCurrencyFromPaymentAccount(paymentAccount);

            long myLimit = accountAgeWitnessService.getMyTradeLimit(paymentAccount, tradeCurrencyCode.get());
            if (amount.get() != null)
                this.amount.set(Coin.valueOf(Math.min(amount.get().value, myLimit)));
        }
    }

    private void setTradeCurrencyFromPaymentAccount(PaymentAccount paymentAccount) {
        if (paymentAccount.getSingleTradeCurrency() != null)
            tradeCurrency = paymentAccount.getSingleTradeCurrency();
        else if (paymentAccount.getSelectedTradeCurrency() != null)
            tradeCurrency = paymentAccount.getSelectedTradeCurrency();
        else if (!paymentAccount.getTradeCurrencies().isEmpty())
            tradeCurrency = paymentAccount.getTradeCurrencies().get(0);

        checkNotNull(tradeCurrency, "tradeCurrency must not be null");
        tradeCurrencyCode.set(tradeCurrency.getCode());
    }

    void onCurrencySelected(TradeCurrency tradeCurrency) {
        if (tradeCurrency != null) {
            if (!this.tradeCurrency.equals(tradeCurrency)) {
                volume.set(null);
                price.set(null);
                marketPriceMargin = 0;
            }

            this.tradeCurrency = tradeCurrency;
            final String code = this.tradeCurrency.getCode();
            tradeCurrencyCode.set(code);

            if (paymentAccount != null)
                paymentAccount.setSelectedTradeCurrency(tradeCurrency);

            priceFeedService.setCurrencyCode(code);

            Optional<TradeCurrency> tradeCurrencyOptional = preferences.getTradeCurrenciesAsObservable().stream().filter(e -> e.getCode().equals(code)).findAny();
            if (!tradeCurrencyOptional.isPresent()) {
                if (CurrencyUtil.isCryptoCurrency(code)) {
                    CurrencyUtil.getCryptoCurrency(code).ifPresent(preferences::addCryptoCurrency);
                } else {
                    CurrencyUtil.getFiatCurrency(code).ifPresent(preferences::addFiatCurrency);
                }
            }
        }
    }

    void fundFromSavingsWallet() {
        this.useSavingsWallet = true;
        updateBalance();
        if (!isBtcWalletFunded.get()) {
            this.useSavingsWallet = false;
            updateBalance();
        }
    }

    void setMarketPriceMargin(double marketPriceMargin) {
        this.marketPriceMargin = marketPriceMargin;
    }

    void requestTxFee() {
        feeService.requestFees(() -> {
            txFeeFromFeeService = feeService.getTxFee(feeTxSize);
            calculateTotalToPay();
        }, null);
    }

    void setPreferredCurrencyForMakerFeeBtc(boolean preferredCurrencyForMakerFeeBtc) {
        preferences.setPayFeeInBtc(preferredCurrencyForMakerFeeBtc);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (minAmount.get() != null && amount.get() != null)
            return !minAmount.get().isGreaterThan(amount.get());
        return true;
    }

    OfferPayload.Direction getDirection() {
        return direction;
    }

    AddressEntry getAddressEntry() {
        return addressEntry;
    }

    public TradeCurrency getTradeCurrency() {
        return tradeCurrency;
    }

    public PaymentAccount getPaymentAccount() {
        return paymentAccount;
    }

    boolean hasAcceptedArbitrators() {
        final List<Arbitrator> acceptedArbitrators = user.getAcceptedArbitrators();
        return acceptedArbitrators != null && acceptedArbitrators.size() > 0;
    }

    public void setUseMarketBasedPrice(boolean useMarketBasedPrice) {
        this.useMarketBasedPrice.set(useMarketBasedPrice);
        preferences.setUsePercentageBasedPrice(useMarketBasedPrice);
    }

    /*boolean isFeeFromFundingTxSufficient() {
        return !isMainNet.get() || feeFromFundingTxProperty.get().compareTo(FeePolicy.getMinRequiredFeeForFundingTx()) >= 0;
    }*/

    public ObservableList<PaymentAccount> getPaymentAccounts() {
        return paymentAccounts;
    }

    double getMarketPriceMargin() {
        return marketPriceMargin;
    }

    boolean isMakerFeeValid() {
        return preferences.getPayFeeInBtc() || isBsqForFeeAvailable();
    }

    long getMaxTradeLimit() {
        if (paymentAccount != null)
            return accountAgeWitnessService.getMyTradeLimit(paymentAccount, tradeCurrencyCode.get());
        else
            return 0;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    void calculateVolume() {
        if (price.get() != null &&
                amount.get() != null &&
                !amount.get().isZero() &&
                !price.get().isZero()) {
            try {
                volume.set(price.get().getVolumeByAmount(amount.get()));
            } catch (Throwable t) {
                log.error(t.toString());
            }
        }

        updateBalance();
    }

    void calculateAmount() {
        if (volume.get() != null &&
                price.get() != null &&
                !volume.get().isZero() &&
                !price.get().isZero()) {
            try {
                amount.set(formatter.reduceTo4Decimals(price.get().getAmountByVolume(volume.get())));
                calculateTotalToPay();
            } catch (Throwable t) {
                log.error(t.toString());
            }
        }
    }

    void calculateTotalToPay() {
        // Maker does not pay the tx fee for the trade txs because the mining fee might be different when maker
        // created the offer and reserved his funds, so that would not work well with dynamic fees.
        // The mining fee for the createOfferFee tx is deducted from the createOfferFee and not visible to the trader
        final Coin makerFee = getMakerFee();
        if (direction != null && amount.get() != null && makerFee != null) {
            Coin feeAndSecDeposit = getTxFee().add(getSecurityDeposit());
            if (isCurrencyForMakerFeeBtc())
                feeAndSecDeposit = feeAndSecDeposit.add(makerFee);
            Coin total = isBuyOffer() ? feeAndSecDeposit : feeAndSecDeposit.add(amount.get());
            totalToPayAsCoin.set(total);
            updateBalance();
        }
    }

    Coin getSecurityDeposit() {
        return isBuyOffer() ? buyerSecurityDeposit.get() : sellerSecurityDeposit;
    }

    public boolean isBuyOffer() {
        return OfferUtil.isBuyOffer(getDirection());
    }

    private void updateBalance() {
        Coin tradeWalletBalance = btcWalletService.getBalanceForAddress(addressEntry.getAddress());
        if (useSavingsWallet) {
            Coin savingWalletBalance = btcWalletService.getSavingWalletBalance();
            totalAvailableBalance = savingWalletBalance.add(tradeWalletBalance);
            if (totalToPayAsCoin.get() != null) {
                if (totalAvailableBalance.compareTo(totalToPayAsCoin.get()) > 0)
                    balance.set(totalToPayAsCoin.get());
                else
                    balance.set(totalAvailableBalance);
            }
        } else {
            balance.set(tradeWalletBalance);
        }

        if (totalToPayAsCoin.get() != null) {
            missingCoin.set(totalToPayAsCoin.get().subtract(balance.get()));
            if (missingCoin.get().isNegative())
                missingCoin.set(Coin.ZERO);
        }

        log.debug("missingCoin " + missingCoin.get().toFriendlyString());

        isBtcWalletFunded.set(isBalanceSufficient(balance.get()));
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (totalToPayAsCoin.get() != null && isBtcWalletFunded.get() && walletFundedNotification == null && !DevEnv.DEV_MODE) {
            walletFundedNotification = new Notification()
                    .headLine(Res.get("notification.walletUpdate.headline"))
                    .notification(Res.get("notification.walletUpdate.msg", formatter.formatCoinWithCode(totalToPayAsCoin.get())))
                    .autoClose();

            walletFundedNotification.show();
        }
    }

    private boolean isBalanceSufficient(Coin balance) {
        return totalToPayAsCoin.get() != null && balance.compareTo(totalToPayAsCoin.get()) >= 0;
    }

    public Coin getTxFee() {
        if (isCurrencyForMakerFeeBtc())
            return txFeeFromFeeService;
        else
            return txFeeFromFeeService.subtract(getMakerFee());
    }

    public Preferences getPreferences() {
        return preferences;
    }

    public void swapTradeToSavings() {
        log.error("swapTradeToSavings, offerId={}", offerId);
        btcWalletService.resetAddressEntriesForOpenOffer(offerId);
    }

    private void fillPaymentAccounts() {
        if (user.getPaymentAccounts() != null) {
            paymentAccounts.setAll(user.getPaymentAccounts().stream()
                    .filter(this::isNotUSBankAccount)
                    .collect(Collectors.toSet()));
        }
    }

    private boolean isNotUSBankAccount(PaymentAccount paymentAccount) {
        //noinspection SimplifiableIfStatement
        if (paymentAccount instanceof SameCountryRestrictedBankAccount &&
                paymentAccount.getPaymentAccountPayload() instanceof BankAccountPayload)
            return !((SameCountryRestrictedBankAccount) paymentAccount).getCountryCode().equals("US");
        else
            return true;
    }

    void setAmount(Coin amount) {
        this.amount.set(amount);
    }

    public void setPrice(Price price) {
        this.price.set(price);
    }

    public void setVolume(Volume volume) {
        this.volume.set(volume);
    }

    void setBuyerSecurityDeposit(Coin buyerSecurityDeposit) {
        this.buyerSecurityDeposit.set(buyerSecurityDeposit);
        preferences.setBuyerSecurityDepositAsLong(buyerSecurityDeposit.value);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    ReadOnlyObjectProperty<Coin> getAmount() {
        return amount;
    }

    ReadOnlyObjectProperty<Coin> getMinAmount() {
        return minAmount;
    }

    ReadOnlyObjectProperty<Price> getPrice() {
        return price;
    }

    ReadOnlyObjectProperty<Volume> getVolume() {
        return volume;
    }

    void setMinAmount(Coin minAmount) {
        this.minAmount.set(minAmount);
    }

    ReadOnlyStringProperty getTradeCurrencyCode() {
        return tradeCurrencyCode;
    }

    ReadOnlyBooleanProperty getIsBtcWalletFunded() {
        return isBtcWalletFunded;
    }

    ReadOnlyBooleanProperty getUseMarketBasedPrice() {
        return useMarketBasedPrice;
    }

    ReadOnlyObjectProperty<Coin> getBuyerSecurityDeposit() {
        return buyerSecurityDeposit;
    }

    Coin getSellerSecurityDeposit() {
        return sellerSecurityDeposit;
    }

    ReadOnlyObjectProperty<Coin> totalToPayAsCoinProperty() {
        return totalToPayAsCoin;
    }

    ReadOnlyObjectProperty<Coin> getMissingCoin() {
        return missingCoin;
    }

    ReadOnlyObjectProperty<Coin> getBalance() {
        return balance;
    }

    public Coin getBsqBalance() {
        return bsqWalletService.getAvailableBalance();
    }

    public void setMarketPriceAvailable(boolean marketPriceAvailable) {
        this.marketPriceAvailable = marketPriceAvailable;
    }

    public Coin getMakerFee(boolean isCurrencyForMakerFeeBtc) {
        return OfferUtil.getMakerFee(isCurrencyForMakerFeeBtc, amount.get(), marketPriceAvailable, marketPriceMargin);
    }

    public Coin getMakerFee() {
        return OfferUtil.getMakerFee(bsqWalletService, preferences, amount.get(), marketPriceAvailable, marketPriceMargin);
    }

    public boolean isCurrencyForMakerFeeBtc() {
        return OfferUtil.isCurrencyForMakerFeeBtc(preferences, bsqWalletService, amount.get(), marketPriceAvailable, marketPriceMargin);
    }

    public boolean isBsqForFeeAvailable() {
        return OfferUtil.isBsqForFeeAvailable(bsqWalletService, amount.get(), marketPriceAvailable, marketPriceMargin);
    }
}
