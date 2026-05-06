package com.finance.portal.market.application.viop;

/**
 * Grafik desteklenmeyen VİOP sözleşmesi için fırlatılır.
 * Endeks, döviz, altın ve emtia dayanaklı sözleşmeler bu kapsamdadır.
 */
public class UnsupportedViopContractException extends RuntimeException {

    public UnsupportedViopContractException(String message) {
        super(message);
    }
}
