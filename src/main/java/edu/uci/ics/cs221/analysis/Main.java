package edu.uci.ics.cs221.analysis;

public class Main {
    public static void main(String[] args){
        /*
        Project 1
         */
        PunctuationTokenizer punctuatetokenizer = new PunctuationTokenizer();
        punctuatetokenizer.tokenize("I am Happy Today! What about you, your friends Yidan?");

        WordBreakTokenizer wordbreaktokenizer = new WordBreakTokenizer();
        wordbreaktokenizer.tokenize("IlOveSAnFrancIsCo");
        wordbreaktokenizer.tokenize("FindthelongestpalindromicstringYoumayassumethatthemaximumlengthisonehundred");

        JapaneseWordBreakTokenizer jpnwordbreaktokenizer = new JapaneseWordBreakTokenizer();
        jpnwordbreaktokenizer.tokenize("英語を学ぶ");
        jpnwordbreaktokenizer.tokenize("箸を食べる");
        jpnwordbreaktokenizer.tokenize("猫を養う");
    }
}
