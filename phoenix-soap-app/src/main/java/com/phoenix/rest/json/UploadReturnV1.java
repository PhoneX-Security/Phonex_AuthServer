/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.rest.json;

/**
 *
 * @author ph4r05
 */
public class UploadReturnV1 {
    private int version;
    private int errorCode;
    private String message;
    private String nonce2;

    public UploadReturnV1() {
        version=1;
    }
    
    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getNonce2() {
        return nonce2;
    }

    public void setNonce2(String nonce2) {
        this.nonce2 = nonce2;
    }
}
