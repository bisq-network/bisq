/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.popups;

import io.bitsquare.common.util.Tuple2;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static io.bitsquare.gui.util.FormBuilder.addLabelComboBox;
import static io.bitsquare.gui.util.FormBuilder.addMultilineLabel;

public class SelectDepositTxPopup extends Popup {
    private static final Logger log = LoggerFactory.getLogger(SelectDepositTxPopup.class);
    private ComboBox<Transaction> transactionsComboBox;
    private List<Transaction> transaction;
    private Optional<Consumer<Transaction>> selectHandlerOptional;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SelectDepositTxPopup() {
    }

    public void show() {
        if (headLine == null)
            headLine = "Select deposit transaction for dispute";

        width = 700;
        createGridPane();
        addHeadLine();
        addContent();
        addCloseButton();
        PopupManager.queueForDisplay(this);
    }

    public SelectDepositTxPopup onSelect(Consumer<Transaction> selectHandler) {
        this.selectHandlerOptional = Optional.of(selectHandler);
        return this;
    }

    public SelectDepositTxPopup transactions(List<Transaction> transaction) {
        this.transaction = transaction;
        return this;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addContent() {
        Label label = addMultilineLabel(gridPane, ++rowIndex,
                "The deposit transaction was not stored in the trade.\n" +
                        "Please select one of the existing MultiSig transactions from your wallet which was the " +
                        "deposit transaction used in the failed trade.",
                10);

        GridPane.setMargin(label, new Insets(0, 0, 10, 0));

        Tuple2<Label, ComboBox> tuple = addLabelComboBox(gridPane, ++rowIndex, "Select deposit transaction");
        transactionsComboBox = tuple.second;
        transactionsComboBox.setPromptText("Select");
        transactionsComboBox.setConverter(new StringConverter<Transaction>() {
            @Override
            public String toString(Transaction transaction) {
                return transaction.getHashAsString();
            }

            @Override
            public Transaction fromString(String string) {
                return null;
            }
        });
        transactionsComboBox.setItems(FXCollections.observableArrayList(transaction));
        transactionsComboBox.setOnAction(event -> {
            selectHandlerOptional.get().accept(transactionsComboBox.getSelectionModel().getSelectedItem());
            hide();
        });
    }


}
