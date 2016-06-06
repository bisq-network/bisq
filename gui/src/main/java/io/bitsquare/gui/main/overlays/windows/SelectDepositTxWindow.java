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

package io.bitsquare.gui.main.overlays.windows;

import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.main.overlays.Overlay;
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

public class SelectDepositTxWindow extends Overlay<SelectDepositTxWindow> {
    private static final Logger log = LoggerFactory.getLogger(SelectDepositTxWindow.class);
    private ComboBox<Transaction> transactionsComboBox;
    private List<Transaction> transactions;
    private Optional<Consumer<Transaction>> selectHandlerOptional;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SelectDepositTxWindow() {
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = "Select deposit transaction for dispute";

        width = 700;
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        addCloseButton();
        applyStyles();
        display();
    }

    public SelectDepositTxWindow onSelect(Consumer<Transaction> selectHandler) {
        this.selectHandlerOptional = Optional.of(selectHandler);
        return this;
    }

    public SelectDepositTxWindow transactions(List<Transaction> transaction) {
        this.transactions = transaction;
        return this;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addContent() {
        Label label = addMultilineLabel(gridPane, ++rowIndex,
                "The deposit transaction was not stored in the trade.\n" +
                        "Please select one of the existing MultiSig transactions from your wallet which was the " +
                        "deposit transaction used in the failed trade.\n\n" +
                        "You can find the correct transaction by opening the trade details window (click on the trade ID in the list)" +
                        " and following the offer fee payment transaction output to the next transaction where you see " +
                        "the Multisig deposit transaction (the address starts with 3). That transaction ID should be " +
                        "visible in the list presented here. Once you found the correct transaction select that transaction here and continue.\n\n" +
                        "Sorry for the inconvenience but that error case should be happen very rare and in future we will try " +
                        "to find better ways to resolve it.",
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
        transactionsComboBox.setItems(FXCollections.observableArrayList(transactions));
        transactionsComboBox.setOnAction(event -> {
            selectHandlerOptional.get().accept(transactionsComboBox.getSelectionModel().getSelectedItem());
            hide();
        });
    }


}
