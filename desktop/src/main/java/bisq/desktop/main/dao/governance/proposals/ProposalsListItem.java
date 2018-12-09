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

package bisq.desktop.main.dao.governance.proposals;

import bisq.desktop.util.FormBuilder;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Vote;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.animation.FillTransition;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import javafx.beans.value.ChangeListener;

import javafx.util.Duration;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@ToString
@Slf4j
@EqualsAndHashCode
//TODO merge with vote result ProposalListItem
public class ProposalsListItem {

    enum IconButtonTypes {
        REMOVE_PROPOSAL(Res.get("dao.proposal.table.icon.tooltip.removeProposal")),
        ACCEPT(Res.get("dao.proposal.display.myVote.accepted")),
        REJECT(Res.get("dao.proposal.display.myVote.rejected")),
        IGNORE(Res.get("dao.proposal.display.myVote.ignored"));
        @Getter
        private String title;

        IconButtonTypes(String title) {
            this.title = title;
        }
    }

    @Getter
    private final Proposal proposal;
    private final DaoFacade daoFacade;
    private final BsqFormatter bsqFormatter;

    @Getter
    @Nullable
    private Ballot ballot;

    @Getter
    private Button iconButton;

    // A Shape used to perform fill transition, never to be shown. The Cell listens to the Shape's fill property.
    @Getter
    private Rectangle colorTransition;

    private ChangeListener<DaoPhase.Phase> phaseChangeListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    ProposalsListItem(Proposal proposal,
                      DaoFacade daoFacade,
                      BsqFormatter bsqFormatter) {
        this.proposal = proposal;
        this.daoFacade = daoFacade;
        this.bsqFormatter = bsqFormatter;

        init();
    }

    ProposalsListItem(Ballot ballot,
                      DaoFacade daoFacade,
                      BsqFormatter bsqFormatter) {
        this.ballot = ballot;
        this.proposal = ballot.getProposal();
        this.daoFacade = daoFacade;
        this.bsqFormatter = bsqFormatter;

        init();
    }

    private void init() {
        phaseChangeListener = (observable, oldValue, newValue) -> onPhaseChanged(newValue);

        daoFacade.phaseProperty().addListener(phaseChangeListener);

        onPhaseChanged(daoFacade.phaseProperty().get());

        colorTransition = new Rectangle(1, 1, new Color(0, 0, 0, 0));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setVote(@Nullable Vote vote) {
        if (ballot != null && ballot.getVote() != vote)
            onVoteChanged(vote);
        daoFacade.setVote(ballot, vote);
    }

    public void cleanup() {
        daoFacade.phaseProperty().removeListener(phaseChangeListener);
    }

    public void onPhaseChanged(DaoPhase.Phase phase) {
        //noinspection IfCanBeSwitch
        Label icon;
        if (phase == DaoPhase.Phase.PROPOSAL) {
            icon = FormBuilder.getIcon(AwesomeIcon.TRASH);

            icon.getStyleClass().addAll("icon", "dao-remove-proposal-icon");
            iconButton = new Button("", icon);
            boolean isMyProposal = daoFacade.isMyProposal(proposal);
            if (isMyProposal)
                iconButton.setUserData(IconButtonTypes.REMOVE_PROPOSAL);
            iconButton.setVisible(isMyProposal);
            iconButton.setManaged(isMyProposal);
            iconButton.getStyleClass().add("hidden-icon-button");
            iconButton.setTooltip(new Tooltip(Res.get("dao.proposal.table.icon.tooltip.removeProposal")));
        } else if (iconButton != null) {
            iconButton.setVisible(true);
            iconButton.setManaged(true);
        }

        // ballot
        if (ballot != null) {
            Vote vote = ballot.getVote();

            if (vote != null) {
                if ((vote).isAccepted()) {
                    icon = FormBuilder.getIcon(AwesomeIcon.THUMBS_UP);
                    icon.getStyleClass().addAll("icon", "dao-accepted-icon");
                    iconButton = new Button("", icon);
                    iconButton.setUserData(IconButtonTypes.ACCEPT);
                } else {
                    icon = FormBuilder.getIcon(AwesomeIcon.THUMBS_DOWN);
                    icon.getStyleClass().addAll("icon", "dao-rejected-icon");
                    iconButton = new Button("", icon);
                    iconButton.setUserData(IconButtonTypes.REJECT);
                }
            } else {
                icon = FormBuilder.getIcon(AwesomeIcon.MINUS);
                icon.getStyleClass().addAll("icon", "dao-ignored-icon");
                iconButton = new Button("", icon);
                iconButton.setUserData(IconButtonTypes.IGNORE);
            }
            iconButton.setTooltip(new Tooltip(Res.get("dao.proposal.table.icon.tooltip.changeVote",
                    ((IconButtonTypes) iconButton.getUserData()).getTitle(),
                    getNext(((IconButtonTypes) iconButton.getUserData()))
            )));
            iconButton.getStyleClass().add("hidden-icon-button");
            iconButton.layout();
        }
    }

    private void onVoteChanged(Vote to) {
        // TODO: Get colors from css directly, for styles:
        // dao-accepted-icon, dao-rejected-icon, dao-ignored-icon
        Color fromColor = Color.web("25B135", 0.5);
        Color toColor = Color.web("25B135", 0);
        if (to == null) {
            fromColor = Color.web("AAAAAA", 0.5);
            toColor = Color.web("AAAAAA", 0);
        } else if (!to.isAccepted()) {
            fromColor = Color.web("dd0000", 0.5);
            toColor = Color.web("dd0000", 0);
        }

        FillTransition ft = new FillTransition(Duration.millis(10000), colorTransition, fromColor, toColor);
        ft.setCycleCount(1);
        ft.setAutoReverse(false);
        ft.play();
    }

    private String getNext(IconButtonTypes iconButtonTypes) {
        if (iconButtonTypes == IconButtonTypes.ACCEPT)
            return IconButtonTypes.REJECT.getTitle();
        else if (iconButtonTypes == IconButtonTypes.REJECT)
            return IconButtonTypes.IGNORE.getTitle();
        else
            return IconButtonTypes.ACCEPT.getTitle();
    }
}
