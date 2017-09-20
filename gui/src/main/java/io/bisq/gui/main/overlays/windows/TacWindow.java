package io.bisq.gui.main.overlays.windows;

import com.google.inject.Inject;
import io.bisq.common.locale.Res;
import io.bisq.gui.app.BisqApp;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.main.overlays.Overlay;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.layout.GridPane;
import javafx.stage.Screen;

import static io.bisq.gui.util.FormBuilder.addHyperlinkWithIcon;

public class TacWindow extends Overlay<TacWindow> {

    private final boolean smallScreen;

    @Inject
    public TacWindow() {
        type = Type.Attention;

        Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
        final double primaryScreenBoundsWidth = primaryScreenBounds.getWidth();
        smallScreen = primaryScreenBoundsWidth < 1024;
        if (smallScreen) {
            this.width = primaryScreenBoundsWidth * 0.8;
            log.warn("Very small screen: primaryScreenBounds=" + primaryScreenBounds.toString());
        } else {
            width = 900;
        }
    }

    @Override
    public void show() {
        //noinspection ConstantConditions
        headLine(Res.get("tacWindow.headline"));

        // We do not translate the tacs because of the legal nature. We would need translations checked by lawyers
        // in each language which is too expensive atm.

        String text = "1. In no event, unless for damages caused by acts of intent and gross negligence, damages resulting from personal injury, " +
                "or damages ensuing from other instances where liability is required by applicable law or agreed to in writing, will any " +
                "developer, copyright holder and/or any other party who modifies and/or conveys the software as permitted above or " +
                "facilitates its operation, be liable for damages, including any general, special, incidental or consequential damages " +
                "arising out of the use or inability to use the software (including but not limited to loss of data or data being " +
                "rendered inaccurate or losses sustained by you or third parties or a failure of the software to operate with any " +
                "other software), even if such developer, copyright holder and/or other party has been advised of the possibility of such damages.\n\n" +

                "2. The user is responsible to use the software in compliance with local laws. Don't use the software if the usage is not legal in your jurisdiction.\n\n" +

                "3. The " + Res.getBaseCurrencyName() + " market price is delivered by 3rd parties (BitcoinAverage, Poloniex, Coinmarketcap). " +
                "It is your responsibility to verify the price with other sources for correctness.\n\n" +

                "4. Any Fiat payment method carries a potential risk for bank chargeback. By accepting the \"User Agreement\" the users confirms " +
                "to be aware of those risks and in no case will claim legal responsibility to the authors or copyright holders of the software.\n\n" +

                "5. Any dispute, controversy or claim arising out of or relating to the use of the software shall be settled by arbitration in " +
                "accordance with the Bisq arbitration rules as at present in force. The arbitration is conducted online. " +
                "The language to be used in the arbitration proceedings shall be English if not otherwise stated.\n\n" +

                "6. The user confirms that he has read and agreed to the rules regarding the dispute process:\n" +
                "    - You must complete trades within the maximum duration specified for each payment method.\n" +
                "    - You must enter the trade ID in the \"reason for payment\" text field when doing the fiat payment transfer.\n" +
                "    - If the bank of the fiat sender charges fees the sender (" + Res.getBaseCurrencyCode() + " buyer) has to cover the fees.\n" +
                "    - You must cooperate with the arbitrator during the arbitration process.\n" +
                "    - You must reply within 48 hours to each arbitrator inquiry.\n" +
                "    - Failure to follow the above requirements may result in loss of your security deposit.\n\n" +
                "For more details and a general overview please read the full documentation about the " +
                "arbitration system and the dispute process.";
        message(text);
        actionButtonText(Res.get("tacWindow.agree"));
        closeButtonText(Res.get("tacWindow.disagree"));
        onClose(BisqApp.shutDownHandler::run);

        super.show();
    }

    @Override
    protected void addMessage() {
        super.addMessage();
        String fontSize = smallScreen ? "9" : "12";
        messageLabel.setStyle("-fx-font-size: " + fontSize + ";");
        HyperlinkWithIcon hyperlinkWithIcon = addHyperlinkWithIcon(gridPane, ++rowIndex, Res.get("tacWindow.arbitrationSystem"),
                "https://bisq.network/arbitration_system.pdf");
        hyperlinkWithIcon.setStyle("-fx-font-size: " + fontSize + ";");
        GridPane.setMargin(hyperlinkWithIcon, new Insets(-6, 0, -20, -4));
    }

    @Override
    protected void setTruncatedMessage() {
        truncatedMessage = message;
    }

    @Override
    protected void onShow() {
        display();
    }
}
