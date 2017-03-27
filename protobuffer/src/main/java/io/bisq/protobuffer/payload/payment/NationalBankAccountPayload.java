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

import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@ToString
@Slf4j
public final class NationalBankAccountPayload extends BankAccountPayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public NationalBankAccountPayload(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    @Override
    public String getPaymentDetails() {
        return "National Bank transfer - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }

    @Override
    public PB.PaymentAccountPayload toProto() {
        PB.NationalBankAccountPayload.Builder thisClass =
                PB.NationalBankAccountPayload.newBuilder();
        PB.BankAccountPayload.Builder bankAccountPayload =
                PB.BankAccountPayload.newBuilder()
                        .setHolderName(holderName)
                        .setBankName(bankName)
                        .setBankId(bankId)
                        .setBranchId(branchId)
                        .setAccountNr(accountNr)
                        .setAccountType(accountType)
                        .setHolderTaxId(holderTaxId)
                        .setNationalBankAccountPayload(thisClass);
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
