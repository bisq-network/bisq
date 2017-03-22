/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.protobuffer.payload.payment;

import com.google.common.base.Joiner;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Locale;

@EqualsAndHashCode(callSuper = true)
@ToString
@Getter
@Slf4j
public final class SpecificBanksAccountPayload extends BankAccountPayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    // Dont use a set here as we need a deterministic ordering, otherwise the contract hash does not match
    private ArrayList<String> acceptedBanks;

    public SpecificBanksAccountPayload(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
        acceptedBanks = new ArrayList<>();
    }

    public void clearAcceptedBanks() {
        acceptedBanks = new ArrayList<>();
    }

    public void addAcceptedBank(String bankName) {
        if (!acceptedBanks.contains(bankName))
            acceptedBanks.add(bankName);
    }

    @Override
    public String getPaymentDetails() {
        return "Transfers with specific banks - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return null;
    }

    @Override
    public String getPaymentDetailsForTradePopup(Locale locale) {
        return super.getPaymentDetailsForTradePopup(locale) + "\n" +
                "Accepted banks: " + Joiner.on(", ").join(acceptedBanks);
    }

    @Override
    public PB.PaymentAccountPayload toProto() {
        PB.SpecificBanksAccountPayload.Builder specificBanksAccountPayload =
                PB.SpecificBanksAccountPayload.newBuilder().addAllAcceptedBanks(acceptedBanks);
        PB.BankAccountPayload.Builder bankAccountPayload =
                PB.BankAccountPayload.newBuilder()
                        .setHolderName(holderName)
                        .setBankName(bankName)
                        .setBankId(bankId)
                        .setBranchId(branchId)
                        .setAccountNr(accountNr)
                        .setAccountType(accountType)
                        .setHolderTaxId(holderTaxId)
                        .setSpecificBanksAccountPayload(specificBanksAccountPayload);
        PB.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayload =
                PB.CountryBasedPaymentAccountPayload.newBuilder()
                        .setCountryCode(countryCode)
                        .setBankAccountPayload(bankAccountPayload);
        PB.PaymentAccountPayload.Builder paymentAccountPayload =
                PB.PaymentAccountPayload.newBuilder()
                        .setId(id)
                        .setPaymentMethodId(paymentMethodId)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayload);

        return paymentAccountPayload.build();
    }
}
