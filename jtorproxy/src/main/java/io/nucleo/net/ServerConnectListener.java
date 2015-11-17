package io.nucleo.net;

public interface ServerConnectListener {

  /**
   * Called whenever an incoming connection was set up properly.
   * Connection.listen() needs to be called ASAP for the connection to become available
   *
   * @param con the newly established connection
   */
  public void onConnect(Connection con);
}
