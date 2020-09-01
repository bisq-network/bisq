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
import bisq.network.p2p.P2PServiceListener;

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

@Slf4j
class CoreDisputeAgentService {

    private final Config config;
    private final KeyRing keyRing;
    private final MediatorManager mediatorManager;
    private final RefundAgentManager refundAgentManager;
    private final P2PService p2PService;

    @Inject
    public CoreDisputeAgentService(Config config,
                                   KeyRing keyRing,
                                   MediatorManager mediatorManager,
                                   RefundAgentManager refundAgentManager,
                                   P2PService p2PService) {
        this.config = config;
        this.keyRing = keyRing;
        this.mediatorManager = mediatorManager;
        this.refundAgentManager = refundAgentManager;
        this.p2PService = p2PService;
        setupListeners();
    }

    private boolean shouldRegisterTestArbitrationDisputeAgents() {
        return p2PService.isBootstrapped()
                && config.useDevPrivilegeKeys
                && config.baseCurrencyNetwork.isRegtest()
                && config.useLocalhostForP2P
                && config.appName.equals("bisq-BTC_REGTEST_Arb_dao");
    }

    private void registerTestArbitratorDisputeAgents() {
        NodeAddress nodeAddress = new NodeAddress("localhost:" + config.nodePort);
        if (!mediatorManager.getDisputeAgentByNodeAddress(nodeAddress).isPresent()
                || !refundAgentManager.getDisputeAgentByNodeAddress(nodeAddress).isPresent()) {
            List<String> languageCodes = Arrays.asList("de", "en", "es", "fr");
            ECKey registrationKey = mediatorManager.getRegistrationKey(DEV_PRIVILEGE_PRIV_KEY);
            String signature = mediatorManager.signStorageSignaturePubKey(Objects.requireNonNull(registrationKey));

            registerTestMediator(nodeAddress, languageCodes, registrationKey, signature);
            registerTestRefundAgent(nodeAddress, languageCodes, registrationKey, signature);
        }
    }

    private void registerTestMediator(NodeAddress nodeAddress,
                                      List<String> languageCodes,
                                      ECKey registrationKey,
                                      String signature) {
        Mediator mediator = new Mediator(
                nodeAddress,
                keyRing.getPubKeyRing(),
                languageCodes,
                new Date().getTime(),
                registrationKey.getPubKey(),
                signature,
                null,
                null,
                null
        );
        mediatorManager.addDisputeAgent(mediator, () -> {
        }, errorMessage -> {
        });
        mediatorManager.getDisputeAgentByNodeAddress(nodeAddress)
                .orElseThrow(() -> new IllegalStateException("Could not register test mediation agent"));
    }

    private void registerTestRefundAgent(NodeAddress nodeAddress,
                                         List<String> languageCodes,
                                         ECKey registrationKey,
                                         String signature) {
        RefundAgent refundAgent = new RefundAgent(
                nodeAddress,
                keyRing.getPubKeyRing(),
                languageCodes,
                new Date().getTime(),
                registrationKey.getPubKey(),
                signature,
                null,
                null,
                null
        );
        refundAgentManager.addDisputeAgent(refundAgent, () -> {
        }, errorMessage -> {
        });
        refundAgentManager.getDisputeAgentByNodeAddress(nodeAddress)
                .orElseThrow(() -> new IllegalStateException("Could not register test refund agent"));
    }

    private void setupListeners() {
        p2PService.addP2PServiceListener(new P2PServiceListener() {

            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onHiddenServicePublished() {
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
            }

            @Override
            public void onRequestCustomBridges() {
            }

            @Override
            public void onDataReceived() {
                if (shouldRegisterTestArbitrationDisputeAgents())
                    registerTestArbitratorDisputeAgents();
            }

            @Override
            public void onNoSeedNodeAvailable() {
            }

            @Override
            public void onNoPeersAvailable() {
            }

            @Override
            public void onUpdatedDataReceived() {
            }
        });
    }
}
