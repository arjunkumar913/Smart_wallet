package com.vardhan.wallet.payment;

/** The gateway could not be reached, or answered with an error. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
