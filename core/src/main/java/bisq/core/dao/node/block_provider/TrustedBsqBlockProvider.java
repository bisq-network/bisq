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

package bisq.core.dao.node.block_provider;

import bisq.network.p2p.NodeAddress;

import bisq.common.util.Utilities;

import java.util.Locale;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;


@EqualsAndHashCode
public final class TrustedBsqBlockProvider {
    private static final String CONFIG_SEPARATOR = "@";

    @Getter
    private final NodeAddress nodeAddress;
    private final byte[] encodedPublicKey;
    @Nullable
    @Getter
    private final Role role;
    @Nullable
    @Getter
    private final String operator;

    public enum Role {
        SEED,
        BRIDGE
    }

    public static TrustedBsqBlockProvider fromConfig(String entry) {
        String[] tokens = splitConfigEntry(entry);
        NodeAddress nodeAddress = toNodeAddress(tokens[0]);
        byte[] encodedPublicKey = toEncodedPublicKey(tokens[1]);
        return new TrustedBsqBlockProvider(nodeAddress, encodedPublicKey, null, null);
    }

    public static TrustedBsqBlockProvider fromResources(String entry) {
        String[] tokens = entry.split(CONFIG_SEPARATOR);
        checkArgument(tokens.length == 4, "Invalid entry in properties entry: " + entry);
        Role role = Role.valueOf(tokens[2]);
        NodeAddress address = toNodeAddress(tokens[0]);
        byte[] encodedPublicKey = toEncodedPublicKey(tokens[1]);
        String operator = tokens[3];
        return new TrustedBsqBlockProvider(address, encodedPublicKey, role, operator);
    }

    private TrustedBsqBlockProvider(NodeAddress nodeAddress,
                                    byte[] encodedPublicKey,
                                    @Nullable Role role,
                                    @Nullable String operator) {
        this.nodeAddress = nodeAddress;
        this.encodedPublicKey = encodedPublicKey;
        this.role = role;
        this.operator = operator;
    }

    public byte[] getEncodedPublicKey() {
        return encodedPublicKey.clone();
    }

    private static String[] splitConfigEntry(String entry) {
        String value = checkNotNull(entry, "entry must not be null").trim();
        String[] tokens = value.split(CONFIG_SEPARATOR, -1);
        checkArgument(tokens.length == 2,
                "Invalid entry in config entry: " + entry + ". Expected host:port" + CONFIG_SEPARATOR +
                        "pubKeyAsHex");
        return tokens;
    }

    private static byte[] toEncodedPublicKey(String value) {
        String pubKeyAsHex = normalizePubKeyAsHex(value);
        return Utilities.decodeFromHex(pubKeyAsHex);
    }

    private static String normalizePubKeyAsHex(String pubKeyAsHex) {
        String value = checkNotNull(pubKeyAsHex, "pubKeyAsHex must not be null")
                .trim()
                .toLowerCase(Locale.ENGLISH);
        checkArgument(!value.isEmpty(), "pubKeyAsHex must not be empty");
        checkArgument(value.length() % 2 == 0, "pubKeyAsHex must have even length");
        checkArgument(value.matches("[0-9a-f]+"), "pubKeyAsHex must be hex encoded");
        return value;
    }

    private static NodeAddress toNodeAddress(String value) {
        return new NodeAddress(checkNotNull(value, "nodeAddress must not be null"));
    }

}
