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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.FormBuilder;

import bisq.core.locale.Res;

import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static bisq.desktop.util.FormBuilder.addMultilineLabel;

//TODO might be removed, but leave it for now until sure we will not use it anymore.
public class SelectDepositTxWindow extends Overlay<SelectDepositTxWindow> {
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

        width = 768;
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();
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

        transactionsComboBox = FormBuilder.addComboBox(gridPane, ++rowIndex, Res.get("selectDepositTxWindow.select"));
        ;
        transactionsComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Transaction transaction) {
                return transaction.getTxId().toString();
            }

            @Override
            public Transaction fromString(String string) {
                return null;
            }
        });
        transactionsComboBox.setItems(FXCollections.observableArrayList(transactions));
        transactionsComboBox.setOnAction(event -> {
            if (selectHandlerOptional.isPresent()) {
                selectHandlerOptional.get().accept(transactionsComboBox.getSelectionModel().getSelectedItem());
            }
            hide();
        });
    }


}
