package io.bitsquare.p2p;

import java.io.Serializable;

public interface Message extends Serializable {
    int getMessageVersion();
}
