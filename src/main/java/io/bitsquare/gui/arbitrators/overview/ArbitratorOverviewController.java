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

package io.bitsquare.gui.arbitrators.overview;

import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.CachedViewController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.ViewController;
import io.bitsquare.gui.arbitrators.profile.ArbitratorProfileController;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.ArbitratorListener;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Persistence;
import io.bitsquare.user.Arbitrator;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javax.inject.Inject;
import net.tomp2p.peers.Number640;
import net.tomp2p.storage.Data;

/**
 * TODO remove tomp2p dependencies
 * import net.tomp2p.peers.Number160;
 * import net.tomp2p.storage.Data;
 */
@SuppressWarnings({"ALL", "UnusedParameters"})
public class ArbitratorOverviewController extends CachedViewController implements ArbitratorListener
{
    private final Settings settings;
    private final Persistence persistence;

    private final MessageFacade messageFacade;
    private final List<Arbitrator> allArbitrators = new ArrayList<>();
    private Arbitrator currentArbitrator;
    private ArbitratorProfileController arbitratorProfileController;
    private int index = -1;

    @FXML private Button prevButton, nextButton, selectButton, closeButton;
    @FXML private Pane arbitratorProfile;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitratorOverviewController(Settings settings, Persistence persistence, MessageFacade messageFacade)
    {

        this.settings = settings;
        this.persistence = persistence;
        this.messageFacade = messageFacade;

        messageFacade.addArbitratorListener(this);
        messageFacade.getArbitrators(LanguageUtil.getDefaultLanguageLocale());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        super.initialize(url, rb);

        loadViewAndGetChildController(NavigationItem.ARBITRATOR_PROFILE);
        checkButtonState();
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
        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try
        {
            final Node view = loader.load();
            arbitratorProfileController = loader.getController();
            arbitratorProfileController.setParentController(this);
            ((Pane) root).getChildren().set(0, view);

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
    public void onArbitratorsReceived(Map<Number640, Data> dataMap, boolean success)
    {
        if (success && dataMap != null)
        {
            allArbitrators.clear();

            for (Data arbitratorData : dataMap.values())
            {
                try
                {
                    Object arbitratorDataObject = arbitratorData.object();
                    if (arbitratorDataObject instanceof Arbitrator)
                    {
                        Arbitrator arbitrator = (Arbitrator) arbitratorDataObject;
                        allArbitrators.add(arbitrator);
                    }
                } catch (ClassNotFoundException | IOException e)
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
    public void onPrevious()
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
    public void onNext()
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
    public void onSelect()
    {
        settings.addAcceptedArbitrator(currentArbitrator);
        persistence.write(settings);
    }

    @FXML
    public void onClose()
    {
        Stage stage = (Stage) root.getScene().getWindow();
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

