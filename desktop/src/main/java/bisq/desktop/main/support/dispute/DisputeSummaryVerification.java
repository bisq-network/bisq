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

package bisq.desktop.main.support.dispute;

import bisq.core.locale.Res;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.agent.DisputeAgent;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Hash;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import java.security.KeyPair;
import java.security.PublicKey;

import static com.google.common.base.Preconditions.checkNotNull;

public class DisputeSummaryVerification {
    // Must not change as it is used for splitting the text for verifying the signature of the summary message
    private static final String SEPARATOR = "\n------------------------------------------------------------------------------------------\n";

    public static String signAndApply(DisputeManager<? extends DisputeList<? extends DisputeList>> disputeManager,
                                      Dispute dispute,
                                      DisputeResult disputeResult,
                                      String textToSign) {
        byte[] hash = Hash.getSha256Hash(textToSign);
        KeyPair signatureKeyPair = disputeManager.getSignatureKeyPair();
        String sigAsHex;
        try {
            byte[] signature = Sig.sign(signatureKeyPair.getPrivate(), hash);
            sigAsHex = Utilities.encodeToHex(signature);
            disputeResult.setArbitratorSignature(signature);
        } catch (CryptoException e) {
            sigAsHex = "Signing failed";
        }

        disputeResult.setArbitratorPubKey(dispute.getAgentPubKeyRing().getSignaturePubKeyBytes());
        NodeAddress agentNodeAddress = checkNotNull(disputeManager.getAgentNodeAddress(dispute));
        return Res.get("disputeSummaryWindow.close.msgWithSigAndPubKey",
                textToSign,
                SEPARATOR,
                agentNodeAddress.getFullAddress(),
                sigAsHex,
                SEPARATOR);
    }

    public static String verifySignature(String input,
                                         MediatorManager mediatorManager,
                                         RefundAgentManager refundAgentManager) {
        try {
            String[] tokens = input.split(SEPARATOR);
            String textToSign = tokens[0];
            String data = tokens[1];
            String[] dataTokens = data.split("\n");
            String fullAddress = dataTokens[0].split(": ")[1];

            NodeAddress nodeAddress = new NodeAddress(fullAddress);
            DisputeAgent disputeAgent = mediatorManager.getDisputeAgentByNodeAddress(nodeAddress).orElse(null);
            if (disputeAgent == null) {
                disputeAgent = refundAgentManager.getDisputeAgentByNodeAddress(nodeAddress).orElse(null);
            }
            checkNotNull(disputeAgent);
            PublicKey pubKey = disputeAgent.getPubKeyRing().getSignaturePubKey();

            String sigString = dataTokens[1].split(": ")[1];
            byte[] sig = Utilities.decodeFromHex(sigString);

            byte[] hash = Hash.getSha256Hash(textToSign);

            try {
                boolean result = Sig.verify(pubKey, hash, sig);
                if (result) {
                    return Res.get("support.sigCheck.popup.success");
                } else {
                    return Res.get("support.sigCheck.popup.failed");
                }
            } catch (CryptoException e) {
                return Res.get("support.sigCheck.popup.failed");
            }
        } catch (Throwable e) {
            return Res.get("support.sigCheck.popup.invalidFormat");
        }
    }
}
