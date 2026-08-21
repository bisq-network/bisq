package bisq.desktop.components.paymentmethods;

import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.user.DontShowAgainLookup;

import bisq.common.app.DevEnv;

public class ArsBlueRatePopup {
    public static boolean isTradeCurrencyArgentinePesos(TradeCurrency tradeCurrency) {
        FiatCurrency arsCurrency = new FiatCurrency("ARS");
        return tradeCurrency.equals(arsCurrency);
    }

    public static void show() {
        if (DevEnv.isIgnorePopupsInDevMode()) {
            return;
        }
        createPopup().show();
    }

    public static void showMaybe() {
        if (DevEnv.isIgnorePopupsInDevMode()) {
            return;
        }
        String key = "arsBlueMarketNotificationPopup";
        if (DontShowAgainLookup.showAgain(key)) {
            createPopup().dontShowAgainId(key)
                    .show();
        }
    }

    private static Popup createPopup() {
        return new Popup()
                .headLine(Res.get("popup.arsBlueMarket.title"))
                .information(Res.get("popup.arsBlueMarket.info"))
                .actionButtonText(Res.get("shared.iUnderstand"))
                .hideCloseButton();
    }
}
