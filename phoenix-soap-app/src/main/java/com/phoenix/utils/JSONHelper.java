/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.utils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Helper class for converting POJO to JSON.
 * Using jackson-mapper-asl
 * 
 * Inspiration: http://www.mkyong.com/java/how-to-convert-java-object-to-from-json-jackson/
 * @author ph4r05
 */
public class JSONHelper {
    public static final ArrayList<String> EMPTY_STRING_ARRAY_LIST = new ArrayList<String>();

    /**
     * Converts POJO object to JSON string.
     * @param obj
     * @return
     * @throws IOException 
     */
    public static String obj2JSON(Object obj) throws IOException{
	    ObjectMapper mapper = new ObjectMapper();
	    return mapper.writeValueAsString(obj);
    }

    /**
     * Converts POJO to JSON and writes it to OutputStream.
     * @param obj
     * @param os
     * @throws IOException 
     */
    public static void writeObj2JSON(Object obj, OutputStream os) throws IOException{
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(os, obj);
    }

    /**
     * Converts json array of strings to array list of strings.
     *
     * @param array
     * @return
     */
    public static ArrayList<String> jsonStringArrayToList(final JSONArray array) throws JSONException {
        final int length = array.length();
        if (length == 0){
            return EMPTY_STRING_ARRAY_LIST;
        }

        final ArrayList<String> list = new ArrayList<String>(length);
        for(int i = 0; i < length; i++){
            list.add(array.getString(i));
        }

        return list;
    }
}
