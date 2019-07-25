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

package bisq.core.notifications.alerts.market;

import bisq.core.payment.PaymentAccount;
import bisq.core.proto.CoreProtoResolver;

import bisq.common.proto.persistable.PersistablePayload;

import java.util.ArrayList;
import java.util.List;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
public class MarketAlertFilter implements PersistablePayload {
    private PaymentAccount paymentAccount;
    private int triggerValue;
    private boolean isBuyOffer;
    private List<String> alertIds;


    public MarketAlertFilter(PaymentAccount paymentAccount, int triggerValue, boolean isBuyOffer) {
        this(paymentAccount, triggerValue, isBuyOffer, new ArrayList<>());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     *
     * @param paymentAccount    // The payment account used for the filter
     * @param triggerValue      // Percentage distance from market price (100 for 1.00%)
     * @param isBuyOffer        // It the offer is a buy offer
     * @param alertIds          // List of offerIds for which we have sent already an alert
     */
    private MarketAlertFilter(PaymentAccount paymentAccount, int triggerValue, boolean isBuyOffer, List<String> alertIds) {
        this.paymentAccount = paymentAccount;
        this.triggerValue = triggerValue;
        this.isBuyOffer = isBuyOffer;
        this.alertIds = alertIds;
    }

    @Override
    public protobuf.MarketAlertFilter toProtoMessage() {
        return protobuf.MarketAlertFilter.newBuilder()
                .setPaymentAccount(paymentAccount.toProtoMessage())
                .setTriggerValue(triggerValue)
                .setIsBuyOffer(isBuyOffer)
                .addAllAlertIds(alertIds)
                .build();
    }

    public static MarketAlertFilter fromProto(protobuf.MarketAlertFilter proto, CoreProtoResolver coreProtoResolver) {
        List<String> list = proto.getAlertIdsList().isEmpty() ?
                new ArrayList<>() : new ArrayList<>(proto.getAlertIdsList());
        return new MarketAlertFilter(PaymentAccount.fromProto(proto.getPaymentAccount(), coreProtoResolver),
                proto.getTriggerValue(),
                proto.getIsBuyOffer(),
                list);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addAlertId(String alertId) {
        if (notContainsAlertId(alertId))
            alertIds.add(alertId);
    }

    public boolean notContainsAlertId(String alertId) {
        return !alertIds.contains(alertId);
    }

    @Override
    public String toString() {
        return "MarketAlertFilter{" +
                "\n     paymentAccount=" + paymentAccount +
                ",\n     triggerValue=" + triggerValue +
                ",\n     isBuyOffer=" + isBuyOffer +
                ",\n     alertIds=" + alertIds +
                "\n}";
    }
}
