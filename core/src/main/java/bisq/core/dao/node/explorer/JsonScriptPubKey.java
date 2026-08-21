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

package bisq.core.dao.node.explorer;

import bisq.core.dao.state.model.blockchain.PubKeyScript;

import java.util.List;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
class JsonScriptPubKey {
    private final List<String> addresses;
    private final String asm;
    private final String hex;
    private final int reqSigs;
    private final String type;

    JsonScriptPubKey(PubKeyScript pubKeyScript) {
        addresses = pubKeyScript.getAddresses();
        asm = pubKeyScript.getAsm();
        hex = pubKeyScript.getHex();
        reqSigs = pubKeyScript.getReqSigs();
        type = pubKeyScript.getScriptType().toString();
    }
}
