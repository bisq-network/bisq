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

import bisq.core.api.model.PaymentAccountForm;
import bisq.core.locale.Res;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CorePaymentAccountsServiceTest {
    @BeforeAll
    public static void setUp() {
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    // The API cannot create an account of a stablecoin payment method, so it does not offer these payment methods
    @Test
    public void fiatPaymentMethodsOfTheApiHaveNoStableCoinPaymentMethod() {
        CorePaymentAccountsService service = new CorePaymentAccountsService(null, null, new PaymentAccountForm(), null);

        List<PaymentMethod> paymentMethods = service.getFiatPaymentMethods();

        // Not the static fields of PaymentMethod: ReceiptPredicatesTest replaces some of them by mocks
        assertTrue(paymentMethods.contains(PaymentMethod.getPaymentMethod(PaymentMethod.SEPA_ID)));
        assertTrue(paymentMethods.contains(PaymentMethod.getPaymentMethod(PaymentMethod.CELPAY_ID)));
        assertFalse(paymentMethods.isEmpty());
        assertTrue(paymentMethods.stream().noneMatch(PaymentMethod::isStableCoin));
        assertTrue(PaymentMethod.getPaymentMethods().stream().anyMatch(PaymentMethod::isStableCoin));
    }
}
