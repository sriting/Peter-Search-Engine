package edu.uci.ics.cs221.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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

//        String indexFolder = "./index/Team21FlushTest/";
//        ComposableAnalyzer analyzer = new ComposableAnalyzer(new PunctuationTokenizer(), token -> token);
//        InvertedIndexManager manager = InvertedIndexManager.createOrOpenPositional(indexFolder, analyzer, new NaiveCompressor());
//        Document doc1 = new Document("dog cat penguin");
//        Document doc2 = new Document("cat bird cat fish cat lion cat");
//        Document doc3 = new Document("fish cat bear cat dog");
//        analyzer = new ComposableAnalyzer(new PunctuationTokenizer(), token -> token);
//        manager = InvertedIndexManager.createOrOpen(indexFolder, analyzer);
//        manager.addDocument(doc1);
//        manager.addDocument(doc2);
//        manager.addDocument(doc3);
//        Set<Integer> list = manager.positionList.positionList.row("fish").keySet();
//        for(Integer i : list){
//            System.out.println(i);
//        }

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

//        PositionList positionList = new PositionList();
//        positionList.addPositionList("a",0, Arrays.asList(0,2,4,5));
//        positionList.addPositionList("b",0,Arrays.asList(1,3,6));
//        positionList.addPositionList("b",1,Arrays.asList(0,2,4,5));
//        positionList.addPositionList("a",1,Arrays.asList(1,3,6));
//        System.out.println(positionList.size());
//        PriorityQueue<Integer> queue = new PriorityQueue(5);
//        queue.add(1);
//        queue.add(2);
//        queue.add(3);
//        queue.add(2);
//        queue.add(2);
//        queue.add(2);
//        System.out.println(queue.size());
        Path documentDirectory = Paths.get("./webpages");
        File file = new File(documentDirectory.toString() + "/cleaned/");
        int docLength = 0;
        if (file.exists() && file.isDirectory()) {
            docLength = file.list().length;
        }
        if (docLength > 0) {
            for (int i = 0; i < 1; i++) {
                String docFile = documentDirectory.toString() + "/cleaned/" + i;
                try {
                    List<String> doc = Files.readAllLines(Paths.get(docFile));
                    System.out.println(doc.get(2));
                } catch (IOException ioe) {
                    System.out.println("IOException!");
                }
            }
        }


    }
}
