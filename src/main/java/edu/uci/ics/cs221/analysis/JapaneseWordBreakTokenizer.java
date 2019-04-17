package edu.uci.ics.cs221.analysis;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class JapaneseWordBreakTokenizer implements Tokenizer {
    Map<String, String> dict;

    public JapaneseWordBreakTokenizer() {
        try {
            // load the dictionary corpus, dictionary source: https://namakajiri.net/data/wikipedia-20150422-lemmas.tsv
            URL dictResource = WordBreakTokenizer.class.getClassLoader().getResource("wikipedia-20150422-lemmas.tsv");
            List<String> dictLines = Files.readAllLines(Paths.get(dictResource.toURI()));
            this.dict = new HashMap<>();
            for(String word : dictLines){
                word = word.trim();
                this.dict.put(word.split("\\s+")[2], word.split("\\s+")[1]);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> tokenize(String text) {
        text = text.toLowerCase();
        List<String> list = new ArrayList<>();

        // return empty list if text is empty
        if(text.length() == 0){
            return list;
        }

        // break the string based on dynamic programming
        list= wordBreak(text);

        // throw RuntimeException if text cannot be break
        if(list.isEmpty()){
            throw new RuntimeException("There's no possible way to break the string.");
        }

        // choose one word break String with highest probability
        String wordBreak = maxProbWordBreak(list);

        // convert String to list
        List<String> result = new ArrayList<>();
        String[] array = wordBreak.split(" ");
        for(String word : array){
            result.add(word);
        }

        // remove all stop words from result list
        StopWords stopWordSet = new StopWords();
        result.removeAll(stopWordSet.stopWords);
//        System.out.println(result);
        return result;
    }

    public List<String> wordBreak(String text){
        List<String> strList = new ArrayList<>();
        int len = text.length();
        for(int num = len - 1; num >= 0; num--){
            if(this.dict.containsKey(text.substring(num)))
                break;
            else{
                if(num == 0)
                    return strList;
            }
        }
        for(int i = 0; i < len-1; i++){
            if(this.dict.containsKey(text.substring(0,i+1)))
            {
                List<String> words = wordBreak(text.substring(i+1,len));
                if(words.size() != 0)
                    for(Iterator<String> iterator = words.iterator();iterator.hasNext();)
                    {
                        strList.add(text.substring(0,i+1)+" "+iterator.next());
                    }
            }
        }
        if(this.dict.containsKey(text)){
            strList.add(text);
        }
        return strList;
    }

    public String maxProbWordBreak(List<String> s){
        String match = ""; Double maxFreq = 0.0;
        // to sum up frequencies of all words in dictionary
        Double freqSum = 0.0;
        for(String word : this.dict.keySet()){
            freqSum = freqSum + Double.valueOf(this.dict.get(word));
        }

        // choose one word break String with highest probability
        for(int i = 0; i<s.size(); i++){
            String[] arr = s.get(i).split("\\s+");
            if(arr.length>0){
                Double currFreq = 1.0;
                for(String word : arr){
                    currFreq = (currFreq * Double.valueOf(this.dict.get(word)))/freqSum;
                }
                Double freq = Math.log(currFreq);

                if(maxFreq == 0.0 || maxFreq < freq){
                    maxFreq = freq;
                    match = s.get(i);
                }
            }
        }
        return match;
    }
}
