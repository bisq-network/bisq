package network.bisq.api;

import com.google.inject.Inject;
import network.bisq.api.model.Market;
import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.core.locale.CurrencyUtil;
import bisq.common.util.MathUtils;
import bisq.core.app.BisqEnvironment;
import bisq.core.btc.Restrictions;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.*;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.CoinUtil;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.Coin;

import javax.annotation.Nullable;
import javax.validation.ValidationException;
import java.util.*;

import static bisq.core.payment.PaymentAccountUtil.isPaymentAccountValidForOffer;

public class OfferBuilder {

    private final FeeService feeService;
    private final KeyRing keyRing;
    private final P2PService p2PService;
    private final Preferences preferences;
    private final PriceFeedService priceFeedService;
    private final User user;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private boolean marketPriceAvailable;

    @Inject
    public OfferBuilder(AccountAgeWitnessService accountAgeWitnessService, BsqWalletService bsqWalletService, BtcWalletService btcWalletService, FeeService feeService, KeyRing keyRing, P2PService p2PService, Preferences preferences, PriceFeedService priceFeedService, User user) {
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.feeService = feeService;
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
        this.user = user;
    }

    public Offer build(String offerId, String accountId, OfferPayload.Direction direction, long amount, long minAmount,
                       boolean useMarketBasedPrice, Double marketPriceMargin, String marketPair, long fiatPrice, Long buyerSecurityDeposit) throws NoAcceptedArbitratorException, PaymentAccountNotFoundException, IncompatiblePaymentAccountException {
        final List<NodeAddress> acceptedArbitratorAddresses = user.getAcceptedArbitratorAddresses();
        if (null == acceptedArbitratorAddresses || acceptedArbitratorAddresses.size() == 0) {
            throw new NoAcceptedArbitratorException("No arbitrator has been chosen");
        }

        // Checked that if fixed we have a fixed price, if percentage we have a percentage
        if (marketPriceMargin == null && useMarketBasedPrice) {
            throw new ValidationException("When choosing PERCENTAGE price, fill in percentageFromMarketPrice");
        } else if (0 == fiatPrice && !useMarketBasedPrice) {
            throw new ValidationException("When choosing FIXED price, fill in fixedPrice with a price > 0");
        }
        if (null == marketPriceMargin)
            marketPriceMargin = 0d;
        // fix marketPair if it's lowercase
        marketPair = marketPair.toUpperCase();

        checkMarketValidity(marketPair);

        Market market = new Market(marketPair);
        // if right side is fiat, then left is base currency.
        // else right side is base currency.
        final String currencyCode = market.getRsymbol();
        final boolean isFiatCurrency = CurrencyUtil.isFiatCurrency(currencyCode);
        String baseCurrencyCode = !isFiatCurrency ? currencyCode : market.getLsymbol();
        String counterCurrencyCode = !isFiatCurrency ? market.getLsymbol() : currencyCode;

        Optional<PaymentAccount> optionalAccount = getPaymentAccounts().stream()
                .filter(account1 -> account1.getId().equals(accountId)).findFirst();
        if (!optionalAccount.isPresent()) {
            throw new PaymentAccountNotFoundException("Could not find payment account with id: " + accountId);
        }
        PaymentAccount paymentAccount = optionalAccount.get();

        // COPIED from CreateDataOfferModel: TODO refactor uit of GUI module  /////////////////////////////
        String countryCode = paymentAccount instanceof CountryBasedPaymentAccount ? ((CountryBasedPaymentAccount) paymentAccount).getCountry().code : null;
        ArrayList<String> acceptedCountryCodes = null;
        if (paymentAccount instanceof SepaAccount) {
            acceptedCountryCodes = new ArrayList<>();
            acceptedCountryCodes.addAll(((SepaAccount) paymentAccount).getAcceptedCountryCodes());
        } else if (paymentAccount instanceof CountryBasedPaymentAccount) {
            acceptedCountryCodes = new ArrayList<>();
            acceptedCountryCodes.add(((CountryBasedPaymentAccount) paymentAccount).getCountry().code);
        }
        String bankId = paymentAccount instanceof BankAccount ? ((BankAccount) paymentAccount).getBankId() : null;
        ArrayList<String> acceptedBanks = null;
        if (paymentAccount instanceof SpecificBanksAccount) {
            acceptedBanks = new ArrayList<>(((SpecificBanksAccount) paymentAccount).getAcceptedBanks());
        } else if (paymentAccount instanceof SameBankAccount) {
            acceptedBanks = new ArrayList<>();
            acceptedBanks.add(((SameBankAccount) paymentAccount).getBankId());
        }
        long maxTradeLimit = paymentAccount.getPaymentMethod().getMaxTradeLimitAsCoin(baseCurrencyCode).value;
        long maxTradePeriod = paymentAccount.getPaymentMethod().getMaxTradePeriod();
        boolean isPrivateOffer = false;
        boolean useAutoClose = false;
        boolean useReOpenAfterAutoClose = false;
        long lowerClosePrice = 0;
        long upperClosePrice = 0;
        String hashOfChallenge = null;
        HashMap<String, String> extraDataMap = null;
        if (isFiatCurrency) {
            extraDataMap = new HashMap<>();
            final String myWitnessHashAsHex = accountAgeWitnessService.getMyWitnessHashAsHex(paymentAccount.getPaymentAccountPayload());
            extraDataMap.put(OfferPayload.ACCOUNT_AGE_WITNESS_HASH, myWitnessHashAsHex);
        }

        // COPIED from CreateDataOfferModel /////////////////////////////

        updateMarketPriceAvailable(baseCurrencyCode);

        // TODO dummy values in this constructor !!!
        Coin coinAmount = Coin.valueOf(amount);
        if (null == buyerSecurityDeposit) {
            buyerSecurityDeposit = preferences.getBuyerSecurityDepositAsCoin().value;
        }
        OfferPayload offerPayload = new OfferPayload(
                null == offerId ? UUID.randomUUID().toString() : offerId,
                new Date().getTime(),
                p2PService.getAddress(),
                keyRing.getPubKeyRing(),
                direction,
                fiatPrice,
                marketPriceMargin,
                useMarketBasedPrice,
                amount,
                minAmount,
                baseCurrencyCode,
                counterCurrencyCode,
                acceptedArbitratorAddresses,
                user.getAcceptedMediatorAddresses(),
                paymentAccount.getPaymentMethod().getId(),
                paymentAccount.getId(),
                null, // will be filled in by BroadcastMakerFeeTx class
                countryCode,
                acceptedCountryCodes,
                bankId,
                acceptedBanks,
                Version.VERSION,
                btcWalletService.getLastBlockSeenHeight(),
                feeService.getTxFee(600).value, // default also used in code CreateOfferDataModel
                getMakerFee(coinAmount, marketPriceMargin).value,
                preferences.getPayFeeInBtc() || !isBsqForFeeAvailable(coinAmount, marketPriceMargin),
                buyerSecurityDeposit,
                Restrictions.getSellerSecurityDeposit().value,
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

        if (null == getMakerFee(false, Coin.valueOf(amount), marketPriceMargin)) {
            throw new ValidationException("makerFee must not be null");
        }
        return offer;
    }

    @Nullable
    private Coin getMakerFee(Coin amount, double marketPriceMargin) {
        final boolean currencyForMakerFeeBtc = isCurrencyForMakerFeeBtc(amount, marketPriceMargin);
        return getMakerFee(currencyForMakerFeeBtc, amount, marketPriceMargin);
    }

    private boolean isCurrencyForMakerFeeBtc(Coin amount, double marketPriceMargin) {
        return preferences.getPayFeeInBtc() || !isBsqForFeeAvailable(amount, marketPriceMargin);
    }

    private boolean isBsqForFeeAvailable(Coin amount, double marketPriceMargin) {
        return BisqEnvironment.isBaseCurrencySupportingBsq() &&
                getMakerFee(false, amount, marketPriceMargin) != null &&
                bsqWalletService.getAvailableBalance() != null &&
                getMakerFee(false, amount, marketPriceMargin) != null &&
                !bsqWalletService.getAvailableBalance().subtract(getMakerFee(false, amount, marketPriceMargin)).isNegative();
    }

    @Nullable
    private Coin getMakerFee(boolean isCurrencyForMakerFeeBtc, Coin amount, double marketPriceMargin) {
        if (amount != null) {
            final Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getMakerFeePerBtc(isCurrencyForMakerFeeBtc), amount);
            double makerFeeAsDouble = (double) feePerBtc.value;
            if (marketPriceAvailable) {
                if (marketPriceMargin > 0)
                    makerFeeAsDouble = makerFeeAsDouble * Math.sqrt(marketPriceMargin * 100);
                else
                    makerFeeAsDouble = 0;
                // For BTC we round so min value change is 100 satoshi
                if (isCurrencyForMakerFeeBtc)
                    makerFeeAsDouble = MathUtils.roundDouble(makerFeeAsDouble / 100, 0) * 100;
            }

            return CoinUtil.maxCoin(Coin.valueOf(MathUtils.doubleToLong(makerFeeAsDouble)), FeeService.getMinMakerFee(isCurrencyForMakerFeeBtc));
        } else {
            return null;
        }
    }

    private Set<PaymentAccount> getPaymentAccounts() {
        final Set<PaymentAccount> paymentAccounts = user.getPaymentAccounts();
        return null == paymentAccounts ? Collections.<PaymentAccount>emptySet() : paymentAccounts;
    }

    private void updateMarketPriceAvailable(String baseCurrencyCode) {
        marketPriceAvailable = null != priceFeedService.getMarketPrice(baseCurrencyCode);
    }

    private void checkMarketValidity(String marketPair) {
        if (StringUtils.isEmpty(marketPair)) {
            throw new ValidationException("The marketPair cannot be empty");
        } else if (!marketPair.equals(marketPair.toUpperCase())) {
            throw new ValidationException("The marketPair should be uppercase: " + marketPair);
        } else {
            final boolean existingPair = BisqProxy.calculateMarketList().markets.stream().filter(market -> market.getPair().equals(marketPair)).count() == 1;
            if (!existingPair) {
                throw new ValidationException("There is no valid market pair called: " + marketPair + ". Note that market pairs are uppercase and are separated by an underscore: XMR_BTC");
            }
        }
    }

}
