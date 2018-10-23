package bisq.httpapi.service.endpoint;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.filter.FilterManager;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.TxFeeEstimation;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import com.google.common.collect.Lists;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import static bisq.core.payment.PaymentAccountUtil.isPaymentAccountValidForOffer;



import bisq.httpapi.exceptions.IncompatiblePaymentAccountException;
import bisq.httpapi.exceptions.NoAcceptedArbitratorException;
import bisq.httpapi.exceptions.PaymentAccountNotFoundException;
import bisq.httpapi.model.Market;
import javax.validation.ValidationException;

public class OfferBuilder {
    private final TradeWalletService tradeWalletService;
    private final FeeService feeService;
    private final KeyRing keyRing;
    private final ReferralIdService referralIdService;
    private final FilterManager filterManager;
    private final P2PService p2PService;
    private final Preferences preferences;
    private final PriceFeedService priceFeedService;
    private final User user;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;

    @Inject
    public OfferBuilder(AccountAgeWitnessService accountAgeWitnessService,
                        BsqWalletService bsqWalletService,
                        BtcWalletService btcWalletService,
                        TradeWalletService tradeWalletService,
                        FeeService feeService, KeyRing keyRing,
                        ReferralIdService referralIdService,
                        FilterManager filterManager,
                        P2PService p2PService,
                        Preferences preferences,
                        PriceFeedService priceFeedService,
                        User user) {
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.tradeWalletService = tradeWalletService;
        this.feeService = feeService;
        this.keyRing = keyRing;
        this.referralIdService = referralIdService;
        this.filterManager = filterManager;
        this.p2PService = p2PService;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
        this.user = user;
    }

    public Offer build(@Nullable String offerId,
                       String accountId,
                       OfferPayload.Direction direction,
                       long amount,
                       long minAmount,
                       boolean useMarketBasedPriceValue,
                       @Nullable Double marketPriceMargin,
                       String marketPair,
                       long priceAsLong,
                       @Nullable Long buyerSecurityDeposit)
            throws NoAcceptedArbitratorException, PaymentAccountNotFoundException, IncompatiblePaymentAccountException {
        List<NodeAddress> acceptedArbitratorAddresses = user.getAcceptedArbitratorAddresses();
        if (null == acceptedArbitratorAddresses || acceptedArbitratorAddresses.size() == 0) {
            throw new NoAcceptedArbitratorException("No arbitrator has been chosen");
        }

        // Checked that if fixed we have a fixed price, if percentage we have a percentage
        if (marketPriceMargin == null && useMarketBasedPriceValue) {
            throw new ValidationException("When choosing PERCENTAGE price marketPriceMargin must be set");
        } else if (priceAsLong == 0 && !useMarketBasedPriceValue) {
            throw new ValidationException("When choosing FIXED price fiatPrice must be set with a price > 0");
        }

        Optional<PaymentAccount> optionalAccount = getPaymentAccounts().stream()
                .filter(account -> account.getId().equals(accountId))
                .findFirst();
        if (!optionalAccount.isPresent()) {
            throw new PaymentAccountNotFoundException("Could not find payment account with id: " + accountId);
        }

        validateMarketPair(marketPair);

        // Handle optional data and set default values if not set
        if (buyerSecurityDeposit == null)
            buyerSecurityDeposit = preferences.getBuyerSecurityDepositAsCoin().value;

        if (marketPriceMargin == null)
            marketPriceMargin = 0d;

        offerId = offerId == null ? UUID.randomUUID().toString() : offerId;

        // fix marketPair if it's lowercase
        marketPair = marketPair.toUpperCase();


        Market market = new Market(marketPair);
        // BTC_USD for fiat or XMR_BTC for altcoins
        // baseCurrencyCode is always left side, counterCurrencyCode right side
        String baseCurrencyCode = market.getLsymbol();
        String counterCurrencyCode = market.getRsymbol();

        String currencyCode = market.getLsymbol().endsWith("BTC") ? market.getRsymbol() : market.getLsymbol();

        PaymentAccount paymentAccount = optionalAccount.get();
        ArrayList<String> acceptedCountryCodes = OfferUtil.getAcceptedCountryCodes(paymentAccount);
        ArrayList<String> acceptedBanks = OfferUtil.getAcceptedBanks(paymentAccount);
        String bankId = OfferUtil.getBankId(paymentAccount);
        String countryCode = OfferUtil.getCountryCode(paymentAccount);
        long maxTradeLimit = accountAgeWitnessService.getMyTradeLimit(paymentAccount, currencyCode);
        long maxTradePeriod = paymentAccount.getPaymentMethod().getMaxTradePeriod();

        boolean isPrivateOffer = false;
        boolean useAutoClose = false;
        boolean useReOpenAfterAutoClose = false;
        long lowerClosePrice = 0;
        long upperClosePrice = 0;
        String hashOfChallenge = null;
        Map<String, String> extraDataMap = OfferUtil.getExtraDataMap(accountAgeWitnessService, referralIdService,
                paymentAccount, currencyCode);
        Coin amountAsCoin = Coin.valueOf(amount);
        boolean marketPriceAvailable = MarketEndpoint.isMarketPriceAvailable();
        Coin makerFeeAsCoin = OfferUtil.getMakerFee(bsqWalletService, preferences, amountAsCoin, marketPriceAvailable, marketPriceMargin);
        // Throws runtime exception if data are invalid
        Coin buyerSecurityDepositAsCoin = Coin.valueOf(buyerSecurityDeposit);
        OfferUtil.validateOfferData(filterManager, p2PService, buyerSecurityDepositAsCoin, paymentAccount, currencyCode, makerFeeAsCoin);

        boolean isCurrencyForMakerFeeBtc = OfferUtil.isCurrencyForMakerFeeBtc(preferences, bsqWalletService, amountAsCoin, marketPriceAvailable, marketPriceMargin);
        long sellerSecurityDeposit = Restrictions.getSellerSecurityDeposit().value;

        Coin fundsNeededForMaker = OfferUtil.getFundsNeededForOffer(amountAsCoin, buyerSecurityDepositAsCoin, direction);
        Coin makerFee = OfferUtil.getMakerFee(bsqWalletService, preferences, amountAsCoin, marketPriceAvailable, marketPriceMargin);
        if (makerFee == null)
            throw new ValidationException("makerFee must not be null");

        Tuple2<Coin, Integer> estimatedFeeAndTxSize = TxFeeEstimation.getEstimatedFeeAndTxSizeForMaker(fundsNeededForMaker,
                makerFee,
                feeService,
                btcWalletService,
                preferences);

        Coin txFeeFromFeeService = estimatedFeeAndTxSize.first;

        OfferPayload offerPayload = new OfferPayload(
                offerId,
                new Date().getTime(),
                p2PService.getAddress(),
                keyRing.getPubKeyRing(),
                direction,
                priceAsLong,
                marketPriceMargin,
                useMarketBasedPriceValue,
                amount,
                minAmount,
                baseCurrencyCode,
                counterCurrencyCode,
                Lists.newArrayList(acceptedArbitratorAddresses),
                Lists.newArrayList(user.getAcceptedMediatorAddresses()),
                paymentAccount.getPaymentMethod().getId(),
                paymentAccount.getId(),
                null, // will be filled in by BroadcastMakerFeeTx class
                countryCode,
                acceptedCountryCodes,
                bankId,
                acceptedBanks,
                Version.VERSION,
                btcWalletService.getLastBlockSeenHeight(),
                txFeeFromFeeService.value,
                makerFeeAsCoin.value,
                isCurrencyForMakerFeeBtc,
                buyerSecurityDeposit,
                sellerSecurityDeposit,
                maxTradeLimit,
                maxTradePeriod,
                useAutoClose,
                useReOpenAfterAutoClose,
                upperClosePrice,
                lowerClosePrice,
                isPrivateOffer,
                hashOfChallenge,
                extraDataMap,
                Version.TRADE_PROTOCOL_VERSION
        );

        Offer offer = new Offer(offerPayload);
        offer.setPriceFeedService(priceFeedService);

        if (!isPaymentAccountValidForOffer(offer, paymentAccount)) {
            final String errorMessage = "PaymentAccount is not valid for offer, needs " + offer.getCurrencyCode();
            throw new IncompatiblePaymentAccountException(errorMessage);
        }

        return offer;
    }


    private Set<PaymentAccount> getPaymentAccounts() {
        final Set<PaymentAccount> paymentAccounts = user.getPaymentAccounts();
        return null == paymentAccounts ? Collections.<PaymentAccount>emptySet() : paymentAccounts;
    }

    private void validateMarketPair(String marketPair) {
        if (StringUtils.isEmpty(marketPair)) {
            throw new ValidationException("The marketPair cannot be empty");
        } else if (!marketPair.equals(marketPair.toUpperCase())) {
            throw new ValidationException("The marketPair must be uppercase: " + marketPair);
        } else {
            boolean existingPair = MarketEndpoint.getMarketList().markets.stream()
                    .filter(market -> market.getPair().equals(marketPair))
                    .count() == 1;
            if (!existingPair) {
                throw new ValidationException("There is no valid market pair called: " + marketPair +
                        ". Note that market pairs are uppercase and are separated by an underscore: " +
                        "e.g. XMR_BTC or BTC_EUR");
            }
        }
    }
}
