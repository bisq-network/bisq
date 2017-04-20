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

package io.bisq.core.dao.blockchain.json;

import io.bisq.core.dao.blockchain.btcd.PubKeyScript;
import lombok.Value;

import java.util.List;

@Value
public class ScriptPubKeyForJson {
    private final List<String> addresses;
    private final String asm;
    private final String hex;
    private final int reqSigs;
    private final String type;

    public ScriptPubKeyForJson(PubKeyScript pubKeyScript) {
        addresses = pubKeyScript.getAddresses();
        asm = pubKeyScript.getAsm();
        hex = pubKeyScript.getHex();
        reqSigs = pubKeyScript.getReqSigs();
        type = pubKeyScript.getType().toString();
    }
}
