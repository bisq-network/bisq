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
import bisq.core.util.coin.BsqFormatter;

import de.jensd.fx.fontawesome.AwesomeIcon;

import com.jfoenix.controls.JFXButton;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

import javafx.beans.value.ChangeListener;

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

    enum IconButtonType {
        REMOVE_PROPOSAL(Res.get("dao.proposal.table.icon.tooltip.removeProposal")),
        ACCEPT(Res.get("dao.proposal.display.myVote.accepted")),
        REJECT(Res.get("dao.proposal.display.myVote.rejected")),
        IGNORE(Res.get("dao.proposal.display.myVote.ignored"));
        @Getter
        private String title;

        IconButtonType(String title) {
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
    private JFXButton iconButton;

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
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup() {
        daoFacade.phaseProperty().removeListener(phaseChangeListener);
    }

    public void onPhaseChanged(DaoPhase.Phase phase) {
        //noinspection IfCanBeSwitch
        Label icon;
        if (phase == DaoPhase.Phase.PROPOSAL) {
            icon = FormBuilder.getIcon(AwesomeIcon.TRASH);

            icon.getStyleClass().addAll("icon", "dao-remove-proposal-icon");
            iconButton = new JFXButton("", icon);
            boolean isMyProposal = daoFacade.isMyProposal(proposal);
            if (isMyProposal) {
                iconButton.setUserData(IconButtonType.REMOVE_PROPOSAL);
            }
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
                    iconButton = new JFXButton("", icon);
                    iconButton.setUserData(IconButtonType.ACCEPT);
                } else {
                    icon = FormBuilder.getIcon(AwesomeIcon.THUMBS_DOWN);
                    icon.getStyleClass().addAll("icon", "dao-rejected-icon");
                    iconButton = new JFXButton("", icon);
                    iconButton.setUserData(IconButtonType.REJECT);
                }
            } else {
                icon = FormBuilder.getIcon(AwesomeIcon.MINUS);
                icon.getStyleClass().addAll("icon", "dao-ignored-icon");
                iconButton = new JFXButton("", icon);
                iconButton.setUserData(IconButtonType.IGNORE);
            }
            iconButton.setTooltip(new Tooltip(Res.get("dao.proposal.table.icon.tooltip.changeVote",
                    ((IconButtonType) iconButton.getUserData()).getTitle(),
                    getNext(((IconButtonType) iconButton.getUserData()))
            )));
            iconButton.getStyleClass().add("hidden-icon-button");
            iconButton.layout();
        }
    }

    public String getProposalTypeAsString() {
        return Res.get("dao.proposal.type." + proposal.getType().name());
    }

    private String getNext(IconButtonType iconButtonType) {
        switch (iconButtonType) {
            case ACCEPT:
                return IconButtonType.REJECT.getTitle();
            case REJECT:
                return IconButtonType.IGNORE.getTitle();
            default:
                return IconButtonType.ACCEPT.getTitle();
        }
    }
}
