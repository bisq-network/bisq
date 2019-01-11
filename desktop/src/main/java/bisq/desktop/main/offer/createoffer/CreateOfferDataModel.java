/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation,
either version 3 of the License,
or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful,
but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not,
see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.offer.createoffer;

import bisq.desktop.main.offer.MutableOfferDataModel;

import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.filter.FilterManager;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.BSFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;

import com.google.inject.Inject;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class CreateOfferDataModel extends MutableOfferDataModel {

    @Inject
    public CreateOfferDataModel(OpenOfferManager openOfferManager,
                                BtcWalletService btcWalletService,
                                BsqWalletService bsqWalletService,
                                Preferences preferences,
                                User user,
                                KeyRing keyRing,
                                P2PService p2PService,
                                PriceFeedService priceFeedService,
                                FilterManager filterManager,
                                AccountAgeWitnessService accountAgeWitnessService,
                                FeeService feeService,
                                TxFeeEstimationService txFeeEstimationService,
                                ReferralIdService referralIdService,
                                BSFormatter btcFormatter) {
        super(openOfferManager,
                btcWalletService,
                bsqWalletService,
                preferences,
                user,
                keyRing,
                p2PService,
                priceFeedService,
                filterManager,
                accountAgeWitnessService,
                feeService,
                txFeeEstimationService,
                referralIdService,
                btcFormatter);
    }
}
