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

import bisq.common.util.JsonExclude;
import bisq.common.util.Tuple2;

import com.google.protobuf.Message;

import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@EqualsAndHashCode(callSuper = true)
@ToString
@Slf4j
public final class RevolutAccountPayload extends PaymentAccountPayload {
    // Only used as internal Id to not break existing account witness objects
    // We still show it in case it is different to the userName for additional security
    @Getter
    private String accountId = "";

    // Was added in 1.3.8
    // To not break signed accounts we keep accountId as internal id used for signing.
    // Old accounts get a popup to add the new required field userName but accountId is
    // left unchanged. Newly created accounts fill accountId with the value of userName.
    // In the UI we only use userName.

    // For backward compatibility we need to exclude the new field for the contract json.
    // We can remove that after a while when risk that users with pre 1.3.8 version trade with updated
    // users is very low.
    @JsonExclude
    @Getter
    private String userName = "";

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
        protobuf.RevolutAccountPayload.Builder revolutBuilder = protobuf.RevolutAccountPayload.newBuilder()
                .setAccountId(accountId)
                .setUserName(userName);
        return getPaymentAccountPayloadBuilder().setRevolutAccountPayload(revolutBuilder).build();
    }


    public static RevolutAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        protobuf.RevolutAccountPayload revolutAccountPayload = proto.getRevolutAccountPayload();
        return new RevolutAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                revolutAccountPayload.getAccountId(),
                revolutAccountPayload.getUserName(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        Tuple2<String, String> tuple = getLabelValueTuple();
        return Res.get(paymentMethodId) + " - " + tuple.first + ": " + tuple.second;
    }

    private Tuple2<String, String> getLabelValueTuple() {
        String label;
        String value;
        checkArgument(!userName.isEmpty() || hasOldAccountId(),
                "Either username must be set or we have an old account with accountId");
        if (!userName.isEmpty()) {
            label = Res.get("payment.account.userName");
            value = userName;

            if (hasOldAccountId()) {
                label += "/" + Res.get("payment.account.phoneNr");
                value += "/" + accountId;
            }
        } else {
            label = Res.get("payment.account.phoneNr");
            value = accountId;
        }
        return new Tuple2<>(label, value);
    }

    public Tuple2<String, String> getRecipientsAccountData() {
        Tuple2<String, String> tuple = getLabelValueTuple();
        String label = Res.get("portfolio.pending.step2_buyer.recipientsAccountData", tuple.first);
        return new Tuple2<>(label, tuple.second);
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return getPaymentDetails();
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        // getAgeWitnessInputData is called at new account creation when accountId is empty string.
        if (hasOldAccountId()) {
            // If the accountId was already in place (updated user who had used accountId for account age) we keep the
            // old accountId to not invalidate the existing account age witness.
            return super.getAgeWitnessInputData(accountId.getBytes(StandardCharsets.UTF_8));

        } else {
            // If a new account was registered from version 1.3.8 or later we use the userName.
            return super.getAgeWitnessInputData(userName.getBytes(StandardCharsets.UTF_8));
        }
    }

    public boolean userNameNotSet() {
        return userName.isEmpty();
    }

    public boolean hasOldAccountId() {
        return !accountId.equals(userName);
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    // In case it is a new account we need to fill the accountId field to support not-updated traders who are not
    // aware of the new userName field
    public void maybeApplyUserNameToAccountId() {
        if (accountId.isEmpty()) {
            accountId = userName;
        }
    }
}
