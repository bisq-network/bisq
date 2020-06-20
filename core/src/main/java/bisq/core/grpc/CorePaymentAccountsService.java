package bisq.core.grpc;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.FiatCurrency;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountFactory;
import bisq.core.payment.PerfectMoneyAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.User;

import bisq.common.config.Config;

import javax.inject.Inject;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CorePaymentAccountsService {

    private final Config config;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final User user;

    @Inject
    public CorePaymentAccountsService(Config config,
                                      AccountAgeWitnessService accountAgeWitnessService,
                                      User user) {
        this.config = config;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.user = user;
    }

    public void createPaymentAccount(String accountName, String accountNumber, String fiatCurrencyCode) {
        // Create and persist a PerfectMoney dummy payment account.  There is no guard
        // against creating accounts with duplicate names & numbers, only the uuid and
        // creation date are unique.
        PaymentMethod dummyPaymentMethod = PaymentMethod.getDummyPaymentMethod(PaymentMethod.PERFECT_MONEY_ID);
        PaymentAccount paymentAccount = PaymentAccountFactory.getPaymentAccount(dummyPaymentMethod);
        paymentAccount.init();
        paymentAccount.setAccountName(accountName);
        ((PerfectMoneyAccount) paymentAccount).setAccountNr(accountNumber);
        paymentAccount.setSingleTradeCurrency(new FiatCurrency(fiatCurrencyCode));
        user.addPaymentAccount(paymentAccount);

        // Don't do this on mainnet until thoroughly tested.
        if (config.baseCurrencyNetwork.isRegtest())
            accountAgeWitnessService.publishMyAccountAgeWitness(paymentAccount.getPaymentAccountPayload());

        log.info("Payment account {} saved", paymentAccount.getId());
    }

    public Set<PaymentAccount> getPaymentAccounts() {
        return user.getPaymentAccounts();
    }
}
