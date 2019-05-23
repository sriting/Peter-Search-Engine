package edu.uci.ics.cs221.index.inverted;

import com.google.common.primitives.Bytes;

import java.util.ArrayList;
import java.util.List;

/**
 * Implement this compressor with Delta Encoding and Variable-Length Encoding.
 * See Project 3 description for details.
 */
public class DeltaVarLenCompressor implements Compressor {

    @Override
    public byte[] encode(List<Integer> integers) {
        // do variable encoding and delta encoding
        List<byte[]> byteArray = new ArrayList();
        int length = 0; int previous = 0;
        for(int i = 0; i < integers.size(); i++){
            byte[] temp = encodeNumber(integers.get(i)-previous);
            previous = integers.get(i);
            length += temp.length;
            byteArray.add(temp);
        }

        byte[] encodeList = new byte[length]; int offset = 0;
        for(int k = 0; k < byteArray.size(); k++){
            System.arraycopy(byteArray.get(k),0, encodeList, offset, byteArray.get(k).length);
            offset += byteArray.get(k).length;
        }

        return encodeList;

    }


    public byte[] encodeNumber(int number){
        int numTemp = number; int count = 1;
        while(numTemp>=128){
            numTemp = numTemp/128; count++;
        }
        byte[] numArr = new byte[count];
        for(int i = 0; i < count; i++){
            numArr[count-1-i] = (byte)(number%128 ^ 0x80);
            number = number / 128;
        }
        numArr[count-1] = (byte)(numArr[count-1] & 0x7F);
        return numArr;
    }

    @Override
    public List<Integer> decode(byte[] bytes, int start, int length) {
        List<Integer> numList = new ArrayList<>();
        int number = 0;

        // do variable decoding
        for(int i = start; i< start+length; i++){
            if((bytes[i] & 0x80) == 0){
                number += (int)bytes[i];
                numList.add(number);
                number = 0;
            }else{
                number += (bytes[i] & 0x7F);
                number = number << 7;
            }
        }
        // do delta decoding
        if(numList.size()>1) {
            for (int k = 0; k < numList.size() - 1; k++) {
                numList.set(k + 1, numList.get(k) + numList.get(k + 1));
            }
        }

        return numList;

    }
}
