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

package bisq.desktop.main.dao.cycles.cycle;

import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.FormBuilder;

import bisq.core.dao.voting.voteresult.EvaluatedProposal;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import javafx.scene.control.Button;
import javafx.scene.control.TextArea;

import javafx.geometry.Insets;

import javafx.beans.value.ChangeListener;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

public class CycleDetailsWindow extends Overlay<CycleDetailsWindow> {

    private final BsqFormatter bsqFormatter;
    private ChangeListener<Number> changeListener;
    private TextArea textArea;
    private EvaluatedProposal evaluatedProposal;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public CycleDetailsWindow(BsqFormatter bsqFormatter, EvaluatedProposal evaluatedProposal) {
        this.bsqFormatter = bsqFormatter;
        this.evaluatedProposal = evaluatedProposal;
        type = Type.Confirmation;
    }

    @Override
    public void show() {
        rowIndex = -1;
        width = 850;
        createGridPane();
        addContent();
        display();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(35, 40, 30, 40));
        gridPane.getStyleClass().add("grid-pane");
    }

    private void addContent() {
        addTitledGroupBg(gridPane, ++rowIndex, 5, Res.get("dao.results.result.detail.header"));

        //TODO impl
        //addLabelTextField(gridPane, rowIndex, Res.get("dao.results.result.detail.header"), evaluatedProposal.getProposal().getName(), Layout.FIRST_ROW_DISTANCE);


        Button closeButton = FormBuilder.addButtonAfterGroup(gridPane, ++rowIndex, Res.get("shared.close"));
        closeButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(Runnable::run);
            hide();
        });
    }
}
