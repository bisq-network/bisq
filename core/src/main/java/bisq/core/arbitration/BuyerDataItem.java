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

package bisq.core.arbitration;

import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.payment.payload.PaymentAccountPayload;

import org.bitcoinj.core.Coin;

import java.security.PublicKey;

import java.util.Objects;

import lombok.Getter;

@Getter
public class BuyerDataItem {
    private final PaymentAccountPayload paymentAccountPayload;
    private final AccountAgeWitness accountAgeWitness;
    private final Coin tradeAmount;
    private final PublicKey sellerPubKey;

    public BuyerDataItem(PaymentAccountPayload paymentAccountPayload,
                         AccountAgeWitness accountAgeWitness,
                         Coin tradeAmount,
                         PublicKey sellerPubKey) {
        this.paymentAccountPayload = paymentAccountPayload;
        this.accountAgeWitness = accountAgeWitness;
        this.tradeAmount = tradeAmount;
        this.sellerPubKey = sellerPubKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BuyerDataItem)) return false;
        // Only distinguish data by AccountAgeWitness. Other details are irrelevant
        return accountAgeWitness.equals(((BuyerDataItem) o).accountAgeWitness);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountAgeWitness);
    }
}
