package io.bisq.gui.main.overlays.windows;

import com.google.inject.Inject;
import io.bisq.common.locale.Res;
import io.bisq.gui.app.BisqApp;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.main.overlays.Overlay;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;

import static io.bisq.gui.util.FormBuilder.addHyperlinkWithIcon;

public class TacWindow extends Overlay<TacWindow> {

    @Inject
    public TacWindow() {
        type = Type.Attention;
        width = 900;
    }

    @Override
    public void show() {
        //noinspection ConstantConditions
        headLine(Res.get("tacWindow.headline"));

        // We do not translate the tacs because of the legal nature. We would need translations checked by lawyers
        // in each language which is too expensive atm.
        String text = "1. This software is experimental and provided \"as is\", without warranty of any kind, " +
                "express or implied, including but not limited to the warranties of " +
                "merchantability, fitness for a particular purpose and non-infringement.\n" +
                "In no event shall the authors or copyright holders be liable for any claim, damages or other " +
                "liability, whether in an action of contract, tort or otherwise, " +
                "arising from, out of or in connection with the software or the use or other dealings in the software.\n\n" +

                "2. The user takes full responsibility for any potential losses experienced in relation to the use of Bisq. " +
                "The user has to take care to remember and secure his wallet password, make regular backups and take care of his operational security.\n" +
                "In no case he will claim legal responsibility to the authors or copyright holders of the software.\n\n" +

                "3. The user is responsible to use the software in compliance with local laws. Don't use the software if the usage is not legal in your jurisdiction.\n\n" +

                "4. The " + Res.getBaseCurrencyName() + " market price is delivered by 3rd parties (BitcoinAverage, Poloniex, Coinmarketcap). " +
                "It is your responsibility to verify the price with other sources for correctness.\n\n" +

                "5. Any Fiat payment method carries a potential risk for bank chargeback. By accepting the \"User Agreement\"  the users confirms " +
                "to be aware of those risks and in no case will claim legal responsibility to the authors or copyright holders of the software.\n\n" +

                "6. Any dispute, controversy or claim arising out of or relating to the use of the software shall be settled by arbitration in " +
                "accordance with the Bisq arbitration rules as at present in force. The arbitration is conducted online.\n" +
                "The language to be used in the arbitration proceedings shall be English if not otherwise stated.\n\n" +
              
                "7. The user confirms that he has read and agreed to the rules regarding the dispute process:\n" +
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
        messageLabel.setStyle("-fx-font-size: 12;");
        HyperlinkWithIcon hyperlinkWithIcon = addHyperlinkWithIcon(gridPane, ++rowIndex, Res.get("tacWindow.arbitrationSystem"),
                "https://bisq.io/arbitration_system.pdf");
        hyperlinkWithIcon.setStyle("-fx-font-size: 12;");
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
