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

package bisq.core.api;

import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.refundagent.RefundAgent;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;

import org.bitcoinj.core.ECKey;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.app.DevEnv.DEV_PRIVILEGE_PRIV_KEY;
import static java.net.InetAddress.getLoopbackAddress;

@Slf4j
class CoreDisputeAgentsService {

    private final Config config;
    private final KeyRing keyRing;
    private final MediatorManager mediatorManager;
    private final RefundAgentManager refundAgentManager;
    private final P2PService p2PService;
    private final NodeAddress nodeAddress;
    private final List<String> languageCodes;

    @Inject
    public CoreDisputeAgentsService(Config config,
                                    KeyRing keyRing,
                                    MediatorManager mediatorManager,
                                    RefundAgentManager refundAgentManager,
                                    P2PService p2PService) {
        this.config = config;
        this.keyRing = keyRing;
        this.mediatorManager = mediatorManager;
        this.refundAgentManager = refundAgentManager;
        this.p2PService = p2PService;
        this.nodeAddress = new NodeAddress(getLoopbackAddress().getHostAddress(), config.nodePort);
        this.languageCodes = Arrays.asList("de", "en", "es", "fr");
    }

    public void registerDisputeAgent(String disputeAgentType, String registrationKey) {
        if (!p2PService.isBootstrapped())
            throw new IllegalStateException("p2p service is not bootstrapped yet");

        if (config.baseCurrencyNetwork.isMainnet()
                || config.baseCurrencyNetwork.isDaoBetaNet()
                || !config.useLocalhostForP2P)
            throw new IllegalStateException("dispute agents must be registered in a Bisq UI");

        if (!registrationKey.equals(DEV_PRIVILEGE_PRIV_KEY))
            throw new IllegalArgumentException("invalid registration key");

        ECKey ecKey;
        String signature;
        switch (disputeAgentType) {
            case "arbitrator":
                throw new IllegalArgumentException("arbitrators must be registered in a Bisq UI");
            case "mediator":
                ecKey = mediatorManager.getRegistrationKey(registrationKey);
                signature = mediatorManager.signStorageSignaturePubKey(Objects.requireNonNull(ecKey));
                registerMediator(nodeAddress, languageCodes, ecKey, signature);
                return;
            case "refundagent":
                ecKey = refundAgentManager.getRegistrationKey(registrationKey);
                signature = refundAgentManager.signStorageSignaturePubKey(Objects.requireNonNull(ecKey));
                registerRefundAgent(nodeAddress, languageCodes, ecKey, signature);
                return;
            default:
                throw new IllegalArgumentException("unknown dispute agent type " + disputeAgentType);
        }
    }

    private void registerMediator(NodeAddress nodeAddress,
                                  List<String> languageCodes,
                                  ECKey ecKey,
                                  String signature) {
        Mediator mediator = new Mediator(nodeAddress,
                keyRing.getPubKeyRing(),
                languageCodes,
                new Date().getTime(),
                ecKey.getPubKey(),
                signature,
                null,
                null,
                null
        );
        mediatorManager.addDisputeAgent(mediator, () -> {
        }, errorMessage -> {
        });
        mediatorManager.getDisputeAgentByNodeAddress(nodeAddress).orElseThrow(() ->
                new IllegalStateException("could not register mediator"));
    }

    private void registerRefundAgent(NodeAddress nodeAddress,
                                     List<String> languageCodes,
                                     ECKey ecKey,
                                     String signature) {
        RefundAgent refundAgent = new RefundAgent(nodeAddress,
                keyRing.getPubKeyRing(),
                languageCodes,
                new Date().getTime(),
                ecKey.getPubKey(),
                signature,
                null,
                null,
                null
        );
        refundAgentManager.addDisputeAgent(refundAgent, () -> {
        }, errorMessage -> {
        });
        refundAgentManager.getDisputeAgentByNodeAddress(nodeAddress).orElseThrow(() ->
                new IllegalStateException("could not register refund agent"));
    }
}
