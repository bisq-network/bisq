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

package io.bisq.core.dao.blockchain.btcd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.bisq.common.app.Version;
import io.bisq.common.util.JsonExclude;
import lombok.AllArgsConstructor;
import lombok.Value;

import javax.annotation.concurrent.Immutable;
import java.io.Serializable;
import java.util.List;

@Value
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Immutable
public class PubKeyScript implements Serializable {
    @JsonExclude
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final Integer reqSigs;
    private final ScriptTypes type;
    private final List<String> addresses;
    private final String asm;
    private final String hex;

    public PubKeyScript(com.neemre.btcdcli4j.core.domain.PubKeyScript scriptPubKey) {
        this(scriptPubKey.getReqSigs(),
                ScriptTypes.forName(scriptPubKey.getType().getName()),
                scriptPubKey.getAddresses(),
                scriptPubKey.getAsm(),
                scriptPubKey.getHex());
    }
}