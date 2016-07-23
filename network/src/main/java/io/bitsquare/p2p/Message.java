package io.bitsquare.p2p;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;

public interface Message extends Serializable {
    int getMessageVersion();

    @Nullable
    ArrayList<Integer> getSupportedCapabilities();
}
