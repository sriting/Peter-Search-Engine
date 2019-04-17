package edu.uci.ics.cs221.analysis.wordbreak;

import edu.uci.ics.cs221.analysis.JapaneseWordBreakTokenizer;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class JapaneseWordBreakTokenizerTest {

    @Test
    public void test1() {
        String text = "英語を学ぶ";
        List<String> expected = Arrays.asList("英語", "を", "学ぶ");

        JapaneseWordBreakTokenizer tokenizer = new JapaneseWordBreakTokenizer();

        assertEquals(expected, tokenizer.tokenize(text));

    }

    @Test
    public void test2(){
        String text = "箸を食べる";
        List<String> expected = Arrays.asList("箸", "を", "食べる");

        JapaneseWordBreakTokenizer tokenizer = new JapaneseWordBreakTokenizer();

        assertEquals(expected, tokenizer.tokenize(text));
    }


    @Test
    public void test3(){
        String text = "猫を養う";
        List<String> expected = Arrays.asList("猫", "を", "養う");

        JapaneseWordBreakTokenizer tokenizer = new JapaneseWordBreakTokenizer();

        assertEquals(expected, tokenizer.tokenize(text));
    }
}
