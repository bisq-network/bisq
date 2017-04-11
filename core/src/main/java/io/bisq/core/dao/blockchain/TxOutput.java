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

import com.neemre.btcdcli4j.core.domain.PubKeyScript;
import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Version;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

@Getter
@EqualsAndHashCode
@Slf4j
public class TxOutput implements Serializable {
    // Immutable
    private final int index;
    private final long value;
    private final String txId;
    private final PubKeyScript pubKeyScript;
    private final int blockHeight;
    private final long time;
    private final String txVersion = Version.BSQ_TX_VERSION;

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

    public TxOutput(int index,
                    long value,
                    String txId,
                    PubKeyScript pubKeyScript,
                    int blockHeight,
                    long time) {
        this.index = index;
        this.value = value;
        this.txId = txId;
        this.pubKeyScript = pubKeyScript;
        this.blockHeight = blockHeight;
        this.time = time;
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
