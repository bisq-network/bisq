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

package bisq.core.account.age;

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

import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.lang3.time.DateUtils;

import java.util.Date;

import lombok.extern.slf4j.Slf4j;

/**
 * Responsible for delayed payout based on account age. It does not consider if the account was yet used for
 * trading and is therefor not considered a strong protection.
 */
@Slf4j
public class AccountCreationAgeService {
    public final static long PHASE_ONE_PERIOD = 30;
    public final static long PERM_DELAY = 7;
    private final AccountAgeWitnessService accountAgeWitnessService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AccountCreationAgeService(AccountAgeWitnessService accountAgeWitnessService) {
        this.accountAgeWitnessService = accountAgeWitnessService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MinDepositAsCoin
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getMyAccountMinDepositAsCoin(PaymentAccount myPaymentAccount) {
        long myAccountAge = accountAgeWitnessService.getMyAccountAge(myPaymentAccount.getPaymentAccountPayload());
        long phaseOnePeriod = getPhaseOnePeriod(myPaymentAccount.getPaymentMethod());
        // Restrictions.getMinBuyerSecurityDepositAsCoin() is 0.001 BTC / 5 USD
        return Coin.valueOf(getMyAccountMinDepositAsCoin(myAccountAge, phaseOnePeriod, Restrictions.getMinBuyerSecurityDepositAsCoin().value));
    }

    public Coin getMinDepositAsCoin(Offer offer) {
        Coin minBuyerSecurityDepositAsCoin = Restrictions.getMinBuyerSecurityDepositAsCoin();
        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return minBuyerSecurityDepositAsCoin;
        }
        //TODO does that make sense here?
        if (offer.getDirection() == OfferPayload.Direction.SELL) {
            return minBuyerSecurityDepositAsCoin;
        }

        long buyersAccountAge = accountAgeWitnessService.getMakersAccountAge(offer);

        long phaseOnePeriod = getPhaseOnePeriod(offer.getPaymentMethod());
        // Restrictions.getMinBuyerSecurityDepositAsCoin() is 0.001 BTC / 5 USD
        return Coin.valueOf(getMyAccountMinDepositAsCoin(buyersAccountAge, phaseOnePeriod, minBuyerSecurityDepositAsCoin.value));
    }

    public Coin getMinDepositAsCoin(Trade trade) {
        Coin minBuyerSecurityDepositAsCoin = Restrictions.getMinBuyerSecurityDepositAsCoin();
        Offer offer = trade.getOffer();
        if (offer == null) {
            return minBuyerSecurityDepositAsCoin;
        }

        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return minBuyerSecurityDepositAsCoin;
        }

        Contract contract = trade.getContract();
        if (contract == null) {
            return minBuyerSecurityDepositAsCoin;
        }

        long buyersAccountAge = accountAgeWitnessService.getAccountAge(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        long phaseOnePeriod = getPhaseOnePeriod(offer.getPaymentMethod());
        // Restrictions.getMinBuyerSecurityDepositAsCoin() is 0.001 BTC / 5 USD
        return Coin.valueOf(getMyAccountMinDepositAsCoin(buyersAccountAge, phaseOnePeriod, minBuyerSecurityDepositAsCoin.value));
    }

    // Starts with 0.003 BTC for new accounts, goes linear to 0.001 BTC for 30 days and stays 0.001 BTC afterwards
    @VisibleForTesting
    public static long getMyAccountMinDepositAsCoin(long accountAge, long phaseOnePeriod, long minBuyerSecurityDepositAsCoin) {
        double accountAgeInDays = accountAge / (double) DateUtils.MILLIS_PER_DAY;
        double phaseOnePeriodInDays = phaseOnePeriod / (double) DateUtils.MILLIS_PER_DAY;
        double remaining = Math.max(0, phaseOnePeriodInDays - accountAgeInDays);
        long initialMinBuyerSecurityDeposit = 3 * minBuyerSecurityDepositAsCoin; // 0.003 BTC / 15 USD
        double diff = initialMinBuyerSecurityDeposit - minBuyerSecurityDepositAsCoin;
        return Math.round(remaining / phaseOnePeriodInDays * diff) + minBuyerSecurityDepositAsCoin;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MinDepositAsPercent
    ///////////////////////////////////////////////////////////////////////////////////////////

    public double getMyAccountMinDepositAsPercent(PaymentAccount myPaymentAccount) {
        long myAccountAge = accountAgeWitnessService.getMyAccountAge(myPaymentAccount.getPaymentAccountPayload());
        long phaseOnePeriod = getPhaseOnePeriod(myPaymentAccount.getPaymentMethod());
        // Restrictions.getMinBuyerSecurityDepositAsPercent() is  5% of trade amount.
        return getMyAccountMinDepositAsPercent(myAccountAge, phaseOnePeriod, Restrictions.getMinBuyerSecurityDepositAsPercent(myPaymentAccount));
    }

    public double getMinDepositAsPercent(Offer offer) {
        boolean cryptoCurrencyAccount = PaymentAccountUtil.isCryptoCurrencyAccount(offer.getPaymentMethod());
        double minBuyerSecurityDepositAsPercent = Restrictions.getMinBuyerSecurityDepositAsPercent(cryptoCurrencyAccount);
        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return minBuyerSecurityDepositAsPercent;
        }
        //TODO does that make sense here?
        if (offer.getDirection() == OfferPayload.Direction.SELL) {
            return minBuyerSecurityDepositAsPercent;
        }

        long buyersAccountAge = accountAgeWitnessService.getMakersAccountAge(offer);
        long phaseOnePeriod = getPhaseOnePeriod(offer.getPaymentMethod());
        return getMyAccountMinDepositAsPercent(buyersAccountAge, phaseOnePeriod, 0.1);
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

        long buyersAccountAge = accountAgeWitnessService.getAccountAge(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        long phaseOnePeriod = getPhaseOnePeriod(offer.getPaymentMethod());
        return getMyAccountMinDepositAsPercent(buyersAccountAge, phaseOnePeriod, 0.1);
    }

    // Starts with 30% for new accounts, goes linear to 10% for 30 days and stays 10% afterwards
    @VisibleForTesting
    public static double getMyAccountMinDepositAsPercent(long accountAge, long phaseOnePeriod, double minBuyerSecurityDepositAsPercent) {
        double accountAgeInDays = accountAge / (double) DateUtils.MILLIS_PER_DAY;
        double phaseOnePeriodInDays = phaseOnePeriod / (double) DateUtils.MILLIS_PER_DAY;
        double remaining = Math.max(0, phaseOnePeriodInDays - accountAgeInDays);
        double initialMinBuyerSecurityDeposit = 0.3; // 30%
        double diff = initialMinBuyerSecurityDeposit - minBuyerSecurityDepositAsPercent;
        return remaining / phaseOnePeriodInDays * diff + minBuyerSecurityDepositAsPercent;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delay
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the delay for the payout in days based on a linear function which starts with a delay of 30 days at age 0 and
     * ends with 7 days delay at account age 30 days and stays 7 days indefinitely.
     * @param buyersAccountAge      Account age of buyer in ms
     * @param phaseOnePeriod    Required account age in ms
     * @return The delay for the delayed payout in days.
     */
    @VisibleForTesting
    public static long getDelayInDays(long buyersAccountAge, long phaseOnePeriod) {
        double buyersAccountAgeInDays = buyersAccountAge / (double) DateUtils.MILLIS_PER_DAY;
        double phaseOnePeriodInDays = phaseOnePeriod / (double) DateUtils.MILLIS_PER_DAY;
        double remaining = Math.max(0, phaseOnePeriodInDays - buyersAccountAgeInDays);
        double permDelay = (double) PERM_DELAY;
        double diffDelay = phaseOnePeriodInDays - permDelay;
        return Math.round(((remaining / phaseOnePeriodInDays * diffDelay) + permDelay));
    }

    /**
     * @param trade     The trade for which we want to know the delayed payout for the buyer.
     * @return The delay in ms for the payout for the fiat buyer in a trade.
     */
    public long getDelay(Trade trade) {
        Offer offer = trade.getOffer();
        if (offer == null) {
            return 0;
        }

        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return 0;
        }

        Contract contract = trade.getContract();
        if (contract == null) {
            return 0;
        }

        long buyersAccountAge = accountAgeWitnessService.getAccountAge(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        long requiredAccountAge = getPhaseOnePeriod(offer.getPaymentMethod());

        return getDelayInDays(buyersAccountAge, requiredAccountAge) * DateUtils.MILLIS_PER_DAY;
    }

    /**
     * @param offer     The offer for which we want to know the delayed payout.
     * @return The delay in ms for the payout if offer maker is buyer.
     */
    public long getDelayForOffer(Offer offer) {
        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return 0;
        }
        if (offer.getDirection() == OfferPayload.Direction.SELL) {
            return 0;
        }

        long buyersAccountAge = accountAgeWitnessService.getMakersAccountAge(offer);
        long requiredAccountAge = getPhaseOnePeriod(offer.getPaymentMethod());
        return getDelayInDays(buyersAccountAge, requiredAccountAge) * DateUtils.MILLIS_PER_DAY;
    }

    /**
     * Delay for maker if he is fiat buyer.
     * @param myPaymentAccount      My payment account used for my offer
     * @param currencyCode          Currency code of my offer
     * @param direction             Direction of my offer
     * @return The delay in ms for the payout of maker.
     */
    public long getDelayForMyOffer(PaymentAccount myPaymentAccount, String currencyCode, OfferPayload.Direction direction) {
        if (direction == OfferPayload.Direction.SELL) {
            return 0;
        }

        if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
            return 0;
        }

        long myAccountAge = accountAgeWitnessService.getMyAccountAge(myPaymentAccount.getPaymentAccountPayload());
        long requiredAccountAge = getPhaseOnePeriod(myPaymentAccount.getPaymentMethod());
        return getDelayInDays(myAccountAge, requiredAccountAge) * DateUtils.MILLIS_PER_DAY;
    }

    /**
     * @param trade     The trade for which we want to know the delayed payout date.
     * @return The date of a delayed payout
     */
    public Date getDelayedTradePayoutDate(Trade trade) {
        long delay = getDelay(trade);
        long now = new Date().getTime();
        return new Date(delay + now);
    }

    /**
     * @param paymentMethod     The paymentMethod which determines the max. period
     * @return The period ofr phase one in ms (day units)
     */
    public long getPhaseOnePeriod(PaymentMethod paymentMethod) {
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
                return 0;

            default:
                // All other bank transfer methods
                return getPhaseOnePeriodAsMilli();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Is in phase one period
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * My account is taken into consideration for a delayed payout.
     * @param myPaymentAccount      My payment account used for my offer
     * @param currencyCode          Currency code of my offer
     * @param direction             Direction of my offer
     * @return If my account requires a delayed payout
     */
    public boolean myMakerAccountInPhaseOnePeriod(PaymentAccount myPaymentAccount,
                                                  String currencyCode,
                                                  OfferPayload.Direction direction) {
        return accountInPhaseOnePeriod(myPaymentAccount.getPaymentMethod(),
                accountAgeWitnessService.getMyAccountAge(myPaymentAccount.getPaymentAccountPayload()),
                currencyCode,
                direction);
    }

    /**
     * We take the maker as buyer into consideration for a delayed payout.
     * @param offer The offer for which we request the delayed payout state.
     * @return If that offer requires a delayed payout
     */
    public boolean offerInPhaseOnePeriod(Offer offer) {
        return accountInPhaseOnePeriod(offer.getPaymentMethod(),
                accountAgeWitnessService.getMakersAccountAge(offer),
                offer.getCurrencyCode(),
                offer.getDirection());
    }

    /**
     * We take the buyer in a trade (maker or taker) into consideration for a delayed payout.
     * @param trade The trade for which we request the delayed payout state.
     * @return If that trade requires a delayed payout
     */
    public boolean tradeInPhaseOnePeriod(Trade trade) {
        Offer offer = trade.getOffer();
        if (offer == null) {
            return false;
        }

        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return false;
        }

        Contract contract = trade.getContract();
        if (contract == null) {
            return false;
        }

        long buyersAccountAge = accountAgeWitnessService.getAccountAge(contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing());
        long requiredAccountAge = getPhaseOnePeriod(offer.getPaymentMethod());
        return buyersAccountAge < requiredAccountAge;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Is delay required
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * We take the buyer in a trade (maker or taker) into consideration for a delayed payout.
     * @param trade The trade for which we request the delayed payout state.
     * @return If that trade requires a delayed payout
     */
    public boolean tradeRequirePayoutDelay(Trade trade) {
        Offer offer = trade.getOffer();
        if (offer == null) {
            return false;
        }

        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return false;
        }

        return true;
    }

    /**
     * We take the maker as buyer into consideration for a delayed payout.
     * @param offer The offer for which we request the delayed payout state.
     * @return If that offer requires a delayed payout
     */
    public boolean offerRequirePayoutDelay(Offer offer) {
        return accountRequiresPayoutDelay(offer.getCurrencyCode(),
                offer.getDirection());
    }

    /**
     * My account is taken into consideration for a delayed payout.
     * @param currencyCode          Currency code of my offer
     * @param direction             Direction of my offer
     * @return If my account requires a delayed payout
     */
    public boolean myMakerAccountRequiresPayoutDelay(String currencyCode,
                                                     OfferPayload.Direction direction) {
        return accountRequiresPayoutDelay(currencyCode,
                direction);
    }

    public long getPhaseOnePeriodAsMilli() {
        return PHASE_ONE_PERIOD * DateUtils.MILLIS_PER_DAY;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean accountInPhaseOnePeriod(PaymentMethod paymentMethod,
                                            long accountAge,
                                            String currencyCode,
                                            OfferPayload.Direction direction) {
        if (direction == OfferPayload.Direction.SELL) {
            return false;
        }

        if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
            return false;
        }

        long requiredAccountAge = getPhaseOnePeriod(paymentMethod);
        return accountAge < requiredAccountAge;
    }

    private boolean accountRequiresPayoutDelay(String currencyCode,
                                               OfferPayload.Direction direction) {
        if (direction == OfferPayload.Direction.SELL) {
            return false;
        }

        if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
            return false;
        }

        return true;
    }
}
