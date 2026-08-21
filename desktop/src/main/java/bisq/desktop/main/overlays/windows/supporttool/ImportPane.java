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

package bisq.desktop.main.overlays.windows.supporttool;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.BisqTextArea;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.mediation.MediationManager;

import bisq.common.UserThread;
import bisq.common.util.Tuple2;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static bisq.desktop.util.FormBuilder.addCheckBox;
import static bisq.desktop.util.FormBuilder.addTopLabelComboBox;

public class ImportPane extends CommonPane {

    private final MediationManager mediationManager;
    private final CheckBox recentTickets;
    private final ComboBox<String> mediationDropDown;
    private final TextArea importHex;
    ObservableList<Dispute> disputeObservableList;

    ImportPane(MediationManager mediationManager, InputsPane inputsPane, SupportToolWindow.MasterCallback parent) {
        this.mediationManager = mediationManager;
        int rowIndexB = 0;
        importHex = new BisqTextArea();
        importHex.setEditable(true);
        importHex.setWrapText(true);
        importHex.setPrefSize(800, 150);
        add(importHex, 0, ++rowIndexB);
        add(new Label(""), 0, ++rowIndexB);  // spacer
        Button buttonImport = new AutoTooltipButton("Import From String");
        buttonImport.setOnAction(e -> {
            // here we need to populate the "inputs" fields from the data contained in the TextArea
            if (inputsPane.doImport(importHex.getText())) {
                // switch back to the inputs pane
                parent.hideAllPanes();
                inputsPane.activate();
            } else {
                importHex.setText("Import failed - data format incorrect");
            }
        });
        HBox hBox = new HBox(12, buttonImport);
        hBox.setAlignment(Pos.BASELINE_CENTER);
        hBox.setPrefWidth(800);
        add(hBox, 0, ++rowIndexB);
        add(new Label(""), 0, ++rowIndexB);  // spacer

        final Separator separator = new Separator(Orientation.HORIZONTAL);
        separator.setPadding(new Insets(10, 10, 10, 10));
        add(separator, 0, ++rowIndexB);

        add(new Label(""), 0, ++rowIndexB);  // spacer
        final Tuple2<Label, ComboBox<String>> xTuple = addTopLabelComboBox(this, rowIndexB, "Mediation Ticket", "", 0);
        mediationDropDown = xTuple.second;
        recentTickets = addCheckBox(this, rowIndexB, "Recent Tickets");
        recentTickets.setSelected(true);
        HBox hBox2 = new HBox(12, mediationDropDown, recentTickets);
        hBox2.setAlignment(Pos.BASELINE_CENTER);
        hBox2.setPrefWidth(800);
        add(hBox2, 0, ++rowIndexB);
        populateMediationTicketCombo(recentTickets.isSelected());
        recentTickets.setOnAction(e -> {
            populateMediationTicketCombo(recentTickets.isSelected());
        });
        add(new Label(""), 0, ++rowIndexB);  // spacer
        Button buttonImportTicket = new AutoTooltipButton("Import From Mediation Ticket");
        buttonImportTicket.setOnAction(e -> {
            // here we need to populate the "inputs" fields from the chosen mediator ticket
            Optional<Dispute> optionalDispute = mediationManager.findDispute(mediationDropDown.getValue());
            optionalDispute.ifPresent(dispute -> {
                inputsPane.importFromMediationTicket(dispute);
                parent.hideAllPanes();
                inputsPane.activate();
                UserThread.execute(() -> new Popup().warning("Ticket imported.  You still need to enter the multisig amount and specify if it is a legacy Tx").show());
            });
        });
        HBox hBox3 = new HBox(12, buttonImportTicket);
        hBox3.setAlignment(Pos.BASELINE_CENTER);
        hBox3.setPrefWidth(800);
        add(hBox3, 0, ++rowIndexB);
    }

    @Override
    public void activate() {
        importHex.setText("");
        super.activate();
    }

    @Override
    public String getName() {
        return "Import";
    }

    private void populateMediationTicketCombo(boolean recentTicketsOnly) {
        Instant twoWeeksAgo = Instant.ofEpochSecond(Instant.now().getEpochSecond() - TimeUnit.DAYS.toSeconds(14));
        disputeObservableList = mediationManager.getDisputesAsObservableList();
        ObservableList<String> disputeIds = FXCollections.observableArrayList();
        for (Dispute dispute :disputeObservableList) {
            if (dispute.getDisputePayoutTxId() != null)    // only show disputes not paid out
                continue;
            if (recentTicketsOnly && dispute.getOpeningDate().toInstant().isBefore(twoWeeksAgo))
                continue;
            if (!disputeIds.contains(dispute.getTradeId()))
                disputeIds.add(dispute.getTradeId());
        }
        disputeIds.sort(String::compareTo);
        mediationDropDown.setItems(disputeIds);
    }
}
