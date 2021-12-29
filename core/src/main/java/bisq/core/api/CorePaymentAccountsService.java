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

package bisq.core.api;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.api.model.PaymentAccountForm;
import bisq.core.locale.CryptoCurrency;
import bisq.core.payment.AssetAccount;
import bisq.core.payment.CryptoCurrencyAccount;
import bisq.core.payment.InstantCryptoCurrencyAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountFactory;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.User;

import bisq.asset.Asset;
import bisq.asset.AssetRegistry;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.File;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.app.DevEnv.isDaoTradingActivated;
import static bisq.common.config.Config.baseCurrencyNetwork;
import static bisq.core.locale.CurrencyUtil.findAsset;
import static bisq.core.locale.CurrencyUtil.getCryptoCurrency;
import static java.lang.String.format;

@Singleton
@Slf4j
class CorePaymentAccountsService {

    private final Predicate<String> apiDoesSupportCryptoCurrencyAccount = (c) ->
            c.equals("BSQ") || c.equals("XMR");

    private final CoreWalletsService coreWalletsService;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final PaymentAccountForm paymentAccountForm;
    private final User user;

    @Inject
    public CorePaymentAccountsService(CoreWalletsService coreWalletsService,
                                      AccountAgeWitnessService accountAgeWitnessService,
                                      PaymentAccountForm paymentAccountForm,
                                      User user) {
        this.coreWalletsService = coreWalletsService;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.paymentAccountForm = paymentAccountForm;
        this.user = user;
    }

    // Fiat Currency Accounts

    PaymentAccount createPaymentAccount(String jsonString) {
        PaymentAccount paymentAccount = paymentAccountForm.toPaymentAccount(jsonString);
        verifyPaymentAccountHasRequiredFields(paymentAccount);
        user.addPaymentAccountIfNotExists(paymentAccount);
        accountAgeWitnessService.publishMyAccountAgeWitness(paymentAccount.getPaymentAccountPayload());
        log.info("Saved payment account with id {} and payment method {}.",
                paymentAccount.getId(),
                paymentAccount.getPaymentAccountPayload().getPaymentMethodId());
        return paymentAccount;
    }

    Set<PaymentAccount> getPaymentAccounts() {
        return user.getPaymentAccounts();
    }

    List<PaymentMethod> getFiatPaymentMethods() {
        return PaymentMethod.getPaymentMethods().stream()
                .filter(PaymentMethod::isFiat)
                .sorted(Comparator.comparing(PaymentMethod::getId))
                .collect(Collectors.toList());
    }

    String getPaymentAccountFormAsString(String paymentMethodId) {
        File jsonForm = getPaymentAccountForm(paymentMethodId);
        jsonForm.deleteOnExit(); // If just asking for a string, delete the form file.
        return paymentAccountForm.toJsonString(jsonForm);
    }

    File getPaymentAccountForm(String paymentMethodId) {
        return paymentAccountForm.getPaymentAccountForm(paymentMethodId);
    }

    // Crypto Currency Accounts

    PaymentAccount createCryptoCurrencyPaymentAccount(String accountName,
                                                      String currencyCode,
                                                      String address,
                                                      boolean tradeInstant) {
        String cryptoCurrencyCode = currencyCode.toUpperCase();
        verifyApiDoesSupportCryptoCurrencyAccount(cryptoCurrencyCode);
        verifyCryptoCurrencyAddress(cryptoCurrencyCode, address);

        AssetAccount cryptoCurrencyAccount = tradeInstant
                ? (InstantCryptoCurrencyAccount) PaymentAccountFactory.getPaymentAccount(PaymentMethod.BLOCK_CHAINS_INSTANT)
                : (CryptoCurrencyAccount) PaymentAccountFactory.getPaymentAccount(PaymentMethod.BLOCK_CHAINS);
        cryptoCurrencyAccount.init();
        cryptoCurrencyAccount.setAccountName(accountName);
        cryptoCurrencyAccount.setAddress(address);
        Optional<CryptoCurrency> cryptoCurrency = getCryptoCurrency(cryptoCurrencyCode);
        cryptoCurrency.ifPresent(cryptoCurrencyAccount::setSingleTradeCurrency);
        user.addPaymentAccount(cryptoCurrencyAccount);
        log.info("Saved crypto payment account with id {} and payment method {}.",
                cryptoCurrencyAccount.getId(),
                cryptoCurrencyAccount.getPaymentAccountPayload().getPaymentMethodId());
        return cryptoCurrencyAccount;
    }

    // TODO Support all alt coin payment methods supported by UI.
    //  The getCryptoCurrencyPaymentMethods method below will be
    //  callable from the CLI when more are supported.

    List<PaymentMethod> getCryptoCurrencyPaymentMethods() {
        return PaymentMethod.getPaymentMethods().stream()
                .filter(PaymentMethod::isAltcoin)
                .sorted(Comparator.comparing(PaymentMethod::getId))
                .collect(Collectors.toList());
    }

    private void verifyCryptoCurrencyAddress(String cryptoCurrencyCode, String address) {
        if (cryptoCurrencyCode.equals("BSQ")) {
            // Validate the BSQ address, but ignore the return value.
            coreWalletsService.getValidBsqAddress(address);
        } else {
            Asset asset = getAsset(cryptoCurrencyCode);
            if (!asset.validateAddress(address).isValid())
                throw new IllegalArgumentException(
                        format("%s is not a valid %s address",
                                address,
                                cryptoCurrencyCode.toLowerCase()));
        }
    }

    private void verifyApiDoesSupportCryptoCurrencyAccount(String cryptoCurrencyCode) {
        if (!apiDoesSupportCryptoCurrencyAccount.test(cryptoCurrencyCode))
            throw new IllegalArgumentException(
                    format("api does not currently support %s accounts",
                            cryptoCurrencyCode.toLowerCase()));

    }

    private Asset getAsset(String cryptoCurrencyCode) {
        return findAsset(new AssetRegistry(),
                cryptoCurrencyCode,
                baseCurrencyNetwork(),
                isDaoTradingActivated())
                .orElseThrow(() -> new IllegalStateException(
                        format("crypto currency with code '%s' not found",
                                cryptoCurrencyCode.toLowerCase())));
    }

    private void verifyPaymentAccountHasRequiredFields(PaymentAccount paymentAccount) {
        if (!paymentAccount.hasMultipleCurrencies() && paymentAccount.getSingleTradeCurrency() == null)
            throw new IllegalArgumentException(format("no trade currency defined for %s payment account",
                    paymentAccount.getPaymentMethod().getDisplayString().toLowerCase()));
    }
}
