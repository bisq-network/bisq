package io.bitsquare.gui.components;

import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.locale.Localisation;
import io.bitsquare.storage.Storage;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LazyLoadingTabPane extends TabPane
{
    private String storageId;
    private NavigationController navigationController;
    private String[] tabContentFXMLUrls;
    private Storage storage;
    SingleSelectionModel<Tab> selectionModel;
    private ChildController childController;

    private Map<Integer, Node> views = new HashMap<>();
    private Map<Integer, ChildController> controllers = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public LazyLoadingTabPane()
    {
        super();
    }

    public void initialize(NavigationController navigationController, Storage storage, String... tabContentFXMLUrls)
    {
        if (tabContentFXMLUrls.length == 0)
            throw new IllegalArgumentException("No tabContentFXMLUrls defined");

        this.tabContentFXMLUrls = tabContentFXMLUrls;
        this.navigationController = navigationController;
        this.tabContentFXMLUrls = tabContentFXMLUrls;
        this.storage = storage;

        storageId = navigationController.getClass().getName() + ".selectedTabIndex";

        selectionModel = getSelectionModel();
        selectionModel.selectedItemProperty().addListener(new ChangeListener<Tab>()
        {
            @Override
            public void changed(ObservableValue<? extends Tab> observableValue, Tab oldTab, Tab newTab)
            {
                onTabSelectedIndexChanged();
            }
        });

        Object indexObject = storage.read(storageId);
        if (indexObject != null)
            selectionModel.select((int) indexObject);

        onTabSelectedIndexChanged();
    }

    public void cleanup()
    {
        if (childController != null)
            childController.cleanup();
    }

    public ChildController navigateToView(String fxmlView)
    {
        for (int i = 0; i < tabContentFXMLUrls.length; i++)
        {
            if (tabContentFXMLUrls[i].equals(fxmlView))
            {
                selectionModel.select(i);
                return childController;
            }
            i++;
        }
        return null;
    }

    private void onTabSelectedIndexChanged()
    {
        int index = selectionModel.getSelectedIndex();
        if (index < tabContentFXMLUrls.length && index > -1)
        {
            if (childController != null)
                childController.cleanup();

            Node view = null;
            if (index < views.size())
            {
                view = views.get(index);
                childController = controllers.get(index);
            }
            if (view == null)
            {
                String fxmlView = tabContentFXMLUrls[index];
                final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(fxmlView), Localisation.getResourceBundle());
                try
                {
                    view = loader.load();
                    views.put(index, view);

                    childController = loader.getController();
                    childController.setNavigationController(navigationController);
                    controllers.put(index, childController);
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }

            selectionModel.getSelectedItem().setContent(view);
            childController.setNavigationController(navigationController);

            storage.write(storageId, index);
        }
    }

}
