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

package bisq.desktop.main.portfolio.pendingtrades;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.SimpleMarkdownLabel;
import bisq.desktop.components.TitledGroupBg;

import bisq.core.locale.Res;
import bisq.core.trade.model.bisq_v1.Trade;

import javafx.scene.layout.GridPane;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import java.util.function.Supplier;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class TradeStepInfo {

    public enum State {
        UNDEFINED,
        SHOW_GET_HELP_BUTTON,
        IN_MEDIATION_SELF_REQUESTED,
        IN_MEDIATION_PEER_REQUESTED,
        MEDIATION_RESULT,
        MEDIATION_RESULT_SELF_ACCEPTED,
        MEDIATION_RESULT_PEER_ACCEPTED,
        IN_ARBITRATION_SELF_REQUESTED,
        IN_ARBITRATION_PEER_REQUESTED,
        IN_REFUND_REQUEST_SELF_REQUESTED,
        IN_REFUND_REQUEST_PEER_REQUESTED,
        WARN_HALF_PERIOD,
        WARN_PERIOD_OVER,
        TRADE_COMPLETED
    }

    private final TitledGroupBg titledGroupBg;
    private final SimpleMarkdownLabel label;
    private final SimpleMarkdownLabel footerLabel;
    private final AutoTooltipButton button;
    @Nullable
    @Setter
    private Trade trade;
    @Getter
    private State state = State.UNDEFINED;
    private Supplier<String> firstHalfOverWarnTextSupplier = () -> "";
    private Supplier<String> periodOverWarnTextSupplier = () -> "";

    TradeStepInfo(TitledGroupBg titledGroupBg,
                  SimpleMarkdownLabel label,
                  AutoTooltipButton button,
                  SimpleMarkdownLabel footerLabel) {
        this.titledGroupBg = titledGroupBg;
        this.label = label;
        this.button = button;
        this.footerLabel = footerLabel;
        GridPane.setColumnIndex(button, 0);

        setState(State.SHOW_GET_HELP_BUTTON);
    }

    void removeItselfFrom(GridPane leftGridPane) {
        leftGridPane.getChildren().remove(titledGroupBg);
        leftGridPane.getChildren().remove(label);
        leftGridPane.getChildren().remove(button);
    }

    public void setOnAction(EventHandler<ActionEvent> e) {
        button.setOnAction(e);
    }

    public void setFirstHalfOverWarnTextSupplier(Supplier<String> firstHalfOverWarnTextSupplier) {
        this.firstHalfOverWarnTextSupplier = firstHalfOverWarnTextSupplier;
    }

    public void setPeriodOverWarnTextSupplier(Supplier<String> periodOverWarnTextSupplier) {
        this.periodOverWarnTextSupplier = periodOverWarnTextSupplier;
    }

    public void setState(State state) {
        this.state = state;
        switch (state) {
            case UNDEFINED:
                break;
            case SHOW_GET_HELP_BUTTON:
                // grey button
                titledGroupBg.setText(Res.get("portfolio.pending.support.headline.getHelp"));
                label.updateContent("");
                button.setText(Res.get("portfolio.pending.support.button.getHelp").toUpperCase());
                button.setId(null);
                button.getStyleClass().remove("action-button");
                button.setDisable(false);
                break;
            case IN_MEDIATION_SELF_REQUESTED:
                // red button
                titledGroupBg.setText(Res.get("portfolio.pending.mediationRequested"));
                label.updateContent(Res.get("portfolio.pending.disputeOpenedMyUser", Res.get("portfolio.pending.communicateWithMediator")));
                button.setText(Res.get("portfolio.pending.mediationRequested").toUpperCase());
                button.setId("open-dispute-button");
                button.getStyleClass().remove("action-button");
                button.setDisable(true);
                break;
            case IN_MEDIATION_PEER_REQUESTED:
                // red button
                titledGroupBg.setText(Res.get("portfolio.pending.mediationRequested"));
                label.updateContent(Res.get("portfolio.pending.disputeOpenedByPeer", Res.get("portfolio.pending.communicateWithMediator")));
                button.setText(Res.get("portfolio.pending.mediationRequested").toUpperCase());
                button.setId("open-dispute-button");
                button.getStyleClass().remove("action-button");
                button.setDisable(true);
                break;
            case MEDIATION_RESULT:
                // green button
                titledGroupBg.setText(Res.get("portfolio.pending.mediationResult.headline"));
                label.updateContent(Res.get("portfolio.pending.mediationResult.info.noneAccepted"));
                button.setText(Res.get("portfolio.pending.mediationResult.button").toUpperCase());
                button.setId(null);
                button.getStyleClass().add("action-button");
                button.setDisable(false);
                break;
            case MEDIATION_RESULT_SELF_ACCEPTED:
                // green button deactivated
                titledGroupBg.setText(Res.get("portfolio.pending.mediationResult.headline"));
                label.updateContent(Res.get("portfolio.pending.mediationResult.info.selfAccepted"));
                button.setText(Res.get("portfolio.pending.mediationResult.button").toUpperCase());
                button.setId(null);
                button.getStyleClass().add("action-button");
                button.setDisable(false);
                break;
            case MEDIATION_RESULT_PEER_ACCEPTED:
                // green button
                titledGroupBg.setText(Res.get("portfolio.pending.mediationResult.headline"));
                label.updateContent(Res.get("portfolio.pending.mediationResult.info.peerAccepted"));
                button.setText(Res.get("portfolio.pending.mediationResult.button").toUpperCase());
                button.setId(null);
                button.getStyleClass().add("action-button");
                button.setDisable(false);
                break;
            case IN_REFUND_REQUEST_SELF_REQUESTED:
                // red button
                titledGroupBg.setText(Res.get("portfolio.pending.refundRequested"));
                label.updateContent(Res.get("portfolio.pending.disputeOpenedMyUser", Res.get("portfolio.pending.communicateWithArbitrator")));
                button.setText(Res.get("portfolio.pending.refundRequested").toUpperCase());
                button.setId("open-dispute-button");
                button.getStyleClass().remove("action-button");
                button.setDisable(true);
                break;
            case IN_REFUND_REQUEST_PEER_REQUESTED:
                // red button
                titledGroupBg.setText(Res.get("portfolio.pending.refundRequested"));
                label.updateContent(Res.get("portfolio.pending.disputeOpenedByPeer", Res.get("portfolio.pending.communicateWithArbitrator")));
                button.setText(Res.get("portfolio.pending.refundRequested").toUpperCase());
                button.setId("open-dispute-button");
                button.getStyleClass().remove("action-button");
                button.setDisable(true);
                break;
            case WARN_HALF_PERIOD:
                // orange button
                titledGroupBg.setText(Res.get("portfolio.pending.support.headline.halfPeriodOver"));
                label.updateContent(firstHalfOverWarnTextSupplier.get());
                button.setText(Res.get("portfolio.pending.support.button.getHelp").toUpperCase());
                button.setId(null);
                button.getStyleClass().remove("action-button");
                button.setDisable(false);
                break;
            case WARN_PERIOD_OVER:
                // red button
                titledGroupBg.setText(Res.get("portfolio.pending.support.headline.periodOver"));
                label.updateContent(periodOverWarnTextSupplier.get());
                button.setText(Res.get("portfolio.pending.openSupport").toUpperCase());
                button.setId("open-dispute-button");
                button.getStyleClass().remove("action-button");
                button.setDisable(false);
                break;
            case TRADE_COMPLETED:
                // hide group
                titledGroupBg.setVisible(false);
                label.setVisible(false);
                button.setVisible(false);
                footerLabel.setVisible(false);
        }

        if (trade != null && trade.getPayoutTx() != null) {
            button.setDisable(true);
        }
    }
}
