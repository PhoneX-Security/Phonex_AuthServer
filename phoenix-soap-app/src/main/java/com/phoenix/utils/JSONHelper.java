/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.utils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * Helper class for converting POJO to JSON.
 * Using jackson-mapper-asl
 * 
 * Inspiration: http://www.mkyong.com/java/how-to-convert-java-object-to-from-json-jackson/
 * @author ph4r05
 */
public class JSONHelper {
    
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
}
