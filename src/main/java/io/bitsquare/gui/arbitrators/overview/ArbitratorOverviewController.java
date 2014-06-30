package io.bitsquare.gui.arbitrators.overview;

import com.google.inject.Inject;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.arbitrators.profile.ArbitratorProfileController;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.locale.Localisation;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.ArbitratorListener;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.Arbitrator;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"ALL", "UnusedParameters"})
public class ArbitratorOverviewController implements Initializable, ChildController, NavigationController, ArbitratorListener
{
    private final Settings settings;
    private final Storage storage;
    @NotNull
    private final MessageFacade messageFacade;
    private final List<Arbitrator> allArbitrators = new ArrayList<>();
    private Arbitrator currentArbitrator;
    private NavigationController navigationController;
    private ArbitratorProfileController arbitratorProfileController;
    private int index = -1;

    @FXML
    private Button prevButton, nextButton, selectButton, closeButton;
    @FXML
    private AnchorPane rootContainer;
    @FXML
    private Pane arbitratorProfile;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitratorOverviewController(Settings settings, Storage storage, @NotNull MessageFacade messageFacade)
    {

        this.settings = settings;
        this.storage = storage;
        this.messageFacade = messageFacade;

        messageFacade.addArbitratorListener(this);
        messageFacade.getArbitrators(LanguageUtil.getDefaultLanguageLocale());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        navigateToView(NavigationItem.ARBITRATOR_PROFILE);
        checkButtonState();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(@NotNull NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    @Override
    public void cleanup()
    {

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: NavigationController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    @Override
    public ChildController navigateToView(@NotNull NavigationItem navigationItem)
    {
        if (arbitratorProfileController != null)
            arbitratorProfileController.cleanup();

        @NotNull final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()), Localisation.getResourceBundle());
        try
        {
            final Node view = loader.load();
            arbitratorProfileController = loader.getController();
            arbitratorProfileController.setNavigationController(this);
            rootContainer.getChildren().set(0, view);

            return arbitratorProfileController;
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ArbitratorListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onArbitratorAdded(Data offerData, boolean success)
    {
    }

    @Override
    public void onArbitratorsReceived(@Nullable Map<Number160, Data> dataMap, boolean success)
    {
        if (success && dataMap != null)
        {
            allArbitrators.clear();

            for (@NotNull Data arbitratorData : dataMap.values())
            {
                try
                {
                    Object arbitratorDataObject = arbitratorData.getObject();
                    if (arbitratorDataObject instanceof Arbitrator)
                    {
                        @NotNull Arbitrator arbitrator = (Arbitrator) arbitratorDataObject;
                        allArbitrators.add(arbitrator);
                    }
                } catch (@NotNull ClassNotFoundException | IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        else
        {
            allArbitrators.clear();
        }

        if (!allArbitrators.isEmpty())
        {
            index = 0;
            currentArbitrator = allArbitrators.get(index);
            arbitratorProfileController.applyArbitrator(currentArbitrator);
            checkButtonState();
        }
    }

    @Override
    public void onArbitratorRemoved(Data data, boolean success)
    {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onPrevious(ActionEvent actionEvent)
    {
        if (index > 0)
        {
            index--;
            currentArbitrator = allArbitrators.get(index);
            arbitratorProfileController.applyArbitrator(currentArbitrator);
        }
        checkButtonState();
    }

    @FXML
    public void onNext(ActionEvent actionEvent)
    {
        if (index < allArbitrators.size() - 1)
        {
            index++;
            currentArbitrator = allArbitrators.get(index);
            arbitratorProfileController.applyArbitrator(currentArbitrator);
        }
        checkButtonState();
    }

    @FXML
    public void onSelect(ActionEvent actionEvent)
    {
        settings.addAcceptedArbitrator(currentArbitrator);
        storage.write(settings.getClass().getName(), settings);
    }

    @FXML
    public void onClose(ActionEvent actionEvent)
    {
        @NotNull Stage stage = (Stage) rootContainer.getScene().getWindow();
        stage.close();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void checkButtonState()
    {
        prevButton.setDisable(index < 1);
        nextButton.setDisable(index == allArbitrators.size() - 1 || index == -1);
    }
}

