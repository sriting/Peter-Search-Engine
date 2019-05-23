package edu.uci.ics.cs221.analysis;

import edu.uci.ics.cs221.index.inverted.InvertedIndexManager;
import edu.uci.ics.cs221.index.inverted.NaiveCompressor;
import edu.uci.ics.cs221.index.inverted.PageFileChannel;
import edu.uci.ics.cs221.index.inverted.PositionList;
import edu.uci.ics.cs221.storage.Document;
import edu.uci.ics.cs221.storage.DocumentStore;
import edu.uci.ics.cs221.storage.MapdbDocStore;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    public static void main(String[] args){
        /*
        Project 1
         */
//        PunctuationTokenizer punctuatetokenizer = new PunctuationTokenizer();
//        punctuatetokenizer.tokenize("I am Happy Today! What about you, your friends Yidan?");
//
//        WordBreakTokenizer wordbreaktokenizer = new WordBreakTokenizer();
//        wordbreaktokenizer.tokenize("IlOveSAnFrancIsCo");
//        wordbreaktokenizer.tokenize("FindthelongestpalindromicstringYoumayassumethatthemaximumlengthisonehundred");
//
//        JapaneseWordBreakTokenizer jpnwordbreaktokenizer = new JapaneseWordBreakTokenizer();
//        jpnwordbreaktokenizer.tokenize("英語を学ぶ");
//        jpnwordbreaktokenizer.tokenize("箸を食べる");
//        jpnwordbreaktokenizer.tokenize("猫を養う");

        String indexFolder = "./index/Team21FlushTest/";
        ComposableAnalyzer analyzer = new ComposableAnalyzer(new PunctuationTokenizer(), token -> token);
        InvertedIndexManager manager = InvertedIndexManager.createOrOpenPositional(indexFolder, analyzer, new NaiveCompressor());
        Document doc1 = new Document("dog cat penguin");
        Document doc2 = new Document("cat bird cat fish cat lion cat");
        Document doc3 = new Document("fish cat bear cat dog");
        analyzer = new ComposableAnalyzer(new PunctuationTokenizer(), token -> token);
        manager = InvertedIndexManager.createOrOpen(indexFolder, analyzer);
        manager.addDocument(doc1);
        manager.addDocument(doc2);
        manager.addDocument(doc3);
        Set<Integer> list = manager.positionList.positionList.row("fish").keySet();
        for(Integer i : list){
            System.out.println(i);
        }

//        Iterator it = manager.indexList.indexList.entrySet().iterator();
//        while(it.hasNext()){
//            Map.Entry entry = (Map.Entry) it.next();
//            Object key = entry.getKey();
//            Object val = entry.getValue();
//            System.out.println(key+" : "+val);
//        }
//        manager.flush();
//        String dictFile = indexFolder+ "Dictionary" + 0 + ".txt";
//        String listFile = indexFolder + "InvertedList" + 0 + ".txt";
//        String storeFile = indexFolder+ "MapdbDocStore"+ 0 +".db";
//        Path dictPath = Paths.get(dictFile);
//        Path listPath = Paths.get(listFile);
//        DocumentStore documentStore = MapdbDocStore.createOrOpen(storeFile);
//        PageFileChannel dictChannel = PageFileChannel.createOrOpen(dictPath);
//        PageFileChannel listChannel = PageFileChannel.createOrOpen(listPath);
//        ByteBuffer dictByteBuffer = dictChannel.readPage(0);
//        ByteBuffer listByteBuffer = listChannel.readPage(0);
//        byte[] dict = dictByteBuffer.array();
//        byte[] list = listByteBuffer.array();
//        System.out.println(new String(dict));
//
//        byte[] Dict = dictChannel.readAllPages().array();
//        System.out.println(new String(Dict));
//
//        documentStore.close();
//
//        Iterator iter = manager.documentIterator();
//        while(iter.hasNext()){
//            System.out.println(iter.next().toString());
//        }
//        System.out.println(manager.getNumSegments());



    }
}
