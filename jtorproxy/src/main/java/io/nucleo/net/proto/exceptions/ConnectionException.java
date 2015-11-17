package io.nucleo.net.proto.exceptions;

public class ConnectionException extends Exception {
  public ConnectionException(String msg) {
    super(msg);
  }

  public ConnectionException(Throwable cause) {
    super(cause);
  }

  public ConnectionException(String msg, Throwable cause) {
    super(msg, cause);
  }

}
