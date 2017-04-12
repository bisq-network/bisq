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

package io.bisq.core.dao.blockchain;

import com.google.protobuf.Message;
import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Version;
import io.bisq.common.util.JsonExclude;
import io.bisq.core.dao.blockchain.btcd.PubKeyScript;
import io.bisq.network.p2p.storage.payload.LazyProcessedStoragePayload;
import io.bisq.network.p2p.storage.payload.PersistedStoragePayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Getter
@EqualsAndHashCode
@Slf4j
public class TxOutput implements LazyProcessedStoragePayload, PersistedStoragePayload {
    @JsonExclude
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    @JsonExclude
    public static final long TTL = TimeUnit.DAYS.toMillis(30);

    // Immutable
    private final int index;
    private final long value;
    private final String txId;
    private final PubKeyScript pubKeyScript;
    private final int blockHeight;
    private final long time;
    private final String txVersion = Version.BSQ_TX_VERSION;
    @JsonExclude
    private PublicKey signaturePubKey;

    // Mutable
    @Setter
    private boolean isBsqCoinBase;
    @Setter
    private boolean isVerified;
    @Setter
    private long burnedFee;

    @Nullable
    @Setter
    private long btcTxFee;

    @Nullable
    @Setter
    private SpendInfo spendInfo;

    // Lazy set
    @Nullable
    private String address;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility 
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new 
    // field in a class would break that hash and therefore break the storage mechanism.
    @Getter
    @Nullable
    private HashMap<String, String> extraDataMap;

    public TxOutput(int index,
                    long value,
                    String txId,
                    PubKeyScript pubKeyScript,
                    int blockHeight,
                    long time,
                    PublicKey signaturePubKey) {
        this.index = index;
        this.value = value;
        this.txId = txId;
        this.pubKeyScript = pubKeyScript;
        this.blockHeight = blockHeight;
        this.time = time;
        this.signaturePubKey = signaturePubKey;
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return signaturePubKey;
    }

    @Nullable
    @Override
    public HashMap<String, String> getExtraDataMap() {
        return extraDataMap;
    }

    public List<String> getAddresses() {
        return pubKeyScript.getAddresses();
    }

    public String getAddress() {
        if (address == null) {
            // Only at raw MS outputs addresses have more then 1 entry 
            // We do not support raw MS for BSQ but lets see if is needed anyway, might be removed 
            final List<String> addresses = pubKeyScript.getAddresses();
            if (addresses.size() == 1) {
                address = addresses.get(0);
            } else if (addresses.size() > 1) {
                final String msg = "We got a raw Multisig script. That is not supported for BSQ tokens.";
                log.warn(msg);
                address = addresses.toString();
                if (DevEnv.DEV_MODE)
                    throw new RuntimeException(msg);
            } else if (addresses.isEmpty()) {
                final String msg = "We got no address. Unsupported pubKeyScript";
                log.warn(msg);
                address = "";
                if (DevEnv.DEV_MODE)
                    throw new RuntimeException(msg);
            }
        }
        return address;
    }

    public boolean isUnSpend() {
        return spendInfo == null;
    }

    public boolean hasBurnedFee() {
        return burnedFee > 0;
    }

    public String getTxoId() {
        return txId + ":" + index;
    }

    public TxIdIndexTuple getTxIdIndexTuple() {
        return new TxIdIndexTuple(txId, index);
    }

    @Override
    public Message toProto() {
        return null;
    }

    @Override
    public String toString() {
        return "TxOutput{" +
                "\n     index=" + index +
                ",\n     value=" + value +
                ",\n     txId='" + txId + '\'' +
                ",\n     pubKeyScript=" + pubKeyScript +
                ",\n     blockHeight=" + blockHeight +
                ",\n     time=" + time +
                ",\n     txVersion='" + txVersion + '\'' +
                ",\n     isBsqCoinBase=" + isBsqCoinBase +
                ",\n     isVerified=" + isVerified +
                ",\n     burnedFee=" + burnedFee +
                ",\n     btcTxFee=" + btcTxFee +
                ",\n     spendInfo=" + spendInfo +
                ",\n     address='" + getAddress() + '\'' +
                "\n}";
    }
}
