package io.bitsquare.gui;

import org.jetbrains.annotations.NotNull;

public interface ChildController
{
    void setNavigationController(@NotNull NavigationController navigationController);

    void cleanup();
}
