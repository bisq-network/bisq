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

package bisq.core.user;

import bisq.common.proto.persistable.PersistablePayload;

import com.google.protobuf.Message;

public final class BlockChainExplorer implements PersistablePayload {
    public final String name;
    public final String txUrl;
    public final String addressUrl;

    public BlockChainExplorer(String name, String txUrl, String addressUrl) {
        this.name = name;
        this.txUrl = txUrl;
        this.addressUrl = addressUrl;
    }

    @Override
    public Message toProtoMessage() {
        return protobuf.BlockChainExplorer.newBuilder().setName(name).setTxUrl(txUrl).setAddressUrl(addressUrl).build();
    }

    public static BlockChainExplorer fromProto(protobuf.BlockChainExplorer proto) {
        return new BlockChainExplorer(proto.getName(),
                proto.getTxUrl(),
                proto.getAddressUrl());
    }
}
