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

import bisq.common.proto.ProtoUtil;

import com.google.protobuf.Message;

import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@EqualsAndHashCode(callSuper = true)
@ToString
@Slf4j
public final class RevolutAccountPayload extends PaymentAccountPayload {
    // Not used anymore from outside. Only used as internal Id to not break existing account witness objects
    private String accountId = null;

    // Was added in 1.3.8
    // To not break signed accounts we keep accountId as internal id used for signing.
    // Old accounts get a popup to add the new required field userName but accountId is
    // left unchanged. Newly created accounts fill accountId with the value of userName.
    // In the UI we only use userName.
    @Nullable
    private String userName = null;

    public RevolutAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RevolutAccountPayload(String paymentMethod,
                                  String id,
                                  String accountId,
                                  @Nullable String userName,
                                  long maxTradePeriod,
                                  Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);

        this.accountId = accountId;
        this.userName = userName;
    }

    @Override
    public Message toProtoMessage() {
        protobuf.RevolutAccountPayload.Builder revolutBuilder = protobuf.RevolutAccountPayload.newBuilder().setAccountId(accountId);
        Optional.ofNullable(userName).ifPresent(revolutBuilder::setUserName);
        return getPaymentAccountPayloadBuilder().setRevolutAccountPayload(revolutBuilder).build();
    }


    public static RevolutAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        protobuf.RevolutAccountPayload revolutAccountPayload = proto.getRevolutAccountPayload();
        return new RevolutAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                revolutAccountPayload.getAccountId(),
                ProtoUtil.stringOrNullFromProto(revolutAccountPayload.getUserName()),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + Res.getWithCol("payment.account.userName") + " " + userName;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return getPaymentDetails();
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        // getAgeWitnessInputData is called at new account creation when accountId is null, we use empty string as
        // it has been the case before
        String input = this.accountId == null ? "" : this.accountId;
        return super.getAgeWitnessInputData(input.getBytes(StandardCharsets.UTF_8));
    }

    public void setUserName(@Nullable String userName) {
        this.userName = userName;
        // We only set accountId to userName for new accounts. Existing accounts have accountId set with email
        // or phone nr. and we keep that to not break account signing.
        if (accountId == null) {
            accountId = userName;
        }
    }

    public String getUserName() {
        return userName != null ? userName : accountId;
    }

    public boolean userNameNotSet() {
        return userName == null;
    }
}
