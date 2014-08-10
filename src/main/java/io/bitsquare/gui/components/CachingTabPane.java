package io.bitsquare.gui.components;

import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.Hibernate;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.storage.Persistence;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * That class caches the already created views in a tab pane.
 * So when switching back to an already opened tab it is faster as no fxml loading is needed anymore.
 */
public class CachingTabPane extends TabPane
{
    private static final Logger log = LoggerFactory.getLogger(CachingTabPane.class);

    private final List<TabInfo> tabInfoList = new ArrayList<>();
    private NavigationController navigationController;
    private Persistence persistence;
    private int selectedTabIndex;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public CachingTabPane()
    {
        super();
    }

    public void initialize(NavigationController navigationController, Persistence persistence, String... tabContentFXMLUrls)
    {
        if (tabContentFXMLUrls.length == 0)
        {
            throw new IllegalArgumentException("No tabContentFXMLUrls defined");
        }

        this.navigationController = navigationController;
        this.persistence = persistence;

        for (String tabContentFXMLUrl : tabContentFXMLUrls) tabInfoList.add(new TabInfo(tabContentFXMLUrl));

        getSelectionModel().selectedItemProperty().addListener((observableValue, oldTab, newTab) -> onTabSelectedIndexChanged());

        // use parent to read selectedTabIndex
        Object indexObject = persistence.read(navigationController, "selectedTabIndex");
        selectedTabIndex = (indexObject == null) ? 0 : (int) indexObject;
        if (selectedTabIndex == 0) onTabSelectedIndexChanged();

        getSelectionModel().select(selectedTabIndex);
    }

    public void cleanup()
    {
        if (tabInfoList.get(selectedTabIndex).controller != null)
            tabInfoList.get(selectedTabIndex).controller.cleanup();
    }

    public ChildController navigateToView(String fxmlView)
    {
        for (int i = 0; i < tabInfoList.size(); i++)
        {
            if (tabInfoList.get(i).url.equals(fxmlView))
            {
                getSelectionModel().select(i);
                return tabInfoList.get(selectedTabIndex).controller;
            }
        }
        // if not found
        throw new IllegalArgumentException("fxmlView not defined in tabContentFXMLUrlMap.");
    }

    private void onTabSelectedIndexChanged()
    {
        selectedTabIndex = getSelectionModel().getSelectedIndex();

        if (tabInfoList.get(selectedTabIndex).controller != null)
        {
            // set old one to sleep
            ((Hibernate) tabInfoList.get(selectedTabIndex).controller).sleep();
        }
        else
        {
            // load for the first time
            String fxmlView = tabInfoList.get(selectedTabIndex).url;
            final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(fxmlView));
            try
            {
                tabInfoList.get(selectedTabIndex).view = loader.load();
                tabInfoList.get(selectedTabIndex).controller = loader.getController();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        getSelectionModel().getSelectedItem().setContent(tabInfoList.get(selectedTabIndex).view);
        tabInfoList.get(selectedTabIndex).controller.setNavigationController(navigationController);
        ((Hibernate) tabInfoList.get(selectedTabIndex).controller).awake();

        // use parent to write selectedTabIndex
        persistence.write(navigationController, "selectedTabIndex", selectedTabIndex);
    }

    public void setSelectedTabIndex(int selectedTabIndex)
    {
        getSelectionModel().select(selectedTabIndex);
    }
}

class TabInfo
{
    Node view;
    ChildController controller;
    final String url;

    TabInfo(String url)
    {
        this.url = url;
    }
}