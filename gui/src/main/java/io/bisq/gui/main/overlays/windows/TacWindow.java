package io.bisq.gui.main.overlays.windows;

import com.google.inject.Inject;
import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.Res;
import io.bisq.core.user.Preferences;
import io.bisq.gui.app.BisqApp;
import io.bisq.gui.main.overlays.Overlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bisq.gui.util.FormBuilder.addHyperlinkWithIcon;

public class TacWindow extends Overlay<TacWindow> {
    private static final Logger log = LoggerFactory.getLogger(TacWindow.class);
    private final Preferences preferences;

    @Inject
    public TacWindow(Preferences preferences) {
        super(preferences);
        this.preferences = preferences;
        type = Type.Attention;
        width = 900;
    }

    public void showIfNeeded() {
        if (!preferences.isTacAccepted() && !DevEnv.DEV_MODE) {
            headLine(Res.get("tacWindow.headline"));

            // We do not translate the tacs because of the legal nature. We would need translations checked by lawyers
            // in each language which is too expensive atm.
            String text = "1. This software is experimental and provided \"as is\", without warranty of any kind, " +
                    "express or implied, including but not limited to the warranties of " +
                    "merchantability, fitness for a particular purpose and non-infringement.\n" +
                    "In no event shall the authors or copyright holders be liable for any claim, damages or other " +
                    "liability, whether in an action of contract, tort or otherwise, " +
                    "arising from, out of or in connection with the software or the use or other dealings in the software.\n\n" +
                    "2. The user is responsible to use the software in compliance with local laws. Don't use bisq if the usage of Bitcoin is not legal in your jurisdiction.\n\n" +
                    "3. Bitcoin market price is delivered by 3rd parties (BitcoinAverage, Poloniex, Coinmarketcap). It is your responsibility to double check the price with other sources.\n\n" +
                    "4. The user confirms that he has read and agreed to the rules regarding the dispute process:\n" +
                    "    - You must finalize trades within the maximum duration specified for each payment method.\n" +
                    "    - You must enter the trade ID in the \"reason for payment\" text field when doing the fiat payment transfer.\n" +
                    "    - If the bank of the fiat sender charges fees the sender (BTC buyer) has to cover the fees.\n" +
                    "    - You must cooperate with the arbitrator during the arbitration process.\n" +
                    "    - You must reply within 48 hours to each arbitrator inquiry.\n" +
                    "    - Failure to follow the above requirements may result in loss of your security deposit.\n\n" +
                    "For more details and a general overview please read the full documentation about the " +
                    "arbitration system and the dispute process.";
            message(text);
            actionButtonText(Res.get("tacWindow.agree"));
            closeButtonText(Res.get("tacWindow.disagree"));
            onAction(() -> preferences.setTacAccepted(true));
            onClose(BisqApp.shutDownHandler::run);

            super.show();
        }
    }

    @Override
    protected void addMessage() {
        super.addMessage();
        addHyperlinkWithIcon(gridPane, ++rowIndex, Res.get("tacWindow.arbitrationSystem"),
                "https://bisq.io/arbitration_system.pdf", -6, preferences);
    }

    @Override
    protected void onShow() {
        display();
    }
}
