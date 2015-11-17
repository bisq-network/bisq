package io.nucleo.net;

public interface DisconnectReason {

  public abstract String toString();

  public boolean isGraceful();

  public boolean isRemote();

}
