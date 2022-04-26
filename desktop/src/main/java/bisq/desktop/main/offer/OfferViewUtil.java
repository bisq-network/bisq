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

package bisq.desktop.main.offer;

import bisq.desktop.Navigation;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.MainView;
import bisq.desktop.main.offer.offerbook.BsqOfferBookView;
import bisq.desktop.main.offer.offerbook.BtcOfferBookView;
import bisq.desktop.main.offer.offerbook.OfferBookView;
import bisq.desktop.main.offer.offerbook.OtherOfferBookView;
import bisq.desktop.main.offer.offerbook.TopAltcoinOfferBookView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;

import bisq.common.UserThread;
import bisq.common.util.Tuple2;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;

// Shared utils for Views
public class OfferViewUtil {

    public static Label createPopOverLabel(String text) {
        final Label label = new Label(text);
        label.setPrefWidth(300);
        label.setWrapText(true);
        label.setLineSpacing(1);
        label.setPadding(new Insets(10));
        return label;
    }

    public static void showPaymentAccountWarning(String msgKey,
                                                 HashMap<String, Boolean> paymentAccountWarningDisplayed) {
        if (msgKey == null || paymentAccountWarningDisplayed.getOrDefault(msgKey, false)) {
            return;
        }
        paymentAccountWarningDisplayed.put(msgKey, true);
        UserThread.runAfter(() -> {
            new Popup().information(Res.get(msgKey))
                    .width(900)
                    .closeButtonText(Res.get("shared.iConfirm"))
                    .dontShowAgainId(msgKey)
                    .show();
        }, 500, TimeUnit.MILLISECONDS);
    }

    public static void addPayInfoEntry(GridPane infoGridPane, int row, String labelText, String value) {
        Label label = new AutoTooltipLabel(labelText);
        TextField textField = new TextField(value);
        textField.setMinWidth(500);
        textField.setEditable(false);
        textField.setFocusTraversable(false);
        textField.setId("payment-info");
        GridPane.setConstraints(label, 0, row, 1, 1, HPos.RIGHT, VPos.CENTER);
        GridPane.setConstraints(textField, 1, row);
        infoGridPane.getChildren().addAll(label, textField);
    }

    public static Tuple2<AutoTooltipButton, HBox> createBuyBsqButtonBox(Navigation navigation) {
        String buyBsqText = Res.get("shared.buyCurrency", "BSQ");
        var buyBsqButton = new AutoTooltipButton(buyBsqText);
        buyBsqButton.getStyleClass().add("action-button");
        buyBsqButton.getStyleClass().add("tiny-button");
        buyBsqButton.setMinWidth(60);
        buyBsqButton.setOnAction(e -> openBuyBsqOfferBook(navigation)
        );

        var info = new AutoTooltipLabel("BSQ is colored BTC that helps fund Bisq developers.");
        var learnMore = new HyperlinkWithIcon("Learn More");
        learnMore.setOnAction(e -> new Popup().headLine(buyBsqText)
                .information(Res.get("createOffer.buyBsq.popupMessage"))
                .actionButtonText(buyBsqText)
                .buttonAlignment(HPos.CENTER)
                .onAction(() -> openBuyBsqOfferBook(navigation)).show());
        learnMore.setMinWidth(100);

        HBox buyBsqBox = new HBox(buyBsqButton, info, learnMore);
        buyBsqBox.setAlignment(Pos.BOTTOM_LEFT);
        buyBsqBox.setSpacing(10);
        buyBsqBox.setPadding(new Insets(0, 0, 4, -20));

        return new Tuple2<>(buyBsqButton, buyBsqBox);
    }

    private static void openBuyBsqOfferBook(Navigation navigation) {
        navigation.navigateTo(
                MainView.class, BuyOfferView.class, BsqOfferBookView.class);
    }

    public static Class<? extends OfferBookView<?, ?>> getOfferBookViewClass(String currencyCode) {
        Class<? extends OfferBookView<?, ?>> offerBookViewClazz;
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            offerBookViewClazz = BtcOfferBookView.class;
        } else if (currencyCode.equals(GUIUtil.BSQ.getCode())) {
            offerBookViewClazz = BsqOfferBookView.class;
        } else if (currencyCode.equals(GUIUtil.TOP_ALTCOIN.getCode())) {
            offerBookViewClazz = TopAltcoinOfferBookView.class;
        } else {
            offerBookViewClazz = OtherOfferBookView.class;
        }
        return offerBookViewClazz;
    }

    public static boolean isShownAsSellOffer(Offer offer) {
        return isShownAsSellOffer(offer.getCurrencyCode(), offer.getDirection());
    }

    public static boolean isShownAsSellOffer(TradeCurrency tradeCurrency, OfferDirection direction) {
        return isShownAsSellOffer(tradeCurrency.getCode(), direction);
    }

    public static boolean isShownAsSellOffer(String currencyCode, OfferDirection direction) {
        return CurrencyUtil.isFiatCurrency(currencyCode) == (direction == OfferDirection.SELL);
    }

    public static boolean isShownAsBuyOffer(Offer offer) {
        return !isShownAsSellOffer(offer);
    }

    public static boolean isShownAsBuyOffer(OfferDirection direction, TradeCurrency tradeCurrency) {
        return !isShownAsSellOffer(tradeCurrency.getCode(), direction);
    }

    public static TradeCurrency getAnyOfMainCryptoCurrencies() {
        return getMainCryptoCurrencies().findAny().get();
    }

    @NotNull
    public static Stream<CryptoCurrency> getMainCryptoCurrencies() {
        return CurrencyUtil.getMainCryptoCurrencies().stream().filter(cryptoCurrency ->
                !Objects.equals(cryptoCurrency.getCode(), GUIUtil.TOP_ALTCOIN.getCode()) &&
                        !Objects.equals(cryptoCurrency.getCode(), GUIUtil.BSQ.getCode()));
    }
}
