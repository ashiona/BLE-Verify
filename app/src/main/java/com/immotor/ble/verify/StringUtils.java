package com.immotor.ble.verify;

/**
 * Created by Ashion on 2017/2/28.
 */

public class StringUtils {

    public static String bytesToHexString(byte[] src){
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    public static String reverseString2(String s) {
        int length = s.length();
        String reverse = "";
        if (length > 0) {
            reverse = s.substring(length - 2);
            String leftStr = s.substring(0, length - 2);
            reverse =  reverse + reverseString2(leftStr);
        }
        return reverse;
    }

    public static String convertMacAddress(String SID){
        StringBuffer macAddress = new StringBuffer();
        for (int i=0; i<6; i++) {
            macAddress.append(SID.substring(i*2, (i + 1)* 2));
            if (i < 5) {
                macAddress.append(":");
            }
        }
        return macAddress.toString();
    }

    public static void main(String[] ars){

        String sid = "88C255ABCC0A";

        String result = convertMacAddress(sid);

        System.out.println(result);

    }
}
