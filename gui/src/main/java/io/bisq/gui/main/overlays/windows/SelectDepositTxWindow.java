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

package io.bisq.gui.main.overlays.windows;

import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.gui.main.overlays.Overlay;
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

import static io.bisq.gui.util.FormBuilder.addLabelComboBox;
import static io.bisq.gui.util.FormBuilder.addMultilineLabel;

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
            headLine = Res.get("selectDepositTxWindow.headline");

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

    public SelectDepositTxWindow transactions(List<Transaction> transactions) {
        this.transactions = transactions;
        return this;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addContent() {
        Label label = addMultilineLabel(gridPane, ++rowIndex, Res.get("selectDepositTxWindow.msg"), 10);
        GridPane.setMargin(label, new Insets(0, 0, 10, 0));

        Tuple2<Label, ComboBox> tuple = addLabelComboBox(gridPane, ++rowIndex, Res.get("selectDepositTxWindow.select"));
        //noinspection unchecked
        transactionsComboBox = tuple.second;
        transactionsComboBox.setPromptText(Res.get("shared.select"));
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
