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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@ToString
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public enum ScriptTypes implements Serializable {

    // https://github.com/bitcoin/bitcoin/blob/8152d3fe57a991e9088d0b9d261d2b10936f45a9/src/script/standard.cpp
    PUB_KEY("pubkey"),
    PUB_KEY_HASH("pubkeyhash"),
    SCRIPT_HASH("scripthash"),
    MULTISIG("multisig"),
    NULL_DATA("nulldata"),
    WITNESS_V0_KEYHASH("witness_v0_keyhash"),
    WITNESS_V0_SCRIPTHASH("witness_v0_scripthash"),
    NONSTANDARD("nonstandard");

    private final String name;


    @JsonValue
    public String getName() {
        return name;
    }

    @JsonCreator
    public static ScriptTypes forName(String name) {
        if (name != null) {
            for (ScriptTypes scriptType : ScriptTypes.values()) {
                if (name.equals(scriptType.getName())) {
                    return scriptType;
                }
            }
        }
        throw new IllegalArgumentException("Expected the argument to be a valid 'bitcoind' script type, "
                + "but was invalid/unsupported instead. Received scriptType=" + name);
    }
}