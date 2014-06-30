package io.bitsquare.gui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface NavigationController
{
    @Nullable
    ChildController navigateToView(@NotNull NavigationItem navigationItem);
}
