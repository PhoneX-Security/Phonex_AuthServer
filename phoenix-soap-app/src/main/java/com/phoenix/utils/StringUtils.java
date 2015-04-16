/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.utils;

/**
 *
 * @author dusanklinec
 */
public class StringUtils {
    /**
     * Takes maximally first N characters from the string. 
     * 
     * @param string
     * @param lengthLimit
     * @return 
     */
    public static String takeMaxN(String string, int lengthLimit){
        if (string == null){
            return null;
        }
        
        final int len = string.length();
        if (len <= lengthLimit){
            return string;
        }
        
        return string.substring(0, lengthLimit);
    }

    public static boolean isEmpty(String str){
        return str == null || str.isEmpty();
    }
}
