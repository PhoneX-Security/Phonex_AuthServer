package com.phoenix.utils;

import java.util.Collection;

/**
 * Created by dusanklinec on 28.04.15.
 */
public class MiscUtils {
    public static int collectionSize(Collection<?> c){
        if (c == null){
            return -1;
        }

        return c.size();
    }
}
