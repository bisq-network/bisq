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

package io.bisq.core.dao.blockchain.btcd;

import com.google.common.collect.ImmutableList;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.ArrayList;

@Value
@AllArgsConstructor
public class PubKeyScript implements PersistablePayload {
    private final int reqSigs;
    private final ScriptType scriptType;
    private final ImmutableList<String> addresses;
    private final String asm;
    private final String hex;

    public PubKeyScript(com.neemre.btcdcli4j.core.domain.PubKeyScript scriptPubKey) {
        this(scriptPubKey.getReqSigs() != null ? scriptPubKey.getReqSigs() : 0,
                ScriptType.forName(scriptPubKey.getType().getName()),
                scriptPubKey.getAddresses() != null ? ImmutableList.copyOf(scriptPubKey.getAddresses()) : null,
                scriptPubKey.getAsm(),
                scriptPubKey.getHex());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.PubKeyScript toProtoMessage() {
        return PB.PubKeyScript.newBuilder()
                .setReqSigs(reqSigs)
                .setScriptType(scriptType.toProtoMessage())
                .addAllAddresses(addresses)
                .setAsm(asm)
                .setHex(hex)
                .build();
    }

    public static PubKeyScript fromProto(PB.PubKeyScript proto) {
        return new PubKeyScript(proto.getReqSigs(),
                ScriptType.fromProto(proto.getScriptType()),
                proto.getAddressesList().isEmpty() ? ImmutableList.copyOf(new ArrayList<>()) : ImmutableList.copyOf(proto.getAddressesList()),
                proto.getAsm(),
                proto.getHex());
    }
}