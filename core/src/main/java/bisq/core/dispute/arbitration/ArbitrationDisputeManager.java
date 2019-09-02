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

package bisq.core.dispute.arbitration;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dispute.DisputeList;
import bisq.core.dispute.DisputeManager;
import bisq.core.offer.OpenOfferManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.closed.ClosedTradableManager;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.storage.Storage;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ArbitrationDisputeManager extends DisputeManager {

    @Inject
    public ArbitrationDisputeManager(P2PService p2PService,
                                     TradeWalletService tradeWalletService,
                                     BtcWalletService walletService,
                                     WalletsSetup walletsSetup,
                                     TradeManager tradeManager,
                                     ClosedTradableManager closedTradableManager,
                                     OpenOfferManager openOfferManager,
                                     KeyRing keyRing,
                                     Storage<DisputeList> storage) {
        super(p2PService, tradeWalletService, walletService, walletsSetup, tradeManager, closedTradableManager, openOfferManager, keyRing, storage);
    }
}
