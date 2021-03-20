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

package bisq.core.payment.payload;

import bisq.core.locale.Res;

import com.google.protobuf.Message;

import org.apache.commons.lang3.ArrayUtils;

import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@ToString
@Setter
@Getter
@Slf4j
public final class CashByMailAccountPayload extends PaymentAccountPayload implements PayloadWithHolderName {
    private String postalAddress = "";
    private String contact = "";
    private String extraInfo = "";

    public CashByMailAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private CashByMailAccountPayload(String paymentMethod, String id,
                                             String postalAddress,
                                             String contact,
                                             String extraInfo,
                                             long maxTradePeriod,
                                             Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);
        this.postalAddress = postalAddress;
        this.contact = contact;
        this.extraInfo = extraInfo;
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder()
                .setCashByMailAccountPayload(protobuf.CashByMailAccountPayload.newBuilder()
                        .setPostalAddress(postalAddress)
                        .setContact(contact)
                        .setExtraInfo(extraInfo))
                .build();
    }

    public static CashByMailAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        return new CashByMailAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                proto.getCashByMailAccountPayload().getPostalAddress(),
                proto.getCashByMailAccountPayload().getContact(),
                proto.getCashByMailAccountPayload().getExtraInfo(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + Res.getWithCol("payment.account.owner") + " " + contact + ", " +
                Res.getWithCol("payment.postal.address") + " " + postalAddress + ", " +
                Res.getWithCol("payment.shared.extraInfo") + " " + extraInfo;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return Res.getWithCol("payment.account.owner") + " " + contact + "\n" +
                Res.getWithCol("payment.postal.address") + " " + postalAddress;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        // We use here the contact because the address alone seems to be too weak
        return super.getAgeWitnessInputData(ArrayUtils.addAll(contact.getBytes(StandardCharsets.UTF_8),
                postalAddress.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String getOwnerId() {
        return contact;
    }
    @Override
    public String getHolderName() {
        return contact;
    }
}
