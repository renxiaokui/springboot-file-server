package com.github.ren.fastdfs.ex;

/**
 * @Description 客户端异常
 * @Author ren
 * @Since 1.0
 */
public class ClientException extends RuntimeException {
    public ClientException() {
        super();
    }

    public ClientException(String message) {
        super(message);
    }

    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClientException(Throwable cause) {
        super(cause);
    }

    protected ClientException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
