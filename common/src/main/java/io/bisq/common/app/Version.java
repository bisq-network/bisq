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
    public static final String VERSION = "0.6.1"; 

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
        if (newVersion.equals(currentVersion))
            return false;
        else if (getMajorVersion(newVersion) > getMajorVersion(currentVersion))
            return true;
        else if (getMajorVersion(newVersion) < getMajorVersion(currentVersion))
            return false;
        else if (getMinorVersion(newVersion) > getMinorVersion(currentVersion))
            return true;
        else if (getMinorVersion(newVersion) < getMinorVersion(currentVersion))
            return false;
        else if (getPatchVersion(newVersion) > getPatchVersion(currentVersion))
            return true;
        else if (getPatchVersion(newVersion) < getPatchVersion(currentVersion))
            return false;
        else
            return false;
    }

    private static int getSubVersion(String version, int index) {
        final String[] split = version.split("\\.");
        checkArgument(split.length == 3, "Version number must be in semantic version format (contain 2 '.'). version=" + version);
        return Integer.parseInt(split[index]);
    }

    // The version no. for the objects sent over the network. A change will break the serialization of old objects.
    // If objects are used for both network and database the network version is applied.
    // VERSION = 0.5.0 -> P2P_NETWORK_VERSION = 1
    @SuppressWarnings("ConstantConditions")
    public static final int P2P_NETWORK_VERSION = DevEnv.STRESS_TEST_MODE ? 100 : 1;

    // The version no. of the serialized data stored to disc. A change will break the serialization of old objects.
    // VERSION = 0.5.0 -> LOCAL_DB_VERSION = 1
    public static final int LOCAL_DB_VERSION = 1;

    // The version no. of the current protocol. The offer holds that version.
    // A taker will check the version of the offers to see if his version is compatible.
    // VERSION = 0.5.0 -> TRADE_PROTOCOL_VERSION = 1
    public static final int TRADE_PROTOCOL_VERSION = 1;
    private static int p2pMessageVersion;

    public static final String BSQ_TX_VERSION = "1";

    public static int getP2PMessageVersion() {
        return p2pMessageVersion;
    }

    // The version for the crypto network (BTC_Mainnet = 0, BTC_TestNet = 1, BTC_Regtest = 2, ...)
    private static int BASE_CURRENCY_NETWORK;

    public static void setBaseCryptoNetworkId(int baseCryptoNetworkId) {
        BASE_CURRENCY_NETWORK = baseCryptoNetworkId;

        // CRYPTO_NETWORK_ID is ordinal of enum. We use for changes at NETWORK_PROTOCOL_VERSION a multiplication with 10
        // to not mix up networks:
        p2pMessageVersion = BASE_CURRENCY_NETWORK + 10 * P2P_NETWORK_VERSION;
    }

    public static int getBaseCurrencyNetwork() {
        return BASE_CURRENCY_NETWORK;
    }

    public static void printVersion() {
        log.info("Version{" +
                "VERSION=" + VERSION +
                ", P2P_NETWORK_VERSION=" + P2P_NETWORK_VERSION +
                ", LOCAL_DB_VERSION=" + LOCAL_DB_VERSION +
                ", TRADE_PROTOCOL_VERSION=" + TRADE_PROTOCOL_VERSION +
                ", BASE_CURRENCY_NETWORK=" + BASE_CURRENCY_NETWORK +
                ", getP2PNetworkId()=" + getP2PMessageVersion() +
                '}');
    }

    public static final byte COMPENSATION_REQUEST_VERSION = (byte) 0x01;
    public static final byte VOTING_VERSION = (byte) 0x01;
}
