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

package bisq.core.dao.node.full;

import bisq.core.dao.node.full.rpc.dto.RawDtoInput;
import bisq.core.dao.node.full.rpc.dto.DtoSignatureScript;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RpcServiceTest {
    private static final String SIGNATURE = "3045" +
            "022100b6c2fa10587d6fed3a0eecfd098b160f69a850beca139fe03ef65bec4cba1c5b" +
            "02204a833a16c22bbd32722243ea3270e672f646ee9406e8797e11093951e92efbd5";
    private static final String SIGNATURE_1 = "3044" +
            "02201f00d9a4aab1a3a239f1ad95a910092c0c55423480d609eaad4599cf7ecb7f48" +
            "0220668b1a9cf5624b1c4ece6da3f64bc6021e509f588ae1006601acd8a9f83b3576";
    private static final String SIGNATURE_2 = "3045" +
            "022100982eca77a72a2bdba51b9231afd4521400bee1bb7830634eb26db2b0c621bc46" +
            "022073d7325916e2b5ceb1d2e510a5161fd9115105a8dafa94068864624bb10d190e";
    private static final String PUB_KEY =
            "03dcca91c2ec7229f1b4f4c4f664c92d3303dddef8d38736f6a7f28de16f3ce416";
    private static final String PUB_KEY_1 =
            "0229713ad5c604c585128b3a5da6de20d78fc33bd3b595e9991f4c0e1fee99f845";
    private static final String PUB_KEY_2 =
            "0398ad45a74bf5a5c5a8ec31de6815d2e805a23e68c0f8001770e74bc4c17c5b31";
    private static final String MULTISIG_REDEEM_SCRIPT_HEX =
            "5221" + PUB_KEY_1 + "21" + PUB_KEY_2 + "52ae";   // OP_2 pub1 pub2 OP_2 OP_CHECKMULTISIG
    private static final String P2WPKH_REDEEM_SCRIPT_HEX =
            "0014" + "9bc809698674ec7c01d35d438e9d0de1aa87b6c8";                         // 0 hash160
    private static final String P2WSH_REDEEM_SCRIPT_HEX =
            "0020" + "223d978073802f79e6ecdc7591e5dc1f0ea7030d6466f73c6b90391bc72e886f"; // 0 hash256

    @Test
    public void testExtractPubKeyAsHex_coinbase() {
        checkExtractPubKeyAsHexReturnsNull(new RawDtoInput());
    }

    @Test
    public void testExtractPubKeyAsHex_P2PK() {
        var input = rawInput(SIGNATURE + "[ALL]");
        checkExtractPubKeyAsHexReturnsNull(input);
    }

    @Test
    public void testExtractPubKeyAsHex_P2PKH() {
        var input = rawInput(SIGNATURE + "[ALL] " + PUB_KEY);
        assertEquals(PUB_KEY, RpcService.extractPubKeyAsHex(input, true));
        assertEquals(PUB_KEY, RpcService.extractPubKeyAsHex(input, false));
    }

    @Test
    public void testExtractPubKeyAsHex_P2WPKH() {
        var input = rawInput("", SIGNATURE + "01", PUB_KEY);
        assertEquals(PUB_KEY, RpcService.extractPubKeyAsHex(input, true));
        assertNull(RpcService.extractPubKeyAsHex(input, false));
    }

    @Test
    public void testExtractPubKeyAsHex_P2SH_P2WPKH() {
        var input = rawInput(P2WPKH_REDEEM_SCRIPT_HEX, SIGNATURE + "01", PUB_KEY);
        assertEquals(PUB_KEY, RpcService.extractPubKeyAsHex(input, true));
        assertNull(RpcService.extractPubKeyAsHex(input, false));
    }

    @Test
    public void testExtractPubKeyAsHex_P2PKH_nonDefaultSighash() {
        var input = rawInput(SIGNATURE + "[SINGLE|ANYONECANPAY] " + PUB_KEY);
        checkExtractPubKeyAsHexReturnsNull(input);
    }

    @Test
    public void testExtractPubKeyAsHex_P2WPKH_nonDefaultSighash() {
        var input = rawInput("", SIGNATURE + "82", PUB_KEY);
        checkExtractPubKeyAsHexReturnsNull(input);
    }

    @Test
    public void testExtractPubKeyAsHex_P2SH_P2WPKH_nonDefaultSighash() {
        var input = rawInput(P2WPKH_REDEEM_SCRIPT_HEX, SIGNATURE + "82", PUB_KEY);
        checkExtractPubKeyAsHexReturnsNull(input);
    }

    @Test
    public void testExtractPubKeyAsHex_P2SH_multisig() {
        var input = rawInput("0 " + SIGNATURE_1 + "[ALL] " + SIGNATURE_2 + "[ALL] " + MULTISIG_REDEEM_SCRIPT_HEX);
        checkExtractPubKeyAsHexReturnsNull(input);
    }

    @Test
    public void testExtractPubKeyAsHex_P2SH_multisig_nonDefaultSighash() {
        var input = rawInput("0 " + SIGNATURE_1 + "[ALL] " + SIGNATURE_2 + "[NONE] " + MULTISIG_REDEEM_SCRIPT_HEX);
        checkExtractPubKeyAsHexReturnsNull(input);
    }

    @Test
    public void testExtractPubKeyAsHex_P2WSH_multisig() {
        var input = rawInput("", "", SIGNATURE_1 + "01", SIGNATURE_2 + "01", MULTISIG_REDEEM_SCRIPT_HEX);
        checkExtractPubKeyAsHexReturnsNull(input);
    }

    @Test
    public void testExtractPubKeyAsHex_P2SH_P2WSH_multisig() {
        var input = rawInput(P2WSH_REDEEM_SCRIPT_HEX, "", SIGNATURE_1 + "01", SIGNATURE_2 + "01", MULTISIG_REDEEM_SCRIPT_HEX);
        checkExtractPubKeyAsHexReturnsNull(input);
    }

    private static void checkExtractPubKeyAsHexReturnsNull(RawDtoInput input) {
        assertNull(RpcService.extractPubKeyAsHex(input, true));
        assertNull(RpcService.extractPubKeyAsHex(input, false));
    }

    private static RawDtoInput rawInput(String asm, String... txInWitness) {
        var input = new RawDtoInput();
        var scriptSig = new DtoSignatureScript();
        scriptSig.setAsm(asm);
        input.setScriptSig(scriptSig);
        input.setTxInWitness(txInWitness.length > 0 ? List.of(txInWitness) : null);
        return input;
    }
}
