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

package bisq.core.payment;

import bisq.core.locale.Country;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentMethod;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class PaymentAccountUtil {

    public static boolean isRiskyBuyOfferWithImmatureAccountAge(Offer offer, AccountAgeWitnessService accountAgeWitnessService) {
        long accountCreationDate = new Date().getTime() - accountAgeWitnessService.getMakersAccountAge(offer, new Date());
        return offer.isBuyOffer() &&
                PaymentMethod.hasChargebackRisk(offer.getPaymentMethod()) &&
                accountCreationDate > AccountAgeWitnessService.SAFE_ACCOUNT_AGE_DATE;
    }

    public static boolean isSellOfferAndAnyTakerPaymentAccountForOfferMature(Offer offer,
                                                                             Collection<PaymentAccount> takerPaymentAccounts,
                                                                             AccountAgeWitnessService accountAgeWitnessService) {
        if (offer.isBuyOffer() || !PaymentMethod.hasChargebackRisk(offer.getPaymentMethod()))
            return true;

        for (PaymentAccount takerPaymentAccount : takerPaymentAccounts) {
            if (isTakerPaymentAccountForOfferMature(offer, takerPaymentAccount, accountAgeWitnessService))
                return true;
        }
        return false;
    }

    private static boolean isTakerPaymentAccountForOfferMature(Offer offer,
                                                               PaymentAccount takerPaymentAccount,
                                                               AccountAgeWitnessService accountAgeWitnessService) {
        long accountCreationDate = new Date().getTime() - accountAgeWitnessService.getMyAccountAge(takerPaymentAccount.getPaymentAccountPayload());
        return isTakerPaymentAccountValidForOffer(offer, takerPaymentAccount) &&
                accountCreationDate <= AccountAgeWitnessService.SAFE_ACCOUNT_AGE_DATE;
    }

    public static boolean hasMakerAnyMatureAccountForBuyOffer(Collection<PaymentAccount> makerPaymentAccounts,
                                                              AccountAgeWitnessService accountAgeWitnessService) {
        for (PaymentAccount makerPaymentAccount : makerPaymentAccounts) {
            if (hasMakerMatureAccountForBuyOffer(makerPaymentAccount, accountAgeWitnessService))
                return true;
        }
        return false;
    }

    private static boolean hasMakerMatureAccountForBuyOffer(PaymentAccount makerPaymentAccount,
                                                            AccountAgeWitnessService accountAgeWitnessService) {
        if (!PaymentMethod.hasChargebackRisk(makerPaymentAccount.getPaymentMethod()))
            return true;

        long accountCreationDate = new Date().getTime() - accountAgeWitnessService.getMyAccountAge(makerPaymentAccount.getPaymentAccountPayload());
        return accountCreationDate <= AccountAgeWitnessService.SAFE_ACCOUNT_AGE_DATE;
    }

    public static boolean isAnyTakerPaymentAccountValidForOffer(Offer offer, Collection<PaymentAccount> takerPaymentAccounts) {
        for (PaymentAccount takerPaymentAccount : takerPaymentAccounts) {
            if (isTakerPaymentAccountValidForOffer(offer, takerPaymentAccount))
                return true;
        }
        return false;
    }

    public static ObservableList<PaymentAccount> getPossiblePaymentAccounts(Offer offer,
                                                                            Set<PaymentAccount> paymentAccounts,
                                                                            AccountAgeWitnessService accountAgeWitnessService) {
        ObservableList<PaymentAccount> result = FXCollections.observableArrayList();
        result.addAll(paymentAccounts.stream()
                .filter(paymentAccount -> isTakerPaymentAccountValidForOffer(offer, paymentAccount))
                .filter(paymentAccount -> isTakerPaymentAccountForOfferMature(offer, paymentAccount, accountAgeWitnessService))
                .collect(Collectors.toList()));
        return result;
    }

    // TODO might be used to show more details if we get payment methods updates with diff. limits
    public static String getInfoForMismatchingPaymentMethodLimits(Offer offer, PaymentAccount paymentAccount) {
        // dont translate atm as it is not used so far in the UI just for logs
        return "Payment methods have different trade limits or trade periods.\n" +
                "Our local Payment method: " + paymentAccount.getPaymentMethod().toString() + "\n" +
                "Payment method from offer: " + offer.getPaymentMethod().toString();
    }

    public static boolean isTakerPaymentAccountValidForOffer(Offer offer, PaymentAccount paymentAccount) {
        return new ReceiptValidator(offer, paymentAccount).isValid();
    }

    public static Optional<PaymentAccount> getMostMaturePaymentAccountForOffer(Offer offer,
                                                                               Set<PaymentAccount> paymentAccounts,
                                                                               AccountAgeWitnessService service) {
        PaymentAccounts accounts = new PaymentAccounts(paymentAccounts, service);
        return Optional.ofNullable(accounts.getOldestPaymentAccountForOffer(offer));
    }

    @Nullable
    public static ArrayList<String> getAcceptedCountryCodes(PaymentAccount paymentAccount) {
        ArrayList<String> acceptedCountryCodes = null;
        if (paymentAccount instanceof SepaAccount) {
            acceptedCountryCodes = new ArrayList<>(((SepaAccount) paymentAccount).getAcceptedCountryCodes());
        } else if (paymentAccount instanceof SepaInstantAccount) {
            acceptedCountryCodes = new ArrayList<>(((SepaInstantAccount) paymentAccount).getAcceptedCountryCodes());
        } else if (paymentAccount instanceof CountryBasedPaymentAccount) {
            acceptedCountryCodes = new ArrayList<>();
            Country country = ((CountryBasedPaymentAccount) paymentAccount).getCountry();
            if (country != null)
                acceptedCountryCodes.add(country.code);
        }
        return acceptedCountryCodes;
    }

    @Nullable
    public static List<String> getAcceptedBanks(PaymentAccount paymentAccount) {
        List<String> acceptedBanks = null;
        if (paymentAccount instanceof SpecificBanksAccount) {
            acceptedBanks = new ArrayList<>(((SpecificBanksAccount) paymentAccount).getAcceptedBanks());
        } else if (paymentAccount instanceof SameBankAccount) {
            acceptedBanks = new ArrayList<>();
            acceptedBanks.add(((SameBankAccount) paymentAccount).getBankId());
        }
        return acceptedBanks;
    }

    @Nullable
    public static String getBankId(PaymentAccount paymentAccount) {
        return paymentAccount instanceof BankAccount ? ((BankAccount) paymentAccount).getBankId() : null;
    }

    @Nullable
    public static String getCountryCode(PaymentAccount paymentAccount) {
        // That is optional and set to null if not supported (AltCoins,...)
        if (paymentAccount instanceof CountryBasedPaymentAccount) {
            Country country = (((CountryBasedPaymentAccount) paymentAccount)).getCountry();
            return country != null ? country.code : null;
        }
        return null;
    }

    public static boolean isCryptoCurrencyAccount(PaymentAccount paymentAccount) {
        return (paymentAccount != null && paymentAccount.getPaymentMethod().equals(PaymentMethod.BLOCK_CHAINS) ||
                paymentAccount != null && paymentAccount.getPaymentMethod().equals(PaymentMethod.BLOCK_CHAINS_INSTANT));
    }

    // TODO no code duplication found in UI code (added for API)
    // That is optional and set to null if not supported (AltCoins,...)
   /* public static String getCountryCode(PaymentAccount paymentAccount) {
        if (paymentAccount instanceof CountryBasedPaymentAccount) {
            Country country = ((CountryBasedPaymentAccount) paymentAccount).getCountry();
            return country != null ? country.code : null;
        } else {
            return null;
        }
    }*/

    // TODO no code duplication found in UI code (added for API)
    /*public static long getMaxTradeLimit(AccountAgeWitnessService accountAgeWitnessService, PaymentAccount paymentAccount, String currencyCode) {
        if (paymentAccount != null)
            return accountAgeWitnessService.getMyTradeLimit(paymentAccount, currencyCode);
        else
            return 0;
    }*/
}
