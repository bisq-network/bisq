package io.bitsquare.gui.util;

import io.bitsquare.bank.BankAccountType;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.components.VSpacer;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;

import java.util.Currency;
import java.util.List;
import java.util.Locale;

public class FormBuilder
{
    public static Label addLabel(GridPane gridPane, String title, String value, int row)
    {
        gridPane.add(new Label(title), 0, row);
        Label valueLabel = new Label(value);
        gridPane.add(valueLabel, 1, row);
        return valueLabel;
    }

    public static Label addHeaderLabel(GridPane gridPane, String title, int row, int column)
    {
        Label headerLabel = new Label(title);
        headerLabel.setId("form-header-text");
        gridPane.add(headerLabel, column, row);
        return headerLabel;
    }

    public static Label addHeaderLabel(GridPane gridPane, String title, int row)
    {
        return addHeaderLabel(gridPane, title, row, 0);
    }

    public static TextField addInputField(GridPane gridPane, String title, String value, int row)
    {
        return addTextField(gridPane, title, value, row, true, true);
    }

    public static TextField addTextField(GridPane gridPane, String title, String value, int row)
    {
        return addTextField(gridPane, title, value, row, false, false);
    }


    public static TextField addTextField(GridPane gridPane, String title, String value, int row, boolean editable, boolean selectable)
    {
        gridPane.add(new Label(title), 0, row);
        TextField textField = new TextField(value);
        gridPane.add(textField, 1, row);
        textField.setMouseTransparent(!selectable && !editable);
        textField.setEditable(editable);

        return textField;
    }

    public static void addVSpacer(GridPane gridPane, int row)
    {
        gridPane.add(new VSpacer(10), 0, row);
    }

    public static Button addButton(GridPane gridPane, String title, int row)
    {
        Button button = new Button(title);
        gridPane.add(button, 1, row);
        return button;
    }

    public static ComboBox<Locale> addLocalesComboBox(GridPane gridPane, String title, List<Locale> list, int row)
    {
        gridPane.add(new Label(title), 0, row);
        ComboBox<Locale> comboBox = new ComboBox<>(FXCollections.observableArrayList(list));
        gridPane.add(comboBox, 1, row);
        return comboBox;
    }

    public static ComboBox<Currency> addCurrencyComboBox(GridPane gridPane, String title, List<Currency> list, int row)
    {
        gridPane.add(new Label(title), 0, row);
        ComboBox<Currency> comboBox = new ComboBox<>(FXCollections.observableArrayList(list));
        gridPane.add(comboBox, 1, row);
        return comboBox;
    }

    public static ComboBox<BankAccountType> addBankAccountComboBox(GridPane gridPane, String title, List<BankAccountType> list, int row)
    {
        gridPane.add(new Label(title), 0, row);
        ComboBox<BankAccountType> comboBox = new ComboBox<>(FXCollections.observableArrayList(list));
        gridPane.add(comboBox, 1, row);
        return comboBox;
    }

   /* public static ComboBox addLocalesComboBox(GridPane gridPane, String title, List<?> list, int row)
    {
        gridPane.add(new Label(title), 0, row);
        ComboBox<?> comboBox = new ComboBox<>(FXCollections.observableArrayList(list));
        gridPane.add(comboBox, 1, row);
        return comboBox;
    }   */


    public static TextField addConfirmationsLabel(GridPane gridPane, WalletFacade walletFacade, int row)
    {
        return FormBuilder.addTextField(gridPane, "Confirmations:", getConfirmationText(walletFacade), row);
    }

    public static ProgressIndicator addConfirmationsSpinner(GridPane gridPane, WalletFacade walletFacade, int row)
    {
        ProgressIndicator progressIndicator = new ProgressIndicator();
        gridPane.add(progressIndicator, 3, row);
        progressIndicator.setPrefSize(18, 18);
        if (walletFacade.getRegConfDepthInBlocks() == 0 && walletFacade.getRegConfNumBroadcastPeers() > 0)
            progressIndicator.setProgress(-1);
        else
            progressIndicator.setOpacity(0);

        return progressIndicator;
    }

    public static ImageView addConfirmationsIcon(GridPane gridPane, WalletFacade walletFacade, int row)
    {
        int depthInBlocks = walletFacade.getRegConfNumBroadcastPeers();
        int numBroadcastPeers = walletFacade.getRegConfDepthInBlocks();

        Image confirmIconImage;
        if (depthInBlocks > 0)
            confirmIconImage = Icons.getIconImage(Icons.getIconIDForConfirmations(depthInBlocks));
        else
            confirmIconImage = Icons.getIconImage(Icons.getIconIDForPeersSeenTx(numBroadcastPeers));

        ImageView confirmIconImageView = new ImageView(confirmIconImage);
        gridPane.add(confirmIconImageView, 2, row);

        return confirmIconImageView;
    }

    public static String getConfirmationText(WalletFacade walletFacade)
    {
        int numBroadcastPeers = walletFacade.getRegConfNumBroadcastPeers();
        int depthInBlocks = walletFacade.getRegConfDepthInBlocks();
        return depthInBlocks + " confirmation(s) / " + "Seen by " + numBroadcastPeers + " peer(s)";
    }


}
