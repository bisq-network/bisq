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

package bisq.network.p2p.mailbox;

import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.proto.persistable.PersistableList;

import com.google.protobuf.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EqualsAndHashCode(callSuper = true)
public class MailboxMessageList extends PersistableList<MailboxItem> {

    public MailboxMessageList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public MailboxMessageList(List<MailboxItem> list) {
        super(list);
    }

    @Override
    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setMailboxMessageList(protobuf.MailboxMessageList.newBuilder()
                        .addAllMailboxItem(getList().stream()
                                .map(MailboxItem::toProtoMessage)
                                .collect(Collectors.toList())))
                .build();
    }

    public static MailboxMessageList fromProto(protobuf.MailboxMessageList proto,
                                               NetworkProtoResolver networkProtoResolver) {
        return new MailboxMessageList(new ArrayList<>(proto.getMailboxItemList().stream()
                .map(e -> {
                    try {
                        return MailboxItem.fromProto(e, networkProtoResolver);
                    } catch (ProtobufferException protobufferException) {
                        protobufferException.printStackTrace();
                        log.error("Error at MailboxItem.fromProto: {}", protobufferException.toString());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList())));
    }
}
