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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.node.full;

import bisq.core.dao.node.messages.DaoBlockSignature;
import bisq.core.dao.node.messages.SignedRawBlock;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.Sig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DaoBlockSigningService {
    private final KeyRing keyRing;

    @Inject
    public DaoBlockSigningService(KeyRing keyRing) {
        this.keyRing = keyRing;
    }

    public SignedRawBlock sign(RawBlock rawBlock, NodeAddress signerNodeAddress) {
        try {
            byte[] hash = DaoBlockSignatureHash.getHash(rawBlock);
            byte[] signature = Sig.sign(keyRing.getSignatureKeyPair().getPrivate(), hash);
            return new SignedRawBlock(rawBlock, new DaoBlockSignature(signerNodeAddress, signature));
        } catch (CryptoException e) {
            throw new IllegalStateException("Could not sign DAO raw block at height " + rawBlock.getHeight(), e);
        }
    }
}
