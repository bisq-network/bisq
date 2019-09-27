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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.app.BisqApp;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.overlays.Overlay;

import bisq.core.locale.Res;

import com.google.inject.Inject;

import javafx.stage.Screen;

import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addHyperlinkWithIcon;

@Slf4j
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
            width = 1100;
        }
    }

    @Override
    public void show() {
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

                "4. Any Fiat payment method carries a potential risk for bank chargeback. By accepting the \"User Agreement\" the user confirms " +
                "to be aware of those risks and in no case will claim legal responsibility to the authors or copyright holders of the software.\n\n" +

                "5. Any dispute, controversy or claim arising out of or relating to the use of the software shall be settled by arbitration in " +
                "accordance with the Bisq arbitration rules as at present in force. The arbitration is conducted online. " +
                "The language to be used in the arbitration proceedings shall be English if not otherwise stated.\n\n" +

                "6. The user confirms that they have read and agreed to the rules regarding the dispute process:\n" +
                "    - You must complete trades within the maximum duration specified for each payment method.\n" +
                "    - You must enter the trade ID in the \"reason for payment\" text field when doing the fiat payment transfer.\n" +
                "    - If the bank of the fiat sender charges fees, the sender (" + Res.getBaseCurrencyCode() + " buyer) has to cover the fees.\n" +
                "    - You must cooperate with the mediator during the mediation process.\n" +
                "    - You must reply within 48 hours to each mediator inquiry.\n" +
                "    - If mediation does not lead to a payout by consensus of both traders the traders can open arbitration after 2 weeks.\n" +
                "    - Opening a refund request from arbitrators will trigger publishing the delayed payout transaction where the funds from the deposit transaction are sent to the Bisq DAO receiver address as collateral. The arbitrator will refund the traders according to his judgement.\n" +
                "    - Opening a refund request from arbitrators should be used only if the trade peer is not reacting or the trader considers the mediators suggested payout as unfair. " +
                "If the arbitrator comes to the same conclusion as the mediator he will take a part of the payout from the trader who opened the dispute for covering his efforts.\n" +
                "    - The arbitrator will make a reimbursement request to the Bisq DAO to get refunded for the funds he paid out to traders in refund requests.\n" +
                "    - Failure to follow the above requirements may result in loss of your security deposit.\n\n" +
                "For more details and a general overview please read the full documentation about the " +
                "arbitration system and the dispute process.";
        message(text);
        actionButtonText(Res.get("tacWindow.agree"));
        closeButtonText(Res.get("tacWindow.disagree"));
        onClose(BisqApp.getShutDownHandler());

        super.show();
    }

    @Override
    protected void addMessage() {
        super.addMessage();
        String fontStyleClass = smallScreen ? "small-text" : "normal-text";
        messageLabel.getStyleClass().add(fontStyleClass);
        HyperlinkWithIcon hyperlinkWithIcon = addHyperlinkWithIcon(gridPane, ++rowIndex, Res.get("tacWindow.arbitrationSystem"),
                "https://bisq.network/docs/exchange/arbitration-system");
        hyperlinkWithIcon.getStyleClass().add(fontStyleClass);
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
