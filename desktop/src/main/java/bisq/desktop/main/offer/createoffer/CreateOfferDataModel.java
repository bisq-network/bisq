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

import bisq.desktop.Navigation;
import bisq.desktop.main.offer.MakerFeeProvider;
import bisq.desktop.main.offer.MutableOfferDataModel;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.CreateOfferService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.P2PService;

import com.google.inject.Inject;

import javax.inject.Named;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class CreateOfferDataModel extends MutableOfferDataModel {

    @Inject
    public CreateOfferDataModel(CreateOfferService createOfferService,
                                OpenOfferManager openOfferManager,
                                BtcWalletService btcWalletService,
                                BsqWalletService bsqWalletService,
                                Preferences preferences,
                                User user,
                                P2PService p2PService,
                                PriceFeedService priceFeedService,
                                AccountAgeWitnessService accountAgeWitnessService,
                                FeeService feeService,
                                @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                                MakerFeeProvider makerFeeProvider,
                                Navigation navigation) {
        super(createOfferService,
                openOfferManager,
                btcWalletService,
                bsqWalletService,
                preferences,
                user,
                p2PService,
                priceFeedService,
                accountAgeWitnessService,
                feeService,
                btcFormatter,
                makerFeeProvider,
                navigation);
    }
}
