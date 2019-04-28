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
import bisq.core.locale.CurrencyUtil;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.Contract;
import bisq.core.trade.Trade;

import javax.inject.Inject;

import org.apache.commons.lang3.time.DateUtils;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Main class for account score domain.
 * Provides access to any data related to account score. Internally it used different protection tools to constructing
 * the resulting parameters.
 */
public class AccountScoreService {
    private final AccountCreationAgeService accountCreationAgeService;
    private final SignedWitnessService signedWitnessService;
    private final AccountAgeWitnessService accountAgeWitnessService;


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

    public long getRequiredAccountAge(PaymentMethod paymentMethod) {
        return accountCreationAgeService.getRequiredAccountAge(paymentMethod);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Is delay required
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean myMakerAccountRequiresPayoutDelay(PaymentAccount myPaymentAccount, String currencyCode, OfferPayload.Direction direction) {
        return accountCreationAgeService.myMakerAccountRequiresPayoutDelay(myPaymentAccount, currencyCode, direction);
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
    // AccountScoreCategory
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<AccountScoreCategory> getMyAccountScoreCategory(PaymentAccount myPaymentAccount, String currencyCode) {
        if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
            return Optional.empty();
        }

        List<Long> myWitnessAgeList = signedWitnessService.getMyWitnessAgeList(myPaymentAccount.getPaymentAccountPayload());
        if (myWitnessAgeList.isEmpty()) {
            // Nobody has singed my witness object yet, so I am considered as an account which has never traded.
            long myAccountAge = accountAgeWitnessService.getMyAccountAge(myPaymentAccount.getPaymentAccountPayload());
            return Optional.of(getAccountScoreCategory(myAccountAge, false));
        } else {
            long oldestAge = myWitnessAgeList.get(0);
            return Optional.of(getAccountScoreCategory(oldestAge, true));
        }
    }

    public Optional<AccountScoreCategory> getAccountScoreCategoryOfMaker(Offer offer) {
        if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
            return Optional.empty();
        }

        Optional<String> accountAgeWitnessHash = offer.getAccountAgeWitnessHashAsHex();
        Optional<AccountAgeWitness> witnessByHashAsHex = accountAgeWitnessHash.isPresent() ?
                accountAgeWitnessService.getWitnessByHashAsHex(accountAgeWitnessHash.get()) :
                Optional.empty();
        if (witnessByHashAsHex.isPresent()) {
            List<Long> myWitnessAgeList = signedWitnessService.getWitnessAgeList(witnessByHashAsHex.get());
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
            List<Long> witnessAgeList = signedWitnessService.getWitnessAgeList(witness.get());
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
            long maxRequiredAge = AccountCreationAgeService.MAX_DELAY * DateUtils.MILLIS_PER_DAY;
            if (accountAge >= maxRequiredAge) {
                return AccountScoreCategory.GOLD;
            } else if (accountAge >= maxRequiredAge / 2) {
                return AccountScoreCategory.SILVER;
            } else {
                return AccountScoreCategory.BRONZE;
            }
        } else {
            long maxRequiredAge = AccountCreationAgeService.MAX_REQUIRED_AGE * DateUtils.MILLIS_PER_DAY;
            if (accountAge >= maxRequiredAge) {
                return AccountScoreCategory.SILVER;
            } else {
                return AccountScoreCategory.BRONZE;
            }
        }
    }
}
