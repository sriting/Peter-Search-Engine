package edu.uci.ics.cs221.index.inverted;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InvertedIndexList {
    public Map<String, List<Integer>> indexList;

    public InvertedIndexList(){
        this.indexList = new HashMap<>();
    }

    public void addDocID(String keyword, int docID){
        if(indexList.containsKey(keyword)){
            indexList.get(keyword).add(docID);
        }else{
            List<Integer> temp = new ArrayList<>();
            temp.add(docID);
            indexList.put(keyword, temp);
        }
    }

    public void deleteAll(){
        indexList.clear();
    }

    public boolean isEmpty(){
        return indexList.isEmpty();
    }

    public void addList(String keyword, List<Integer> list){
        if(indexList.containsKey(keyword)){
            indexList.get(keyword).addAll(list);
        }else{
            indexList.put(keyword, list);
        }
    }

    public Map<String, List<Integer>> returnMap(){
        return indexList;
    }
}
