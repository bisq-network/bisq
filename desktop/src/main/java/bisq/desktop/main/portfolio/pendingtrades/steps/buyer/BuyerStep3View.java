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

package bisq.desktop.main.portfolio.pendingtrades.steps.buyer;

import bisq.desktop.components.TextFieldWithIcon;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;
import bisq.desktop.main.portfolio.pendingtrades.steps.TradeStepView;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;

import bisq.core.locale.Res;
import bisq.core.network.MessageState;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Label;
import javafx.scene.paint.Paint;

import javafx.beans.value.ChangeListener;

public class BuyerStep3View extends TradeStepView {
    private final ChangeListener<MessageState> messageStateChangeListener;
    private TextFieldWithIcon textFieldWithIcon;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerStep3View(PendingTradesViewModel model) {
        super(model);

        messageStateChangeListener = (observable, oldValue, newValue) -> {
            updateMessageStateInfo();
        };
    }

    @Override
    public void activate() {
        super.activate();

        model.getMessageStateProperty().addListener(messageStateChangeListener);

        updateMessageStateInfo();
    }

    public void deactivate() {
        super.deactivate();

        model.getMessageStateProperty().removeListener(messageStateChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Info
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void addInfoBlock() {
        FormBuilder.addTitledGroupBg(gridPane, ++gridRow, 2, getInfoBlockTitle(), Layout.GROUP_DISTANCE);
        infoLabel = FormBuilder.addMultilineLabel(gridPane, gridRow, "", Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        textFieldWithIcon = FormBuilder.addLabelTextFieldWithIcon(gridPane, ++gridRow,
                Res.get("portfolio.pending.step3_buyer.wait.msgStateInfo.label"), 0).second;
    }

    @Override
    protected String getInfoBlockTitle() {
        return Res.get("portfolio.pending.step3_buyer.wait.headline");
    }

    @Override
    protected String getInfoText() {
        return Res.get("portfolio.pending.step3_buyer.wait.info", model.dataModel.getCurrencyCode());
    }

    private void updateMessageStateInfo() {
        MessageState messageState = model.getMessageStateProperty().get();
        textFieldWithIcon.setText(Res.get("message.state." + messageState.name()));
        Label iconLabel = textFieldWithIcon.getIconLabel();
        switch (messageState) {
            case UNDEFINED:
                textFieldWithIcon.setIcon(AwesomeIcon.QUESTION);
                iconLabel.setTextFill(Paint.valueOf("#e9a20a"));
                break;
            case SENT:
                textFieldWithIcon.setIcon(AwesomeIcon.ARROW_RIGHT);
                iconLabel.setTextFill(Paint.valueOf("#afe193"));
                break;
            case ARRIVED:
                textFieldWithIcon.setIcon(AwesomeIcon.OK);
                iconLabel.setTextFill(Paint.valueOf("#0793ad"));
                break;
            case STORED_IN_MAILBOX:
                textFieldWithIcon.setIcon(AwesomeIcon.ENVELOPE_ALT);
                iconLabel.setTextFill(Paint.valueOf("#91B6E9"));
                break;
            case ACKNOWLEDGED:
                textFieldWithIcon.setIcon(AwesomeIcon.OK_SIGN);
                iconLabel.setTextFill(Paint.valueOf("#009900"));
                break;
            case FAILED:
                textFieldWithIcon.setIcon(AwesomeIcon.EXCLAMATION_SIGN);
                iconLabel.setTextFill(Paint.valueOf("#dd0000"));
                break;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getWarningText() {
        setInformationHeadline();
        String substitute = model.isBlockChainMethod() ?
                Res.get("portfolio.pending.step3_buyer.warn.part1a", model.dataModel.getCurrencyCode()) :
                Res.get("portfolio.pending.step3_buyer.warn.part1b");
        return Res.get("portfolio.pending.step3_buyer.warn.part2", substitute, model.getDateForOpenDispute());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getOpenForDisputeText() {
        return Res.get("portfolio.pending.step3_buyer.openForDispute");
    }

    @Override
    protected void applyOnDisputeOpened() {
    }
}


