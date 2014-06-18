package io.bitsquare.gui.arbitrators.profile;

import com.google.inject.Inject;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.Arbitrator;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

public class ArbitratorProfileController implements Initializable, ChildController
{

    private Settings settings;
    private Storage storage;

    private Arbitrator arbitrator;
    private NavigationController navigationController;

    @FXML
    private Label nameLabel;
    @FXML
    private TextField nameTextField, languagesTextField, reputationTextField, maxTradeVolumeTextField, passiveServiceFeeTextField,
            arbitrationFeeTextField, methodsTextField, idVerificationsTextField, webPageTextField;
    @FXML
    private TextArea descriptionTextArea;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitratorProfileController(Settings settings, Storage storage)
    {

        this.settings = settings;
        this.storage = storage;

        Settings savedSettings = (Settings) storage.read(settings.getClass().getName());
        if (savedSettings != null)
        {
            settings.updateFromStorage(savedSettings);
        }
        else
        {
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applyArbitrator(Arbitrator arbitrator)
    {
        if (arbitrator != null)
        {
            String name = "";
            switch (arbitrator.getIdType())
            {
                case REAL_LIFE_ID:
                    name = "Name:";
                    break;
                case NICKNAME:
                    name = "Nickname:";
                    break;
                case COMPANY:
                    name = "Company:";
                    break;
            }
            nameLabel.setText(name);

            nameTextField.setText(arbitrator.getName());
            languagesTextField.setText(BitSquareFormatter.languageLocalesToString(arbitrator.getLanguages()));
            reputationTextField.setText(arbitrator.getReputation().toString());
            maxTradeVolumeTextField.setText(String.valueOf(arbitrator.getMaxTradeVolume()) + " BTC");
            passiveServiceFeeTextField.setText(String.valueOf(arbitrator.getPassiveServiceFee()) + " % (Min. " + String.valueOf(arbitrator.getMinPassiveServiceFee()) + " BTC)");
            arbitrationFeeTextField.setText(String.valueOf(arbitrator.getArbitrationFee()) + " % (Min. " + String.valueOf(arbitrator.getMinArbitrationFee()) + " BTC)");
            methodsTextField.setText(BitSquareFormatter.arbitrationMethodsToString(arbitrator.getArbitrationMethods()));
            idVerificationsTextField.setText(BitSquareFormatter.arbitrationIDVerificationsToString(arbitrator.getIdVerifications()));
            webPageTextField.setText(arbitrator.getWebUrl());
            descriptionTextArea.setText(arbitrator.getDescription());
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    @Override
    public void cleanup()
    {

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}

