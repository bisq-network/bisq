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

package bisq.core.dao.node.lite;

import bisq.core.dao.node.block_provider.TrustedBsqBlockProvider;
import bisq.core.dao.node.block_provider.TrustedBsqBlockProviderRepository;
import bisq.core.dao.node.full.DaoBlockSignatureHash;
import bisq.core.dao.node.messages.DaoBlockSignature;
import bisq.core.dao.node.messages.SignedRawBlock;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Sig;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.security.PublicKey;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DaoBlockSignatureVerifier {
    private final TrustedBsqBlockProviderRepository trustedBsqBlockProviderRepository;

    @Inject
    public DaoBlockSignatureVerifier(TrustedBsqBlockProviderRepository trustedBsqBlockProviderRepository) {
        this.trustedBsqBlockProviderRepository = trustedBsqBlockProviderRepository;
    }

    public boolean isValid(SignedRawBlock signedRawBlock) {
        DaoBlockSignature signature = signedRawBlock.getSignature();
        NodeAddress signerNodeAddress = signature.getSignerNodeAddress();
        Optional<byte[]> signaturePubKeyBytes = getSignaturePubKeyBytes(signerNodeAddress);
        if (signaturePubKeyBytes.isEmpty()) {
            log.warn("DAO block signature ignored because signer is not trusted. signerNodeAddress={}, blockHeight={}, blockHash={}",
                    signerNodeAddress, signedRawBlock.getBlock().getHeight(), signedRawBlock.getBlock().getHash());
            return false;
        }

        try {
            PublicKey publicKey = Sig.getPublicKeyFromBytes(signaturePubKeyBytes.get());
            byte[] hash = DaoBlockSignatureHash.getHash(signedRawBlock.getBlock());
            boolean isValid = Sig.verify(publicKey, hash, signature.getSignature());
            if (!isValid) {
                log.warn("DAO block signature is invalid. signerNodeAddress={}, blockHeight={}, blockHash={}",
                        signerNodeAddress, signedRawBlock.getBlock().getHeight(), signedRawBlock.getBlock().getHash());
            }
            return isValid;
        } catch (CryptoException | RuntimeException e) {
            log.warn("DAO block signature verification failed. signerNodeAddress={}, blockHeight={}, blockHash={}",
                    signerNodeAddress, signedRawBlock.getBlock().getHeight(), signedRawBlock.getBlock().getHash(), e);
            return false;
        }
    }

    private Optional<byte[]> getSignaturePubKeyBytes(NodeAddress signerNodeAddress) {
        return trustedBsqBlockProviderRepository.getTrustedBsqBlockProviders().stream()
                .filter(provider -> provider.getNodeAddress().equals(signerNodeAddress))
                .map(TrustedBsqBlockProvider::getEncodedPublicKey)
                .findFirst();
    }
}
