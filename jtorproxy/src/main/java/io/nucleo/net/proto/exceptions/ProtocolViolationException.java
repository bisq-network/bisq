package io.nucleo.net.proto.exceptions;

public class ProtocolViolationException extends Exception {

  public ProtocolViolationException() {
  }

  public ProtocolViolationException(Throwable cause) {
    super(cause);
  }

  public ProtocolViolationException(String msg) {
    super(msg);
  }

  public ProtocolViolationException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
