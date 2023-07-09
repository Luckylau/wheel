package com.luckylau.wheel.common.exception;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class BaseRuntimeException extends RuntimeException {
    public static final String ERROR_MESSAGE_FORMAT = "errCode: %d, errMsg: %s ";
    private static final long serialVersionUID = 3513491993982293262L;
    private int errCode;

    public BaseRuntimeException(int errCode) {
        super();
        this.errCode = errCode;
    }

    public BaseRuntimeException(int errCode, String errMsg) {
        super(String.format(ERROR_MESSAGE_FORMAT, errCode, errMsg));
        this.errCode = errCode;
    }

    public BaseRuntimeException(int errCode, Throwable throwable) {
        super(throwable);
        this.errCode = errCode;
    }

    public BaseRuntimeException(int errCode, String errMsg, Throwable throwable) {
        super(String.format(ERROR_MESSAGE_FORMAT, errCode, errMsg), throwable);
        this.errCode = errCode;
    }

    public int getErrCode() {
        return errCode;
    }

    public void setErrCode(int errCode) {
        this.errCode = errCode;
    }
}
