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

package bisq.core.payment;

import bisq.core.proto.CoreProtoResolver;

import bisq.common.proto.persistable.PersistableList;

import com.google.protobuf.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class PaymentAccountList extends PersistableList<PaymentAccount> {

    public PaymentAccountList(List<PaymentAccount> list) {
        super(list);
    }

    @Override
    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setPaymentAccountList(protobuf.PaymentAccountList.newBuilder()
                        .addAllPaymentAccount(getList().stream().map(PaymentAccount::toProtoMessage).collect(Collectors.toList())))
                .build();
    }

    public static PaymentAccountList fromProto(protobuf.PaymentAccountList proto, CoreProtoResolver coreProtoResolver) {
        return new PaymentAccountList(new ArrayList<>(proto.getPaymentAccountList().stream()
                .map(e -> PaymentAccount.fromProto(e, coreProtoResolver))
                .filter(Objects::nonNull)
                .collect(Collectors.toList())));
    }
}
