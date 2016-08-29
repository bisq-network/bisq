/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Version {
    private static final Logger log = LoggerFactory.getLogger(Version.class);

    // The application versions
    public static final String VERSION = "0.4.9.5";

    // The version nr. for the objects sent over the network. A change will break the serialization of old objects.
    // If objects are used for both network and database the network version is applied.
    // VERSION = 0.3.4 -> P2P_NETWORK_VERSION = 1
    // VERSION = 0.3.5 -> P2P_NETWORK_VERSION = 2
    // VERSION = 0.4.0 -> P2P_NETWORK_VERSION = 3
    // VERSION = 0.4.2 -> P2P_NETWORK_VERSION = 4
    public static final int P2P_NETWORK_VERSION = DevFlags.STRESS_TEST_MODE ? 100 : 4;

    // The version nr. of the serialized data stored to disc. A change will break the serialization of old objects.
    // VERSION = 0.3.4 -> LOCAL_DB_VERSION = 1
    // VERSION = 0.3.5 -> LOCAL_DB_VERSION = 2
    // VERSION = 0.4.0 -> LOCAL_DB_VERSION = 3
    // VERSION = 0.4.2 -> LOCAL_DB_VERSION = 4
    public static final int LOCAL_DB_VERSION = 4;

    // The version nr. of the current protocol. The offer holds that version. 
    // A taker will check the version of the offers to see if his version is compatible.
    public static final int TRADE_PROTOCOL_VERSION = 1;
    private static int p2pMessageVersion;

    public static int getP2PMessageVersion() {
        // TODO investigate why a changed NETWORK_PROTOCOL_VERSION for the serialized objects does not trigger 
        // reliable a disconnect., but java serialisation should be replaced anyway, so using one existing field
        // for the version is fine.
        return p2pMessageVersion;
    }

    // The version for the bitcoin network (Mainnet = 0, TestNet = 1, Regtest = 2)
    private static int BTC_NETWORK_ID;

    public static void setBtcNetworkId(int btcNetworkId) {
        BTC_NETWORK_ID = btcNetworkId;

        // BTC_NETWORK_ID  is 0, 1 or 2, we use for changes at NETWORK_PROTOCOL_VERSION a multiplication with 10 
        // to avoid conflicts:
        // E.g. btc BTC_NETWORK_ID=1, NETWORK_PROTOCOL_VERSION=1 -> getNetworkId()=2;
        // BTC_NETWORK_ID=0, NETWORK_PROTOCOL_VERSION=2 -> getNetworkId()=2; -> wrong
        p2pMessageVersion = BTC_NETWORK_ID + 10 * P2P_NETWORK_VERSION;
    }

    public static int getBtcNetworkId() {
        return BTC_NETWORK_ID;
    }

    public static void printVersion() {
        log.info("Version{" +
                "VERSION=" + VERSION +
                ", P2P_NETWORK_VERSION=" + P2P_NETWORK_VERSION +
                ", LOCAL_DB_VERSION=" + LOCAL_DB_VERSION +
                ", TRADE_PROTOCOL_VERSION=" + TRADE_PROTOCOL_VERSION +
                ", BTC_NETWORK_ID=" + BTC_NETWORK_ID +
                ", getP2PNetworkId()=" + getP2PMessageVersion() +
                '}');
    }
}
