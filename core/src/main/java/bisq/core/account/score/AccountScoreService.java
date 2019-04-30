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

package bisq.core.account.score;

import bisq.core.account.age.AccountCreationAgeService;
import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.CurrencyUtil;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountUtil;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.Contract;
import bisq.core.trade.Trade;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Main class for account score domain.
 * Provides access to any data related to account score. Internally it used different protection tools to constructing
 * the resulting parameters.
 */
public class AccountScoreService {
    public final static long LOW_AMOUNT_THRESHOLD = Coin.parseCoin("0.01").value;
    private final AccountCreationAgeService accountCreationAgeService;
    private final SignedWitnessService signedWitnessService;
    private final AccountAgeWitnessService accountAgeWitnessService;


    // TODO missing: If trade amount is below 0.01 BTC (about 50 USD atm) there are no delays and increased security deposit (same as now).

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AccountScoreService(AccountCreationAgeService accountCreationAgeService,
                               SignedWitnessService signedWitnessService,
                               AccountAgeWitnessService accountAgeWitnessService) {
        this.accountCreationAgeService = accountCreationAgeService;
        this.signedWitnessService = signedWitnessService;
        this.accountAgeWitnessService = accountAgeWitnessService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getPhaseOnePeriodAsMilli() {
        return accountCreationAgeService.getPhaseOnePeriodAsMilli();
    }

    public boolean ignoreRestrictions(Coin tradeAmount) {
        return tradeAmount.value <= LOW_AMOUNT_THRESHOLD;
    }

    public boolean ignoreRestrictions(Offer offer) {
        return ignoreRestrictions(offer.getPaymentMethod()) || ignoreRestrictions(offer.getAmount());
    }

    public boolean ignoreRestrictions(PaymentMethod paymentMethod) {
        switch (paymentMethod.getId()) {
            case PaymentMethod.BLOCK_CHAINS_ID:
            case PaymentMethod.BLOCK_CHAINS_INSTANT_ID:

            case PaymentMethod.US_POSTAL_MONEY_ORDER_ID:
            case PaymentMethod.HAL_CASH_ID:
            case PaymentMethod.F2F_ID:
            case PaymentMethod.MONEY_GRAM_ID:
            case PaymentMethod.WESTERN_UNION_ID:

            case PaymentMethod.SWISH_ID:
            case PaymentMethod.PERFECT_MONEY_ID:
            case PaymentMethod.ALI_PAY_ID:
            case PaymentMethod.WECHAT_PAY_ID:
            case PaymentMethod.ADVANCED_CASH_ID:
            case PaymentMethod.PROMPT_PAY_ID:
            case PaymentMethod.CASH_DEPOSIT_ID:
                return true;

            default:
                // All other bank transfer methods
                return false;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Is in phase one period
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean myMakerAccountInPhaseOnePeriod(PaymentAccount myPaymentAccount, String currencyCode, OfferPayload.Direction direction) {
        return accountCreationAgeService.myMakerAccountInPhaseOnePeriod(myPaymentAccount, currencyCode, direction);
    }

    public boolean offerInPhaseOnePeriod(Offer offer) {
        return accountCreationAgeService.offerInPhaseOnePeriod(offer);
    }

    public boolean tradeInPhaseOnePeriod(Trade trade) {
        return accountCreationAgeService.tradeInPhaseOnePeriod(trade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Is delay required
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean myMakerAccountRequiresPayoutDelay(String currencyCode, OfferPayload.Direction direction) {
        return accountCreationAgeService.myMakerAccountRequiresPayoutDelay(currencyCode, direction);
    }

    public boolean offerRequirePayoutDelay(Offer offer) {
        return accountCreationAgeService.offerRequirePayoutDelay(offer);
    }

    public boolean tradeRequirePayoutDelay(Trade trade) {
        return accountCreationAgeService.tradeRequirePayoutDelay(trade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delay
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getDelayForMyOffer(PaymentAccount myPaymentAccount, String currencyCode, OfferPayload.Direction direction) {
        return accountCreationAgeService.getDelayForMyOffer(myPaymentAccount, currencyCode, direction);
    }

    public long getDelayForOffer(Offer offer) {
        return accountCreationAgeService.getDelayForOffer(offer);
    }

    public Date getDelayedTradePayoutDate(Trade trade) {
        return accountCreationAgeService.getDelayedTradePayoutDate(trade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ScoreInfo
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<ScoreInfo> getScoreInfoForMyOffer(PaymentAccount myPaymentAccount, String currencyCode, OfferPayload.Direction direction) {
        long requiredDelay = accountCreationAgeService.getDelayForMyOffer(myPaymentAccount, currencyCode, direction);
        return getMyScoreInfo(myPaymentAccount, currencyCode, requiredDelay);
    }

    public Optional<ScoreInfo> getScoreInfoForMyPaymentAccount(PaymentAccount myPaymentAccount, String currencyCode) {
        long requiredDelay = accountCreationAgeService.getDelayForMyPaymentAccount(myPaymentAccount, currencyCode);
        return getMyScoreInfo(myPaymentAccount, currencyCode, requiredDelay);
    }

    private Optional<ScoreInfo> getMyScoreInfo(PaymentAccount myPaymentAccount, String currencyCode, long requiredDelay) {
        if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
            return Optional.empty();
        }

        Optional<AccountScoreCategory> accountScoreCategory = getMyAccountScoreCategory(myPaymentAccount, currencyCode);
        checkArgument(accountScoreCategory.isPresent(), "accountScoreCategory must be present");
        long accountAge = accountAgeWitnessService.getMyAccountAge(myPaymentAccount.getPaymentAccountPayload());

        Optional<Long> signedTradeAge;
        List<Long> myWitnessAgeList = signedWitnessService.getMyWitnessAgeList(myPaymentAccount.getPaymentAccountPayload());
        if (!myWitnessAgeList.isEmpty()) {
            signedTradeAge = Optional.of(myWitnessAgeList.get(0));
        } else {
            signedTradeAge = Optional.empty();
        }

        Coin minDepositAsCoin = getMyAccountMinDepositAsCoin(myPaymentAccount, currencyCode);
        double minDepositAsPercent = getMyAccountMinDepositAsPercent(myPaymentAccount, currencyCode);
        boolean canSign = signedTradeAge.isPresent() && signedTradeAge.get() > getPhaseOnePeriodAsMilli();
        return Optional.of(new ScoreInfo(accountScoreCategory.get(),
                accountAge,
                signedTradeAge,
                minDepositAsCoin,
                minDepositAsPercent,
                requiredDelay,
                canSign));
    }

    public Optional<ScoreInfo> getScoreInfoForMaker(Offer offer) {
        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return Optional.empty();
        }

        Optional<AccountScoreCategory> accountScoreCategory = getAccountScoreCategoryForMaker(offer);
        checkArgument(accountScoreCategory.isPresent(), "accountScoreCategory must be present");
        long accountAge = accountAgeWitnessService.getMakersAccountAge(offer);

        Optional<Long> signedTradeAge = Optional.empty();
        ;
        Optional<String> accountAgeWitnessHash = offer.getAccountAgeWitnessHashAsHex();
        Optional<AccountAgeWitness> witnessByHashAsHex = accountAgeWitnessHash.isPresent() ?
                accountAgeWitnessService.getWitnessByHashAsHex(accountAgeWitnessHash.get()) :
                Optional.empty();
        if (witnessByHashAsHex.isPresent()) {
            List<Long> myWitnessAgeList = signedWitnessService.getVerifiedWitnessAgeList(witnessByHashAsHex.get());
            if (!myWitnessAgeList.isEmpty()) {
                signedTradeAge = Optional.of(myWitnessAgeList.get(0));
            }
        }

        Coin minDepositAsCoin = getMinDepositAsCoin(offer);
        double minDepositAsPercent = getMinDepositAsPercent(offer);
        long requiredDelay = accountCreationAgeService.getDelayForOffer(offer);
        boolean canSign = signedTradeAge.isPresent() && signedTradeAge.get() > getPhaseOnePeriodAsMilli();
        return Optional.of(new ScoreInfo(accountScoreCategory.get(),
                accountAge,
                signedTradeAge,
                minDepositAsCoin,
                minDepositAsPercent,
                requiredDelay,
                canSign));
    }


    public Optional<ScoreInfo> getScoreInfoForBuyer(Trade trade) {
        Offer offer = trade.getOffer();
        if (offer == null) {
            return Optional.empty();
        }

        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return Optional.empty();
        }

        Contract contract = trade.getContract();
        if (contract == null) {
            return Optional.empty();
        }

        Optional<Long> signedTradeAge = Optional.empty();
        ;
        Optional<AccountAgeWitness> witness = accountAgeWitnessService.findWitness(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        if (witness.isPresent()) {
            List<Long> witnessAgeList = signedWitnessService.getVerifiedWitnessAgeList(witness.get());
            if (!witnessAgeList.isEmpty()) {
                signedTradeAge = Optional.of(witnessAgeList.get(0));
            }
        }

        Optional<AccountScoreCategory> accountScoreCategory = getAccountScoreCategoryOfBuyer(trade);
        checkArgument(accountScoreCategory.isPresent(), "accountScoreCategory must be present");
        long buyersAccountAge = accountAgeWitnessService.getAccountAge(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        Coin minDepositAsCoin = getMinDepositAsCoin(trade);
        double minDepositAsPercent = getMinDepositAsPercent(trade);
        long requiredDelay = accountCreationAgeService.getDelay(trade);
        boolean canSign = signedTradeAge.isPresent() && signedTradeAge.get() > getPhaseOnePeriodAsMilli();
        return Optional.of(new ScoreInfo(accountScoreCategory.get(),
                buyersAccountAge,
                signedTradeAge,
                minDepositAsCoin,
                minDepositAsPercent,
                requiredDelay,
                canSign));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DepositAsCoin
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getMyAccountMinDepositAsCoin(PaymentAccount myPaymentAccount, String currencyCode) {
        Coin minBuyerSecurityDepositAsCoin = Restrictions.getMinBuyerSecurityDepositAsCoin();
        if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
            return minBuyerSecurityDepositAsCoin;
        }

        List<Long> myWitnessAgeList = signedWitnessService.getMyWitnessAgeList(myPaymentAccount.getPaymentAccountPayload());
        if (!myWitnessAgeList.isEmpty()) {
            long oldestAge = myWitnessAgeList.get(0);
            if (oldestAge >= getPhaseOnePeriodAsMilli()) {
                return minBuyerSecurityDepositAsCoin;
            }
        }
        // No signature yet or it is too young to be considered, so we use the deposit based on the account age.
        return accountCreationAgeService.getMyAccountMinDepositAsCoin(myPaymentAccount);
    }

    public Coin getMinDepositAsCoin(Offer offer) {
        Coin minBuyerSecurityDepositAsCoin = Restrictions.getMinBuyerSecurityDepositAsCoin();
        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return minBuyerSecurityDepositAsCoin;
        }

        Optional<String> accountAgeWitnessHash = offer.getAccountAgeWitnessHashAsHex();
        Optional<AccountAgeWitness> witnessByHashAsHex = accountAgeWitnessHash.isPresent() ?
                accountAgeWitnessService.getWitnessByHashAsHex(accountAgeWitnessHash.get()) :
                Optional.empty();
        if (witnessByHashAsHex.isPresent()) {
            List<Long> myWitnessAgeList = signedWitnessService.getVerifiedWitnessAgeList(witnessByHashAsHex.get());
            if (!myWitnessAgeList.isEmpty()) {
                long oldestAge = myWitnessAgeList.get(0);
                if (oldestAge >= getPhaseOnePeriodAsMilli()) {
                    return minBuyerSecurityDepositAsCoin;
                }
            }
        }

        // No signature yet or it is too young to be considered, so we use the deposit based on the account age.
        return accountCreationAgeService.getMinDepositAsCoin(offer);
    }

    public Coin getMinDepositAsCoin(Trade trade) {
        Coin minBuyerSecurityDepositAsCoin = Restrictions.getMinBuyerSecurityDepositAsCoin();
        Offer offer = trade.getOffer();
        if (offer == null) {
            return minBuyerSecurityDepositAsCoin; // unexpected case
        }

        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return minBuyerSecurityDepositAsCoin;
        }

        Contract contract = trade.getContract();
        if (contract == null) {
            return minBuyerSecurityDepositAsCoin;
        }

        Optional<AccountAgeWitness> witness = accountAgeWitnessService.findWitness(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        if (witness.isPresent()) {
            List<Long> witnessAgeList = signedWitnessService.getVerifiedWitnessAgeList(witness.get());
            if (!witnessAgeList.isEmpty()) {
                long oldestAge = witnessAgeList.get(0);
                if (oldestAge >= getPhaseOnePeriodAsMilli()) {
                    return minBuyerSecurityDepositAsCoin;
                }
            }
        }

        // No signature yet or it is too young to be considered, so we use the deposit based on the account age.
        return accountCreationAgeService.getMinDepositAsCoin(trade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DepositAsPercent
    ///////////////////////////////////////////////////////////////////////////////////////////

    public double getMyAccountMinDepositAsPercent(PaymentAccount myPaymentAccount, String currencyCode) {
        double minBuyerSecurityDepositAsPercent = Restrictions.getMinBuyerSecurityDepositAsPercent(myPaymentAccount);
        if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
            return minBuyerSecurityDepositAsPercent;
        }

        List<Long> myWitnessAgeList = signedWitnessService.getMyWitnessAgeList(myPaymentAccount.getPaymentAccountPayload());
        if (!myWitnessAgeList.isEmpty()) {
            long oldestAge = myWitnessAgeList.get(0);
            if (oldestAge >= getPhaseOnePeriodAsMilli()) {
                return minBuyerSecurityDepositAsPercent;
            }
        }
        // No signature yet or it is too young to be considered, so we use the deposit based on the account age.
        return accountCreationAgeService.getMyAccountMinDepositAsPercent(myPaymentAccount);
    }

    public double getMinDepositAsPercent(Offer offer) {
        boolean cryptoCurrencyAccount = PaymentAccountUtil.isCryptoCurrencyAccount(offer.getPaymentMethod());
        double minBuyerSecurityDepositAsPercent = Restrictions.getMinBuyerSecurityDepositAsPercent(cryptoCurrencyAccount);
        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return minBuyerSecurityDepositAsPercent;
        }

        Optional<String> accountAgeWitnessHash = offer.getAccountAgeWitnessHashAsHex();
        Optional<AccountAgeWitness> witnessByHashAsHex = accountAgeWitnessHash.isPresent() ?
                accountAgeWitnessService.getWitnessByHashAsHex(accountAgeWitnessHash.get()) :
                Optional.empty();
        if (witnessByHashAsHex.isPresent()) {
            List<Long> myWitnessAgeList = signedWitnessService.getVerifiedWitnessAgeList(witnessByHashAsHex.get());
            if (!myWitnessAgeList.isEmpty()) {
                long oldestAge = myWitnessAgeList.get(0);
                if (oldestAge >= getPhaseOnePeriodAsMilli()) {
                    return minBuyerSecurityDepositAsPercent;
                }
            }
        }

        // No signature yet or it is too young to be considered, so we use the deposit based on the account age.
        return accountCreationAgeService.getMinDepositAsPercent(offer);
    }

    public double getMinDepositAsPercent(Trade trade) {
        Offer offer = trade.getOffer();
        if (offer == null) {
            return 0.05; // unexpected case
        }
        boolean cryptoCurrencyAccount = PaymentAccountUtil.isCryptoCurrencyAccount(offer.getPaymentMethod());
        double minBuyerSecurityDepositAsPercent = Restrictions.getMinBuyerSecurityDepositAsPercent(cryptoCurrencyAccount);

        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return minBuyerSecurityDepositAsPercent;
        }

        Contract contract = trade.getContract();
        if (contract == null) {
            return minBuyerSecurityDepositAsPercent;
        }

        Optional<AccountAgeWitness> witness = accountAgeWitnessService.findWitness(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        if (witness.isPresent()) {
            List<Long> witnessAgeList = signedWitnessService.getVerifiedWitnessAgeList(witness.get());
            if (!witnessAgeList.isEmpty()) {
                long oldestAge = witnessAgeList.get(0);
                if (oldestAge >= getPhaseOnePeriodAsMilli()) {
                    return minBuyerSecurityDepositAsPercent;
                }
            }
        }

        // No signature yet or it is too young to be considered, so we use the deposit based on the account age.
        return accountCreationAgeService.getMinDepositAsPercent(trade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AccountScoreCategory
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<AccountScoreCategory> getMyAccountScoreCategory(PaymentAccount myPaymentAccount, String currencyCode) {
        if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
            return Optional.empty();
        }

        List<Long> myWitnessAgeList = signedWitnessService.getMyWitnessAgeList(myPaymentAccount.getPaymentAccountPayload());
        if (myWitnessAgeList.isEmpty()) {
            // Nobody has signed my witness object yet, so I am considered as an account which has never traded.
            long myAccountAge = accountAgeWitnessService.getMyAccountAge(myPaymentAccount.getPaymentAccountPayload());
            return Optional.of(getAccountScoreCategory(myAccountAge, false));
        } else {
            long oldestAge = myWitnessAgeList.get(0);
            return Optional.of(getAccountScoreCategory(oldestAge, true));
        }
    }

    public Optional<AccountScoreCategory> getAccountScoreCategoryForMaker(Offer offer) {
        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return Optional.empty();
        }

        Optional<String> accountAgeWitnessHash = offer.getAccountAgeWitnessHashAsHex();
        Optional<AccountAgeWitness> witnessByHashAsHex = accountAgeWitnessHash.isPresent() ?
                accountAgeWitnessService.getWitnessByHashAsHex(accountAgeWitnessHash.get()) :
                Optional.empty();
        if (witnessByHashAsHex.isPresent()) {
            List<Long> myWitnessAgeList = signedWitnessService.getVerifiedWitnessAgeList(witnessByHashAsHex.get());
            if (!myWitnessAgeList.isEmpty()) {
                long oldestAge = myWitnessAgeList.get(0);
                return Optional.of(getAccountScoreCategory(oldestAge, true));
            }
        }

        long makersAccountAge = accountAgeWitnessService.getMakersAccountAge(offer);
        return Optional.of(getAccountScoreCategory(makersAccountAge, false));
    }

    public Optional<AccountScoreCategory> getAccountScoreCategoryOfBuyer(Trade trade) {
        Offer offer = trade.getOffer();
        if (offer == null) {
            return Optional.empty();
        }

        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return Optional.empty();
        }

        Contract contract = trade.getContract();
        if (contract == null) {
            return Optional.empty();
        }

        Optional<AccountAgeWitness> witness = accountAgeWitnessService.findWitness(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        if (witness.isPresent()) {
            List<Long> witnessAgeList = signedWitnessService.getVerifiedWitnessAgeList(witness.get());
            if (!witnessAgeList.isEmpty()) {
                long oldestAge = witnessAgeList.get(0);
                return Optional.of(getAccountScoreCategory(oldestAge, true));
            }
        }

        long buyersAccountAge = accountAgeWitnessService.getAccountAge(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        return Optional.of(getAccountScoreCategory(buyersAccountAge, false));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AccountScoreCategory getAccountScoreCategory(long accountAge, boolean isSignedWitness) {
        if (isSignedWitness) {
            long phaseOnePeriod = getPhaseOnePeriodAsMilli();
            if (accountAge >= 2 * phaseOnePeriod) {
                return AccountScoreCategory.GOLD;
            } else if (accountAge >= phaseOnePeriod) {
                return AccountScoreCategory.SILVER;
            } else {
                return AccountScoreCategory.BRONZE;
            }
        } else {
            return AccountScoreCategory.BRONZE;
        }
    }
}
