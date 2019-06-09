package edu.uci.ics.cs221.index.ranking;

import edu.uci.ics.cs221.analysis.ComposableAnalyzer;
import edu.uci.ics.cs221.analysis.PorterStemmer;
import edu.uci.ics.cs221.analysis.PunctuationTokenizer;
import edu.uci.ics.cs221.index.inverted.DeltaVarLenCompressor;
import edu.uci.ics.cs221.index.inverted.InvertedIndexManager;
import edu.uci.ics.cs221.index.inverted.Pair;
import edu.uci.ics.cs221.storage.Document;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Team17searchTfIdfTest {

    private String path = "./index/Team17searchTfIdfTest";
    private static InvertedIndexManager iim;
    private static  List<String> documents;


    @After
    public void cleanUp (){
        InvertedIndexManager.DEFAULT_FLUSH_THRESHOLD = 1000;
        File dir = new File("./index/Team17searchTfIdfTest");
        for (File file: dir.listFiles()){
            if (!file.isDirectory()){
                file.delete();
            }
        }
        dir.delete();
    }

    /**
     * Add some documents and check if all the documents are retrieved for a search phrase that is present.
     * */
    @Test
    public void test1(){
        //Initialize the iim object
        iim = InvertedIndexManager.createOrOpenPositional(
                path,
                new ComposableAnalyzer(new PunctuationTokenizer(), new PorterStemmer()),
                new DeltaVarLenCompressor()
        );

        //Create and add documents
        Document doc0 = new Document("The purpose of life is a life with purpose");
        Document doc1 = new Document("The purpose of life is to code");
        Document doc2 = new Document("The purpose of life is to eat good food");
        Document doc3 = new Document("The purpose of life is to play counter strike");
        Document doc4 = new Document("The purpose of life is to play football");
        Document doc5 = new Document("The purpose of life is to sleep");

        iim.addDocument(doc0);
        iim.addDocument(doc1);
        iim.addDocument(doc2);
        iim.addDocument(doc3);
        iim.addDocument(doc4);
        iim.addDocument(doc5);
        iim.flush();

        List<String> searchKeyword = new ArrayList<>(Arrays.asList("life"));

        Iterator<Pair<Document, Double>> res = iim.searchTfIdf(searchKeyword, 6);

        int count = 0;

        while(res.hasNext()){
            res.next();
            count ++;
        }

        assertEquals(6, count);

    }


    /**
     * Search for a non existing keyword.
     * */
    @Test
    public void test3(){
        //Initialize the iim object
        iim = InvertedIndexManager.createOrOpenPositional(
                path,
                new ComposableAnalyzer(new PunctuationTokenizer(), new PorterStemmer()),
                new DeltaVarLenCompressor()
        );

        //Create and add documents
        Document doc0 = new Document("The purpose of life is a life with purpose");
        Document doc1 = new Document("The purpose of life is to code");
        Document doc2 = new Document("The purpose of life is to eat good food");
        Document doc3 = new Document("The purpose of life is to play counter strike");
        Document doc4 = new Document("The purpose of life is to play football");
        Document doc5 = new Document("The purpose of life is to sleep");

        iim.addDocument(doc0);
        iim.addDocument(doc1);
        iim.addDocument(doc2);
        iim.addDocument(doc3);
        iim.addDocument(doc4);
        iim.addDocument(doc5);
        iim.flush();

        List<String> searchKeyword = new ArrayList<>(Arrays.asList("JCBKiKhudai"));

        Iterator<Pair<Document, Double>> res = iim.searchTfIdf(searchKeyword, 3);

        assertTrue(!res.hasNext());
    }

}
