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

package bisq.desktop.main.market;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.main.MainView;
import bisq.desktop.main.market.offerbook.OfferBookChartView;
import bisq.desktop.main.market.spread.SpreadView;
import bisq.desktop.main.market.spread.SpreadViewPaymentMethod;
import bisq.desktop.main.market.trades.TradesChartsView;
import bisq.desktop.main.offer.offerbook.OfferBook;
import bisq.desktop.main.offer.offerbook.OfferBookListItem;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.DisplayUtils;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatistics3StorageService;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.base.Joiner;

import org.apache.commons.lang3.StringUtils;

import javafx.fxml.FXML;

import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import javafx.beans.value.ChangeListener;

import javafx.event.EventHandler;

import java.util.List;
import java.util.stream.Collectors;

@FxmlView
public class MarketView extends ActivatableView<TabPane, Void> {
    @FXML
    Tab offerBookTab, tradesTab, spreadTab, spreadTabPaymentMethod;
    private final ViewLoader viewLoader;
    private final TradeStatistics3StorageService tradeStatistics3StorageService;
    private final OfferBook offerBook;
    private final CoinFormatter formatter;
    private final Navigation navigation;
    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
    private EventHandler<KeyEvent> keyEventEventHandler;
    private Scene scene;


    @Inject
    public MarketView(CachingViewLoader viewLoader,
                      TradeStatistics3StorageService tradeStatistics3StorageService,
                      OfferBook offerBook,
                      @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                      Navigation navigation) {
        this.viewLoader = viewLoader;
        this.tradeStatistics3StorageService = tradeStatistics3StorageService;
        this.offerBook = offerBook;
        this.formatter = formatter;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        offerBookTab.setText(Res.get("market.tabs.offerBook").toUpperCase());
        spreadTab.setText(Res.get("market.tabs.spreadCurrency").toUpperCase());
        spreadTabPaymentMethod.setText(Res.get("market.tabs.spreadPayment").toUpperCase());
        tradesTab.setText(Res.get("market.tabs.trades").toUpperCase());

        navigationListener = (viewPath, data) -> {
            if (viewPath.size() == 3 && viewPath.indexOf(MarketView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == offerBookTab)
                navigation.navigateTo(MainView.class, MarketView.class, OfferBookChartView.class);
            else if (newValue == tradesTab)
                navigation.navigateTo(MainView.class, MarketView.class, TradesChartsView.class);
            else if (newValue == spreadTab)
                navigation.navigateTo(MainView.class, MarketView.class, SpreadView.class);
            else if (newValue == spreadTabPaymentMethod)
                navigation.navigateTo(MainView.class, MarketView.class, SpreadViewPaymentMethod.class);
        };

        keyEventEventHandler = keyEvent -> {
            if (Utilities.isCtrlPressed(KeyCode.T, keyEvent)) {
                String allTradesWithReferralId = getAllTradesWithReferralId();
                new Popup().message(StringUtils.abbreviate(allTradesWithReferralId, 600))
                        .actionButtonText(Res.get("shared.copyToClipboard"))
                        .onAction(() -> Utilities.copyToClipboard(allTradesWithReferralId))
                        .show();
            } else if (Utilities.isCtrlPressed(KeyCode.O, keyEvent)) {
                String allOffersWithReferralId = getAllOffersWithReferralId();
                new Popup().message(StringUtils.abbreviate(allOffersWithReferralId, 600))
                        .actionButtonText(Res.get("shared.copyToClipboard"))
                        .onAction(() -> Utilities.copyToClipboard(allOffersWithReferralId))
                        .show();
            }
        };
    }

    @Override
    protected void activate() {
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (root.getSelectionModel().getSelectedItem() == offerBookTab)
            navigation.navigateTo(MainView.class, MarketView.class, OfferBookChartView.class);
        else if (root.getSelectionModel().getSelectedItem() == tradesTab)
            navigation.navigateTo(MainView.class, MarketView.class, TradesChartsView.class);
        else if (root.getSelectionModel().getSelectedItem() == spreadTab)
            navigation.navigateTo(MainView.class, MarketView.class, SpreadView.class);
        else
            navigation.navigateTo(MainView.class, MarketView.class, SpreadViewPaymentMethod.class);

        if (root.getScene() != null) {
            scene = root.getScene();
            scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
        }
    }

    @Override
    protected void deactivate() {
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);

        // root.getScene() is null already so we used a field property
        if (scene != null)
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
    }

    private void loadView(Class<? extends View> viewClass) {
        final Tab tab;
        View view = viewLoader.load(viewClass);

        if (view instanceof OfferBookChartView) tab = offerBookTab;
        else if (view instanceof TradesChartsView) tab = tradesTab;
        else if (view instanceof SpreadViewPaymentMethod) tab = spreadTabPaymentMethod;
        else if (view instanceof SpreadView) tab = spreadTab;
        else throw new IllegalArgumentException("Navigation to " + viewClass + " is not supported");

        if (tab.getContent() != null && tab.getContent() instanceof ScrollPane) {
            ((ScrollPane) tab.getContent()).setContent(view.getRoot());
        } else {
            tab.setContent(view.getRoot());
        }
        root.getSelectionModel().select(tab);
    }

    private String getAllTradesWithReferralId() {
        // We don't use the list from the tradeStatisticsManager as that has filtered the duplicates but we want to get
        // all items of both traders in case the referral ID was only set by one trader.
        // If both traders had set it the tradeStatistics is only delivered once.
        // If both traders used a different referral ID then we would get 2 objects.
        List<String> list = tradeStatistics3StorageService.getMapOfAllData().values().stream()
                .filter(e -> e instanceof TradeStatistics3)
                .map(e -> (TradeStatistics3) e)
                .filter(tradeStatistics3 -> tradeStatistics3.getExtraDataMap() != null)
                .filter(tradeStatistics3 -> tradeStatistics3.getExtraDataMap().get(OfferPayload.REFERRAL_ID) != null)
                .map(tradeStatistics3 -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Date: ").append(DisplayUtils.formatDateTime(tradeStatistics3.getDate())).append("\n")
                            .append("Market: ").append(CurrencyUtil.getCurrencyPair(tradeStatistics3.getCurrency())).append("\n")
                            .append("Price: ").append(FormattingUtils.formatPrice(tradeStatistics3.getTradePrice())).append("\n")
                            .append("Amount: ").append(formatter.formatCoin(tradeStatistics3.getTradeAmount())).append("\n")
                            .append("Volume: ").append(VolumeUtil.formatVolume(tradeStatistics3.getTradeVolume())).append("\n")
                            .append("Payment method: ").append(Res.get(tradeStatistics3.getPaymentMethodId())).append("\n")
                            .append("ReferralID: ").append(tradeStatistics3.getExtraDataMap().get(OfferPayload.REFERRAL_ID));
                    return sb.toString();
                })
                .collect(Collectors.toList());
        return Joiner.on("\n\n").join(list);
    }

    private String getAllOffersWithReferralId() {
        List<String> list = offerBook.getOfferBookListItems().stream()
                .map(OfferBookListItem::getOffer)
                .filter(offer -> offer.getExtraDataMap() != null)
                .filter(offer -> offer.getExtraDataMap().get(OfferPayload.REFERRAL_ID) != null)
                .map(offer -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Offer ID: ").append(offer.getId()).append("\n")
                            .append("Type: ").append(offer.getDirection().name()).append("\n")
                            .append("Market: ").append(CurrencyUtil.getCurrencyPair(offer.getCurrencyCode())).append("\n")
                            .append("Price: ").append(FormattingUtils.formatPrice(offer.getPrice())).append("\n")
                            .append("Amount: ").append(DisplayUtils.formatAmount(offer, formatter)).append(" BTC\n")
                            .append("Payment method: ").append(Res.get(offer.getPaymentMethod().getId())).append("\n")
                            .append("ReferralID: ").append(offer.getExtraDataMap().get(OfferPayload.REFERRAL_ID));
                    return sb.toString();
                })
                .collect(Collectors.toList());
        return Joiner.on("\n\n").join(list);
    }
}
