package org.ormada.exception;

public class UnableToOpenException extends RuntimeException {

    public UnableToOpenException() {
    }

    public UnableToOpenException(String message) {
        super(message);
    }

    public UnableToOpenException(Throwable t) {
        super(t);
    }

    public UnableToOpenException(String message, Throwable t) {
        super(message, t);
    }
}
