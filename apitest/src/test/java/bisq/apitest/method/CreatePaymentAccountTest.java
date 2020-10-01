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

package bisq.apitest.method;

import bisq.proto.grpc.GetPaymentAccountsRequest;

import protobuf.PaymentAccount;
import protobuf.PerfectMoneyAccountPayload;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;


@Slf4j
@TestMethodOrder(OrderAnnotation.class)
public class CreatePaymentAccountTest extends MethodTest {

    static final String PERFECT_MONEY_ACCT_NAME = "Perfect Money USD";
    static final String PERFECT_MONEY_ACCT_NUMBER = "0123456789";

    @BeforeAll
    public static void setUp() {
        try {
            setUpScaffold(bitcoind, alicedaemon);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    @Order(1)
    public void testCreatePerfectMoneyUSDPaymentAccount() {
        var perfectMoneyPaymentAccountRequest = createCreatePerfectMoneyPaymentAccountRequest(
                PERFECT_MONEY_ACCT_NAME,
                PERFECT_MONEY_ACCT_NUMBER,
                "USD");
        //noinspection ResultOfMethodCallIgnored
        grpcStubs(alicedaemon).paymentAccountsService.createPaymentAccount(perfectMoneyPaymentAccountRequest);

        var getPaymentAccountsRequest = GetPaymentAccountsRequest.newBuilder().build();
        var reply = grpcStubs(alicedaemon).paymentAccountsService.getPaymentAccounts(getPaymentAccountsRequest);

        // The daemon is running against the regtest/dao setup files, and was set up with
        // two dummy accounts ("PerfectMoney dummy", "ETH dummy") before any tests ran.
        // We just added 1 test account, making 3 total.
        assertEquals(3, reply.getPaymentAccountsCount());

        // Sort the returned list by creation date; the last item in the sorted
        // list will be the payment acct we just created.
        List<PaymentAccount> paymentAccountList = reply.getPaymentAccountsList().stream()
                .sorted(comparing(PaymentAccount::getCreationDate))
                .collect(Collectors.toList());
        PaymentAccount paymentAccount = paymentAccountList.get(2);
        PerfectMoneyAccountPayload perfectMoneyAccount = paymentAccount
                .getPaymentAccountPayload()
                .getPerfectMoneyAccountPayload();

        assertEquals(PERFECT_MONEY_ACCT_NAME, paymentAccount.getAccountName());
        assertEquals("USD",
                paymentAccount.getSelectedTradeCurrency().getFiatCurrency().getCurrency().getCurrencyCode());
        assertEquals(PERFECT_MONEY_ACCT_NUMBER, perfectMoneyAccount.getAccountNr());
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
