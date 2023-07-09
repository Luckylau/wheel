package com.luckylau.wheel.common.exception;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class RemoteException extends BaseRuntimeException {
    public RemoteException(int errorCode) {
        super(errorCode);
    }

    public RemoteException(int errorCode, String msg) {
        super(errorCode, msg);
    }

    public RemoteException(int errorCode, Throwable throwable) {
        super(errorCode, throwable);
    }
}
