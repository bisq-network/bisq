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

package bisq.restapi.dto;

import bisq.core.dao.state.model.blockchain.PubKeyScript;

import java.util.List;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
public class JsonScriptPubKey {
    List<String> addresses;
    String asm;
    String hex;
    int reqSigs;
    String type;

    public JsonScriptPubKey(PubKeyScript pubKeyScript) {
        addresses = pubKeyScript.getAddresses();
        asm = pubKeyScript.getAsm();
        hex = pubKeyScript.getHex();
        reqSigs = pubKeyScript.getReqSigs();
        type = pubKeyScript.getScriptType().toString();
    }
}
