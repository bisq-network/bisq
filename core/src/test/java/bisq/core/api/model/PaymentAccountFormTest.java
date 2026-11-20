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
package bisq.core.api.model;

import bisq.core.payment.stablecoin.StableCoinPaymentRail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PaymentAccountFormTest {
    private final PaymentAccountForm paymentAccountForm = new PaymentAccountForm();

    // The stablecoin payment methods share one account class, which the form cannot create
    @Test
    public void formOfAStableCoinPaymentMethodIsRefused() {
        for (StableCoinPaymentRail rail : StableCoinPaymentRail.values()) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> paymentAccountForm.getPaymentAccountForm(rail.name()));
            assertEquals("the api does not support " + rail.name() + " payment accounts", exception.getMessage());
        }
    }

    @Test
    public void accountOfAStableCoinPaymentMethodIsRefused() {
        String json = "{\"paymentMethodId\": \"USDT_TRC20\", \"accountName\": \"name\", \"address\": \"address\"}";
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> paymentAccountForm.toPaymentAccount(json));
        assertEquals("the api does not support USDT_TRC20 payment accounts", exception.getMessage());
    }
}
