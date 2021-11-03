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

package bisq.desktop.components;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.user.Preferences;

import bisq.network.p2p.NodeAddress;

import bisq.common.util.Tuple5;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.paint.Color;

import java.util.Date;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.desktop.util.Colors.AVATAR_BLUE;
import static bisq.desktop.util.Colors.AVATAR_GREEN;
import static bisq.desktop.util.Colors.AVATAR_ORANGE;
import static bisq.desktop.util.Colors.AVATAR_RED;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class PeerInfoIconTrading extends PeerInfoIcon {
    private final AccountAgeWitnessService accountAgeWitnessService;
    private boolean isFiatCurrency;

    public PeerInfoIconTrading(NodeAddress nodeAddress,
                               String role,
                               int numTrades,
                               PrivateNotificationManager privateNotificationManager,
                               Offer offer,
                               Preferences preferences,
                               AccountAgeWitnessService accountAgeWitnessService,
                               boolean useDevPrivilegeKeys) {
        this(nodeAddress,
                role,
                numTrades,
                privateNotificationManager,
                offer,
                null,
                preferences,
                accountAgeWitnessService,
                useDevPrivilegeKeys);
    }

    public PeerInfoIconTrading(NodeAddress nodeAddress,
                               String role,
                               int numTrades,
                               PrivateNotificationManager privateNotificationManager,
                               TradeModel tradeModel,
                               Preferences preferences,
                               AccountAgeWitnessService accountAgeWitnessService,
                               boolean useDevPrivilegeKeys) {
        this(nodeAddress,
                role,
                numTrades,
                privateNotificationManager,
                tradeModel.getOffer(),
                tradeModel,
                preferences,
                accountAgeWitnessService,
                useDevPrivilegeKeys);
    }

    private PeerInfoIconTrading(NodeAddress nodeAddress,
                                String role,
                                int numTrades,
                                PrivateNotificationManager privateNotificationManager,
                                @Nullable Offer offer,
                                @Nullable TradeModel tradeModel,
                                Preferences preferences,
                                AccountAgeWitnessService accountAgeWitnessService,
                                boolean useDevPrivilegeKeys) {
        super(nodeAddress, preferences);
        this.numTrades = numTrades;
        this.accountAgeWitnessService = accountAgeWitnessService;
        if (offer == null) {
            checkNotNull(tradeModel, "Trade must not be null if offer is null.");
            offer = tradeModel.getOffer();
        }
        checkNotNull(offer, "Offer must not be null");
        isFiatCurrency = CurrencyUtil.isFiatCurrency(offer.getCurrencyCode());
        initialize(role, offer, tradeModel, privateNotificationManager, useDevPrivilegeKeys);
    }

    protected void initialize(String role,
                              Offer offer,
                              TradeModel tradeModel,
                              PrivateNotificationManager privateNotificationManager,
                              boolean useDevPrivilegeKeys) {
        boolean hasTraded = numTrades > 0;
        Tuple5<Long, Long, String, String, String> peersAccount = getPeersAccountAge(tradeModel, offer);

        Long accountAge = peersAccount.first;
        Long signAge = peersAccount.second;

        tooltipText = hasTraded ?
                Res.get("peerInfoIcon.tooltip.trade.traded", role, fullAddress, numTrades, getAccountAgeTooltip(accountAge)) :
                Res.get("peerInfoIcon.tooltip.trade.notTraded", role, fullAddress, getAccountAgeTooltip(accountAge));

        createAvatar(getRingColor(offer, tradeModel, accountAge, signAge));
        addMouseListener(numTrades, privateNotificationManager, tradeModel, offer, preferences, useDevPrivilegeKeys,
                isFiatCurrency, accountAge, signAge, peersAccount.third, peersAccount.fourth, peersAccount.fifth);
    }

    protected String getAccountAgeTooltip(Long accountAge) {
        return isFiatCurrency ? super.getAccountAgeTooltip(accountAge) : "";
    }

    protected Color getRingColor(Offer offer, TradeModel tradeModel, Long accountAge, Long signAge) {
        // outer circle
        // for altcoins we always display green
        Color ringColor = AVATAR_GREEN;
        if (isFiatCurrency) {
            switch (accountAgeWitnessService.getPeersAccountAgeCategory(hasChargebackRisk(tradeModel, offer) ? signAge : accountAge)) {
                case TWO_MONTHS_OR_MORE:
                    ringColor = AVATAR_GREEN;
                    break;
                case ONE_TO_TWO_MONTHS:
                    ringColor = AVATAR_BLUE;
                    break;
                case LESS_ONE_MONTH:
                    ringColor = AVATAR_ORANGE;
                    break;
                case UNVERIFIED:
                default:
                    ringColor = AVATAR_RED;
                    break;
            }
        }
        return ringColor;
    }

    /**
     * @param tradeModel Open trade for trading peer info to be shown
     * @param offer Open offer for trading peer info to be shown
     * @return account age, sign age, account info, sign info, sign state
     */
    private Tuple5<Long, Long, String, String, String> getPeersAccountAge(@Nullable TradeModel tradeModel,
                                                                          @Nullable Offer offer) {
        AccountAgeWitnessService.SignState signState = null;
        long signAge = -1L;
        long accountAge = -1L;

        if (tradeModel != null) {
            offer = tradeModel.getOffer();
            if (offer == null) {
                // unexpected
                return new Tuple5<>(signAge, accountAge, Res.get("peerInfo.age.noRisk"), null, null);
            }
            if (tradeModel instanceof Trade) {
                Trade trade = (Trade) tradeModel;
                signState = accountAgeWitnessService.getSignState(trade);
                signAge = accountAgeWitnessService.getWitnessSignAge(trade, new Date());
                accountAge = accountAgeWitnessService.getAccountAge(trade);
            }
        } else {
            checkNotNull(offer, "Offer must not be null if trade is null.");
            signState = accountAgeWitnessService.getSignState(offer);
            signAge = accountAgeWitnessService.getWitnessSignAge(offer, new Date());
            accountAge = accountAgeWitnessService.getAccountAge(offer);
        }

        if (signState != null && hasChargebackRisk(tradeModel, offer)) {
            String signAgeInfo = Res.get("peerInfo.age.chargeBackRisk");
            String accountSigningState = StringUtils.capitalize(signState.getDisplayString());
            if (signState.equals(AccountAgeWitnessService.SignState.UNSIGNED)) {
                signAgeInfo = null;
            }

            return new Tuple5<>(accountAge, signAge, Res.get("peerInfo.age.noRisk"), signAgeInfo, accountSigningState);
        }
        return new Tuple5<>(accountAge, signAge, Res.get("peerInfo.age.noRisk"), null, null);
    }

    private static boolean hasChargebackRisk(@Nullable TradeModel tradeModel, @Nullable Offer offer) {
        Offer offerToCheck = tradeModel != null ? tradeModel.getOffer() : offer;

        return offerToCheck != null &&
                PaymentMethod.hasChargebackRisk(offerToCheck.getPaymentMethod(), offerToCheck.getCurrencyCode());
    }
}
