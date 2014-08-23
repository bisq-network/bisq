package io.bitsquare.gui.arbitrators.profile;

import io.bitsquare.gui.CachedViewController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.ViewController;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Persistence;
import io.bitsquare.user.Arbitrator;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javax.inject.Inject;

public class ArbitratorProfileController extends CachedViewController
{

    private final Settings settings;

    private final Persistence persistence;
    private Arbitrator arbitrator;


    @FXML private Label nameLabel;
    @FXML private TextField nameTextField, languagesTextField, reputationTextField, maxTradeVolumeTextField, passiveServiceFeeTextField, arbitrationFeeTextField, methodsTextField,
            idVerificationsTextField, webPageTextField;
    @FXML private TextArea descriptionTextArea;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitratorProfileController(Settings settings, Persistence persistence)
    {
        this.settings = settings;
        this.persistence = persistence;

        //  Settings persistedSettings = (Settings) storage.read(settings.getClass().getName());
        // settings.applyPersistedSettings(persistedSettings);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        super.initialize(url, rb);
    }

    @Override
    public void terminate()
    {
        super.terminate();
    }

    @Override
    public void deactivate()
    {
        super.deactivate();
    }

    @Override
    public void activate()
    {
        super.activate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setParentController(ViewController parentController)
    {
        super.setParentController(parentController);
    }

    @Override
    public ViewController loadViewAndGetChildController(NavigationItem navigationItem)
    {
        return null;
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

}

