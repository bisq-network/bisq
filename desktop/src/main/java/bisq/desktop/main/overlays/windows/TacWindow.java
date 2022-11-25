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
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.ExternalHyperlink;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.Res;

import com.google.inject.Inject;

import javafx.stage.Screen;

import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;

import lombok.extern.slf4j.Slf4j;

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
            log.warn("Very small screen: primaryScreenBounds=" + primaryScreenBounds);
        } else {
            width = 1250;
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

                "2. The user is responsible for using the software in compliance with local laws. Don't use the software if using it is not legal in your jurisdiction.\n\n" +

                "3. Any market prices, network fee estimates, or other data obtained from servers operated by the Bisq DAO is provided on an 'as is, as available' basis without representation or warranty of any kind. It is your responsibility to verify any data provided in regards to inaccuracies or omissions.\n\n" +

                "4. Any Fiat payment method carries a potential risk for bank chargeback. By accepting the \"User Agreement\" the user confirms " +
                "to be aware of those risks and in no case will claim legal responsibility to the authors or copyright holders of the software.\n\n" +

                "5. Any dispute, controversy or claim arising out of or relating to the use of the software shall be settled by arbitration in " +
                "accordance with the Bisq arbitration rules as at present in force. The arbitration is conducted online. " +
                "The language to be used in the arbitration proceedings shall be English if not otherwise stated.\n\n" +

                "6. The user confirms that they have read and agreed to the rules regarding the dispute process:\n" +
                "    - You must complete trades within the trading window.\n" +
                "    - By default, leave the \"reason for payment\" field empty. NEVER put the trade ID or any other text like 'bitcoin', 'BTC', or 'Bisq'.\n" +
                "    - If the bank of the fiat sender charges fees, the sender (BTC buyer) has to cover the fees.\n" +
                "    - In case of mediation, you must cooperate with the mediator and respond to each message within 48 hours.\n" +
                "    - If either (or both) traders do not accept the mediator's suggested payout, traders can open arbitration after 10 days in case of altcoin trades and after 20 days for fiat trades.\n" +
                "    - You should only open arbitration if you think the mediator's suggested payout is unfair, or if your trading peer is unresponsive.\n" +
                "    - By opening arbitration the trader publishes the delayed payout transaction, sending all funds from the deposit transaction to the distributed Bisq Burningmen who have burned BSQ upfront\n" +
                "      which authorizes them for that role.\n" +
                "    - The arbitrator will re-investigate the case and decide how the trader(s) should be refunded.\n" +
                "    - The refund agent will take the payout suggestion from the arbitrator and refund the trader(s) from their own pocket to avoid the extra effort for the traders to do the reimbursement request at\n" +
                "      the Bisq DAO. The refund agent will make the reimbursement request on behalf of those traders who got refunded directly by them. It is up to the discretion of the refund agent to which\n" +
                "      amounts they provide this service.\n" +
                "    - In case a trader is not satisfied with the arbitration result or if the refund agent could not refund directly the trader, they can make a reimbursement request to the Bisq DAO by themself.\n";
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

        Label label = new AutoTooltipLabel("    - For more details and a general overview please read the full documentation about ");
        label.getStyleClass().add(fontStyleClass);

        HyperlinkWithIcon hyperlinkWithIcon = new ExternalHyperlink(Res.get("tacWindow.arbitrationSystem").toLowerCase() + ".");
        hyperlinkWithIcon.setOnAction(e -> GUIUtil.openWebPage("https://bisq.wiki/Dispute_resolution"));
        hyperlinkWithIcon.getStyleClass().add(fontStyleClass);
        HBox.setMargin(hyperlinkWithIcon, new Insets(-0.5, 0, 0, 0));

        HBox hBox = new HBox(label, hyperlinkWithIcon);
        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setMargin(hBox, new Insets(-5, 0, -20, 0));
        gridPane.getChildren().add(hBox);
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
