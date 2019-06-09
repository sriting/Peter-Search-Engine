package edu.uci.ics.cs221.search;

import edu.uci.ics.cs221.index.inverted.InvertedIndexManager;
import edu.uci.ics.cs221.index.inverted.Pair;
import edu.uci.ics.cs221.storage.Document;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class IcsSearchEngine {
    public Path documentDirectory;
    public InvertedIndexManager indexManager;
    public int docLen = 0;
    public List<Pair<Integer, Double>> PRScores = new ArrayList<>();


    /**
     * Initializes an IcsSearchEngine from the directory containing the documents and the
     *
     */
    public static IcsSearchEngine createSearchEngine(Path documentDirectory, InvertedIndexManager indexManager) {
        return new IcsSearchEngine(documentDirectory, indexManager);
    }

    private IcsSearchEngine(Path documentDirectory, InvertedIndexManager indexManager) {
        this.documentDirectory = documentDirectory;
        this.indexManager = indexManager;
    }

    /**
     * Writes all ICS web page documents in the document directory to the inverted index.
     */
    public void writeIndex() {
        File file = new File(documentDirectory.toString() + "/cleaned/");
        int docLength = 0;
        if (file.exists() && file.isDirectory()) {
            docLength = file.list().length;
            docLen = docLength;
        }
        if(docLength > 0){
            for (int i = 0; i < docLength; i++){
                String docFile = documentDirectory.toString() + "/cleaned/" + i;
                try {
                    List<String> doc = Files.readAllLines(Paths.get(docFile));
                    String content = "";
                    for (String str : doc){
                        content += (str + "\n");
                    }
                    indexManager.addDocument(new Document(content));
                } catch (IOException ioe) {
                    System.out.println("IOException in Doc "+i);
                }
            }
        }
    }

    /**
     * Computes the page rank score from the "id-graph.tsv" file in the document directory.
     * The results of the computation can be saved in a class variable and will be later retrieved by `getPageRankScores`.
     */
    public void computePageRank(int numIterations) {
        if (docLen == 0) {
            File file = new File(documentDirectory.toString() + "/cleaned/");
            if (file.exists() && file.isDirectory()) {
                docLen = file.list().length;
            }
        }
        String graphFile = documentDirectory.toString() + "/id-graph.tsv";
        double factor = 0.85;
        try {
            List<String> graphStr = Files.readAllLines(Paths.get(graphFile));
            // A double array to store PR scores for all documents. Initially, all PR are 1.
            double[] PR = new double[docLen];
            // A int array to store the number of outgoing links of Pi
            int[] outgo = new int[docLen];
            for (int p = 0; p < docLen; p++){
                PR[p] = 1.0; outgo[p] = 0;
            }
            // A Map to store all incoming links of Pi
            Map<Integer, List<Integer>> income = new HashMap<>();

            // get links info from file
            for(int i = 0; i < graphStr.size(); i++){
                String[] link = graphStr.get(i).trim().split("\\s+");
                int start = Integer.parseInt(link[0]);
                int end = Integer.parseInt(link[1]);
                if(income.containsKey(end)){
                    income.get(end).add(start);
                }else{
                    income.put(end, new ArrayList<>(Arrays.asList(start)));
                }
                outgo[start] += 1;
            }

            // compute PR scores in n times of iterations
            for(int n = 0; n < numIterations; n++){
                for(int m = 0; m < docLen; m++){
                    double incomeSum = 0.0;
                    List<Integer> incomeLinks = income.get(m);
                    if (incomeLinks != null){
                        for(int l = 0; l < incomeLinks.size(); l++){
                            int docID = incomeLinks.get(l);
                            incomeSum += (PR[docID] / outgo[docID]);
                        }
                    }
                    PR[m] = 1 - factor + factor*incomeSum;
                }
            }

            // store PR stores
            for(int m = 0; m < docLen; m++){
                PRScores.add(new Pair<>(m, PR[m]));
            }
        } catch (IOException ioe) {
            System.out.println("IOException!");
        }
    }

    /**
     * Gets the page rank score of all documents previously computed. Must be called after `computePageRank`.
     * Returns an list of <DocumentID - Score> Pairs that is sorted by score in descending order (high scores first).
     */
    public List<Pair<Integer, Double>> getPageRankScores() {
        // bubble sort by score in descending order
        Pair<Integer, Double> temp;
        int size = PRScores.size();
        for(int i = 0; i < size; i++) {
            for(int j = 0; j <size - i - 1; j++) {
                if(PRScores.get(j).getRight() < PRScores.get(j+1).getRight()) {
                    temp = PRScores.get(j);
                    PRScores.set(j, PRScores.get(j+1));
                    PRScores.set(j + 1, temp);
                }
            }
        }
        return PRScores;
    }

    /**
     * Searches the ICS document corpus and returns the top K documents ranked by combining TF-IDF and PageRank.
     *
     * The search process should first retrieve ALL the top documents from the InvertedIndex by TF-IDF rank,
     * by calling `searchTfIdf(query, null)`.
     *
     * Then the corresponding PageRank score of each document should be retrieved. (`computePageRank` will be called beforehand)
     * For each document, the combined score is  tfIdfScore + pageRankWeight * pageRankScore.
     *
     * Finally, the top K documents of the combined score are returned. Each element is a pair of <Document, combinedScore>
     *
     *
     * Note: We could get the Document ID by reading the first line of the document.
     * This is a workaround because our project doesn't support multiple fields. We cannot keep the documentID in a separate column.
     */
    public Iterator<Pair<Document, Double>> searchQuery(List<String> query, int topK, double pageRankWeight) {
        Iterator<Pair<Document, Double>> TFIDF = indexManager.searchTfIdf(query, topK);
        PriorityQueue<Double> scoreQueue = new PriorityQueue<>(11, new Comparator<Double>() {
            @Override
            public int compare(Double d1, Double d2) {
                return d2.compareTo(d1);
            }
        });
        List<Pair<Document, Double>> possibleDoc = new ArrayList<>();

        while(TFIDF.hasNext()){
            Pair<Document, Double> curr = TFIDF.next();
            int docID = Integer.parseInt(curr.getLeft().getText().split("\\n")[0]);
            double currTFIDF = curr.getRight();
            Pair<Integer, Double> currPair = PRScores.get(docID);
            if(currPair.getLeft() != docID){
                System.out.println("DocID is incorret: "+docID);
            }
            double currPR = currPair.getRight();
            double combine = currTFIDF + pageRankWeight * currPR;
            possibleDoc.add(new Pair(curr.getLeft(), combine));
            scoreQueue.add(combine);
        }

        List<Pair<Document, Double>> result = new ArrayList<>();
        int pollLen = scoreQueue.size();
        // take top-k elements if the size of queue > k
        if (pollLen > topK) {
            pollLen = topK;
        }
        for (int k = 0; k < pollLen; k++) {
            Double score = scoreQueue.poll();
            for (int j = 0; j < possibleDoc.size(); j++) {
                if (possibleDoc.get(j).getRight().equals(score)) {
                    result.add(new Pair<>(possibleDoc.get(j).getLeft(), score));
                    possibleDoc.remove(j);
                    break;
                }
            }
        }
        return result.iterator();
    }

}
