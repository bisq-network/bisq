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


package bisq.network.p2p.network;

import lombok.Getter;

/**
 * The types of the built-in bridges which the Tor network settings offer. The bridges themselves come with the Tor
 * Browser version of the bundled Tor, so they stay as current as the bundle.
 * <p>
 * The preferences store the ordinal. Versions before the Tor library stored the ordinal of OBFS_4, OBFS_3, MEEK_AMAZON
 * and MEEK_AZURE. obfs3 has no bridges anymore and the meek bridges at Amazon and Azure stopped working, so SNOWFLAKE
 * takes the ordinal of OBFS_3, and the ordinals of both former meek types mean the current meek bridges. An older
 * version reads the ordinals of this enum as valid values of its own.
 */
public enum BridgeTransport {
    OBFS_4("obfs4"),
    SNOWFLAKE("snowflake"),
    MEEK("meek");

    // The name of the built-in bridges in pt_config.json of the Tor expert bundle
    @Getter
    private final String builtInBridgesName;

    BridgeTransport(String builtInBridgesName) {
        this.builtInBridgesName = builtInBridgesName;
    }

    public static BridgeTransport fromPreferences(int ordinal) {
        switch (ordinal) {
            case 1:
                return SNOWFLAKE;
            case 2:
            case 3:
                return MEEK;
            case 0:
            default:
                return OBFS_4;
        }
    }
}
