package bisq.desktop.main.overlays.windows;

import bisq.desktop.Navigation;
import bisq.desktop.main.dao.governance.ProposalDisplay;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.myvote.MyVote;
import bisq.core.dao.governance.proposal.param.ChangeParamValidator;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Vote;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import javax.inject.Inject;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.add2ButtonsAfterGroup;
import static bisq.desktop.util.FormBuilder.add3ButtonsAfterGroup;
import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;

@Slf4j
public class SelectProposalWindow extends Overlay<SelectProposalWindow> {

    private final BsqFormatter bsqFormatter;
    private final DaoFacade daoFacade;
    private final ChangeParamValidator changeParamValidator;
    private final Navigation navigation;
    private final Preferences preferences;
    private Optional<Runnable> acceptHandlerOptional;
    private Optional<Runnable> rejectHandlerOptional;
    private Optional<Runnable> ignoreHandlerOptional;
    private Optional<Runnable> removeHandlerOptional;
    private Optional<Runnable> hideHandlerOptional;
    private Proposal proposal;
    private EvaluatedProposal evaluatedProposal;
    private Ballot ballot;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SelectProposalWindow(BsqFormatter bsqFormatter, DaoFacade daoFacade,
                                ChangeParamValidator changeParamValidator, Navigation navigation,
                                Preferences preferences) {
        this.bsqFormatter = bsqFormatter;
        this.daoFacade = daoFacade;
        this.changeParamValidator = changeParamValidator;
        this.navigation = navigation;
        this.preferences = preferences;
    }

    public void show(Proposal proposal, EvaluatedProposal evaluatedProposal, Ballot ballot) {

        this.proposal = proposal;
        this.evaluatedProposal = evaluatedProposal;
        this.ballot = ballot;

        rowIndex = 0;
        width = 1000;
        createGridPane();
        headLine(Res.get("dao.proposal.selectedProposal"));
        message("");
        hideCloseButton();
        super.show();
    }

    public void onAccept(Runnable acceptHandler) {
        this.acceptHandlerOptional = Optional.of(acceptHandler);
    }

    public void onReject(Runnable rejectHandler) {
        this.rejectHandlerOptional = Optional.of(rejectHandler);
    }

    public void onIgnore(Runnable ignoreHandler) {
        this.ignoreHandlerOptional = Optional.of(ignoreHandler);
    }

    public void onRemove(Runnable removeHandler) {
        this.removeHandlerOptional = Optional.of(removeHandler);
    }

    public void onHide(Runnable hideHandler) {
        this.hideHandlerOptional = Optional.of(hideHandler);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.getColumnConstraints().remove(1);
    }

    @Override
    protected void onShow() {

        display();

        setupCloseKeyHandler(stage.getScene());
    }

    @Override
    protected void addMessage() {
        super.addMessage();

        addContent(proposal, evaluatedProposal, ballot);
    }

    @Override
    protected void onHidden() {
        if (hideHandlerOptional != null) {
            hideHandlerOptional.ifPresent(Runnable::run);
            hideHandlerOptional = null;
        }
    }

    private void addContent(Proposal proposal, EvaluatedProposal evaluatedProposal, Ballot ballot) {
        ProposalDisplay proposalDisplay = new ProposalDisplay(gridPane, bsqFormatter, daoFacade, changeParamValidator,
                navigation, preferences);

        proposalDisplay.onNavigate(this::doClose);

        proposalDisplay.createAllFields("", rowIndex, -Layout.FIRST_ROW_DISTANCE, proposal.getType(),
                false, "last");
        proposalDisplay.setEditable(false);
        proposalDisplay.applyProposalPayload(proposal);
        proposalDisplay.applyEvaluatedProposal(evaluatedProposal);

        Tuple2<Long, Long> meritAndStakeTuple = daoFacade.getMeritAndStakeForProposal(proposal.getTxId());
        long merit = meritAndStakeTuple.first;
        long stake = meritAndStakeTuple.second;
        proposalDisplay.applyBallotAndVoteWeight(ballot, merit, stake);


        List<MyVote> myVoteListForCycle = daoFacade.getMyVoteListForCycle();
        boolean hasAlreadyVoted = !myVoteListForCycle.isEmpty();

        DaoPhase.Phase currentPhase = daoFacade.phaseProperty().get();

        if (currentPhase == DaoPhase.Phase.PROPOSAL) {

            Tuple2<Button, Button> proposalPhaseButtonsTuple = add2ButtonsAfterGroup(gridPane, proposalDisplay.incrementAndGetGridRow(), Res.get("shared.remove"), Res.get("shared.close"));
            Button removeProposalButton = proposalPhaseButtonsTuple.first;

            boolean doShowRemoveButton = daoFacade.isMyProposal(proposal);

            removeProposalButton.setOnAction(event -> {
                removeHandlerOptional.ifPresent(Runnable::run);
                doClose();
            });
            removeProposalButton.setVisible(doShowRemoveButton);
            removeProposalButton.setManaged(doShowRemoveButton);

            proposalPhaseButtonsTuple.second.setOnAction(event -> doClose());

        } else if (currentPhase == DaoPhase.Phase.BLIND_VOTE &&
                !hasAlreadyVoted &&
                daoFacade.isInPhaseButNotLastBlock(currentPhase)) {
            int rowIndexForVoting = proposalDisplay.incrementAndGetGridRow();
            Tuple3<Button, Button, Button> tuple = add3ButtonsAfterGroup(gridPane,
                    rowIndexForVoting,
                    Res.get("dao.proposal.myVote.accept"),
                    Res.get("dao.proposal.myVote.reject"),
                    Res.get("dao.proposal.myVote.removeMyVote"));
            Button acceptButton = tuple.first;
            acceptButton.setDefaultButton(false);
            Button rejectButton = tuple.second;
            Button ignoreButton = tuple.third;

            // show if already voted
            proposalDisplay.applyBallot(ballot);

            Optional<Vote> optionalVote = getVote(ballot);
            boolean isPresent = optionalVote.isPresent();
            boolean isAccepted = isPresent && optionalVote.get().isAccepted();
            acceptButton.setDisable((isPresent && isAccepted));
            rejectButton.setDisable((isPresent && !isAccepted));
            ignoreButton.setDisable(!isPresent);

            acceptButton.setOnAction(event -> {
                acceptHandlerOptional.ifPresent(Runnable::run);
                doClose();
            });
            rejectButton.setOnAction(event -> {
                rejectHandlerOptional.ifPresent(Runnable::run);
                doClose();
            });
            ignoreButton.setOnAction(event -> {
                ignoreHandlerOptional.ifPresent(Runnable::run);
                doClose();
            });

            Button closeButton = addButtonAfterGroup(gridPane, ++rowIndexForVoting, Res.get("shared.close"));
            closeButton.setOnAction(event -> doClose());

        } else {
            Button closeButton = addButtonAfterGroup(gridPane, proposalDisplay.incrementAndGetGridRow(), Res.get("shared.close"));
            closeButton.setOnAction(event -> doClose());
        }
    }

    private void setupCloseKeyHandler(Scene scene) {
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE || e.getCode() == KeyCode.ENTER) {
                e.consume();
                doClose();
            }
        });
    }

    private Optional<Vote> getVote(@Nullable Ballot ballot) {
        if (ballot == null)
            return Optional.empty();
        else
            return ballot.getVoteAsOptional();
    }

    public Proposal getProposal() {
        return proposal;
    }
}
