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

package io.bisq.common.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;

public class Version {
    private static final Logger log = LoggerFactory.getLogger(Version.class);

    // The application versions
    // VERSION = 0.5.0 introduces proto buffer for the P2P network and local DB and is a not backward compatible update
    // Therefore all sub versions start again with 1
    // We use semantic versioning with major, minor and patch
    public static final String VERSION = "0.5.0";

    public static int getMajorVersion(String version) {
        return getSubVersion(version, 0);
    }

    public static int getMinorVersion(String version) {
        return getSubVersion(version, 1);
    }

    public static int getPatchVersion(String version) {
        return getSubVersion(version, 2);
    }

    public static boolean isNewVersion(String newVersion) {
        return isNewVersion(newVersion, VERSION);
    }

    static boolean isNewVersion(String newVersion, String currentVersion) {
        return getMajorVersion(newVersion) > getMajorVersion(currentVersion) ||
                getMinorVersion(newVersion) > getMinorVersion(currentVersion) ||
                getPatchVersion(newVersion) > getPatchVersion(currentVersion);
    }

    static int getSubVersion(String version, int index) {
        final String[] split = version.split("\\.");
        checkArgument(split.length == 3, "Version number must be in semantic version format (contain 2 '.'). version=" + version);
        return Integer.parseInt(split[index]);
    }

    // The version no. for the objects sent over the network. A change will break the serialization of old objects.
    // If objects are used for both network and database the network version is applied.
    // VERSION = 0.5.0.0 -> P2P_NETWORK_VERSION = 1
    @SuppressWarnings("ConstantConditions")
    public static final int P2P_NETWORK_VERSION = DevEnv.STRESS_TEST_MODE ? 100 : 1;

    // The version no. of the serialized data stored to disc. A change will break the serialization of old objects.
    // VERSION = 0.5.0.0 -> LOCAL_DB_VERSION = 1
    public static final int LOCAL_DB_VERSION = 1;

    // The version no. of the current protocol. The offer holds that version.
    // A taker will check the version of the offers to see if his version is compatible.
    // VERSION = 0.5.0.0 -> TRADE_PROTOCOL_VERSION = 1
    public static final int TRADE_PROTOCOL_VERSION = 1;
    private static int p2pMessageVersion;

    public static final String BSQ_TX_VERSION = "1";

    public static int getP2PMessageVersion() {
        // TODO investigate why a changed NETWORK_PROTOCOL_VERSION for the serialized objects does not trigger
        // reliable a disconnect., but java serialisation should be replaced anyway, so using one existing field
        // for the version is fine.
        return p2pMessageVersion;
    }

    // The version for the bitcoin network (Mainnet = 0, TestNet = 1, Regtest = 2)
    private static int CRYPTO_NETWORK_ID;

    public static void setBaseCryptoNetworkId(int btcNetworkId) {
        CRYPTO_NETWORK_ID = btcNetworkId;

        // CRYPTO_NETWORK_ID  is 0, 1 or 2, we use for changes at NETWORK_PROTOCOL_VERSION a multiplication with 10
        // to avoid conflicts:
        // E.g. btc CRYPTO_NETWORK_ID=1, NETWORK_PROTOCOL_VERSION=1 -> getNetworkId()=2;
        // CRYPTO_NETWORK_ID=0, NETWORK_PROTOCOL_VERSION=2 -> getNetworkId()=2; -> wrong
        p2pMessageVersion = CRYPTO_NETWORK_ID + 10 * P2P_NETWORK_VERSION;
    }

    public static int getCryptoNetworkId() {
        return CRYPTO_NETWORK_ID;
    }

    public static void printVersion() {
        log.info("Version{" +
                "VERSION=" + VERSION +
                ", P2P_NETWORK_VERSION=" + P2P_NETWORK_VERSION +
                ", LOCAL_DB_VERSION=" + LOCAL_DB_VERSION +
                ", TRADE_PROTOCOL_VERSION=" + TRADE_PROTOCOL_VERSION +
                ", CRYPTO_NETWORK_ID=" + CRYPTO_NETWORK_ID +
                ", getP2PNetworkId()=" + getP2PMessageVersion() +
                '}');
    }

    public static final byte COMPENSATION_REQUEST_VERSION = (byte) 0x01;
    public static final byte VOTING_VERSION = (byte) 0x01;
}
