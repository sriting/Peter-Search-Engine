package edu.uci.ics.cs221.index.inverted;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PositionList {
    public Table<String, Integer, List<Integer>> positionList;
    public int tableSize = 0;

    public PositionList(){
        positionList = HashBasedTable.create();
    }

    public void addPosition(String keyword, Integer docID, Integer position){
        if(positionList.rowKeySet().contains(keyword)){
            if(positionList.row(keyword).containsKey(docID)){
                // if the keyword in this docID exists, add position
                positionList.row(keyword).get(docID).add(position);
            }else{
                // if the keyword exists and the docID doesn't, add a position list of this docID
                List<Integer> newList = new ArrayList<>();
                newList.add(position);
                positionList.row(keyword).put(docID, newList);
            }
        }else{
            // if the keyword doesn't exist, add this keyword, docID and its position list
            List<Integer> newList = new ArrayList<>();
            newList.add(position);
            positionList.put(keyword, docID, newList);
        }
        tableSize++;
    }

    public void addPositionList(String keyword, Integer docID, List<Integer> positions){
        positionList.put(keyword,docID,positions);
        tableSize += positions.size();
    }

    public void addPositionLists(String keyword, Map<Integer, List<Integer>> positionIndexLists){
        for(Map.Entry<Integer, List<Integer>> entry : positionIndexLists.entrySet()){
            positionList.put(keyword, entry.getKey(), entry.getValue());
            tableSize += entry.getValue().size();
        }
    }

    public List<Integer> getPositionList(String keyword, Integer docID){
        return positionList.get(keyword, docID);
    }

    public void deleteAll(){
        positionList.clear();
        tableSize = 0;
    }

    public Table<String, Integer, List<Integer>> returnTable(){
        return positionList;
    }

    public int size() { return tableSize;}
}
