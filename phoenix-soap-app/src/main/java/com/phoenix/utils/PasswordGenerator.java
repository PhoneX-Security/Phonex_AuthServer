package com.phoenix.utils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Simple password generator utility class.
 * Created by dusanklinec on 19.03.15.
 */
public class PasswordGenerator {
    private static final char[] characters = initCharacters();
    private static final char[] allCharacters = initAllCharacters();
    private static final char[] specialChars = new char[]{'_', ';', '-', '+', '=', '|', '!', '@', '#', '$', '^', '.', '/' };

    private static char[] initCharacters() {
        final int initialCapacity = 63;
        // a vector is a variable-size array
        final List<Character> chars = new Vector<Character>(initialCapacity);

        // add digits 0–9
        for (char c = '0'; c <= '9'; c++) {
            chars.add(c);
        }

        // add uppercase A–Z and '@'
        for (char c = '@'; c <= 'Z'; c++) {
            chars.add(c);
        }

        // add lowercase a–z
        for (char c = 'a'; c <= 'z'; c++) {
            chars.add(c);
        }

        // Copy the chars over to a simple array, now that we know
        // the length. The .toArray method could have been used here,
        // but its usage is a pain.
        final char[] charArray = new char[chars.size()];
        for (int i = 0; i < chars.size(); i++) {
            charArray[i] = chars.get(i);
        }

        return charArray;
    }

    private static char[] initAllCharacters(){
        final ArrayList<Character> lst = new ArrayList<Character>(characters.length + specialChars.length);
        for (char ch : characters) {
            lst.add(ch);
        }

        for(char ch : specialChars){
            lst.add(ch);
        }

        final char[] charArray = new char[lst.size()];
        for (int i = 0; i < lst.size(); i++) {
            charArray[i] = lst.get(i);
        }

        return charArray;
    }

    /**
     * Generate password of given length, possible with special characters.
     * @param length
     * @param specialCharacters
     * @return
     */
    public static String genPassword(int length, boolean specialCharacters){
        final char[] charList = specialCharacters ? allCharacters : characters;
        final int charLen = charList.length;
        final StringBuilder sb = new StringBuilder();
        final SecureRandom rnd = new SecureRandom();

        for(int i = 0; i < length; i++){
            sb.append(charList[rnd.nextInt(charLen)]);
        }

        return sb.toString();
    }
}
