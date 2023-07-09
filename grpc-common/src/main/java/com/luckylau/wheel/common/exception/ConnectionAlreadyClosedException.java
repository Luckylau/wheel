package com.luckylau.wheel.common.exception;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class ConnectionAlreadyClosedException extends RemoteException {

    private static final int CONNECTION_ALREADY_CLOSED = 600;

    public ConnectionAlreadyClosedException(String msg) {
        super(CONNECTION_ALREADY_CLOSED);
    }

    public ConnectionAlreadyClosedException() {
        super(CONNECTION_ALREADY_CLOSED);
    }

    public ConnectionAlreadyClosedException(Throwable throwable) {
        super(CONNECTION_ALREADY_CLOSED, throwable);
    }
}
