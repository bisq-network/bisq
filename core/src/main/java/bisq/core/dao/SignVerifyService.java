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

package bisq.core.dao;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;

import bisq.common.util.Utilities;

import org.bitcoinj.core.ECKey;

import javax.inject.Inject;

import java.security.SignatureException;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.core.Utils.HEX;

@Slf4j
public class SignVerifyService {
    private final BsqWalletService bsqWalletService;
    private final DaoStateService daoStateService;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SignVerifyService(BsqWalletService bsqWalletService,
                             DaoStateService daoStateService) {
        this.bsqWalletService = bsqWalletService;
        this.daoStateService = daoStateService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<Tx> getTx(String txId) {
        return daoStateService.getTx(txId);
    }

    // Of connected output of first input. Used for signing and verification.
    // Proofs ownership of the proof of burn tx.
    public byte[] getPubKey(String txId) {
        return daoStateService.getTx(txId)
                .map(tx -> tx.getTxInputs().get(0))
                .map(e -> Utilities.decodeFromHex(e.getPubKey()))
                .orElse(new byte[0]);
    }

    public String getPubKeyAsHex(String txId) {
        return Utilities.bytesAsHexString(getPubKey(txId));
    }

    public Optional<String> sign(String txId, String message) {
        byte[] pubKey = getPubKey(txId);
        ECKey key = bsqWalletService.findKeyFromPubKey(pubKey);
        if (key == null)
            return Optional.empty();

        try {
            String signatureBase64 = bsqWalletService.isEncrypted()
                    ? key.signMessage(message, bsqWalletService.getAesKey())
                    : key.signMessage(message);
            return Optional.of(signatureBase64);
        } catch (Throwable t) {
            log.error(t.toString());
            t.printStackTrace();
            return Optional.empty();
        }
    }

    public void verify(String message, String pubKey, String signatureBase64) throws SignatureException {
        ECKey key = ECKey.fromPublicOnly(HEX.decode(pubKey));
        checkNotNull(key, "ECKey must not be null");
        key.verifyMessage(message, signatureBase64);
    }
}
