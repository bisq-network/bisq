package io.bitsquare.gui;

public interface NavigationController
{
    ChildController navigateToView(NavigationItem navigationItem);

    ChildController navigateToView(String fxmlView);
}
