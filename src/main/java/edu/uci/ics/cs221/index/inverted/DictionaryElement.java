package edu.uci.ics.cs221.index.inverted;

public class DictionaryElement {
    int offset;
    int length;
    int pageNum;

    public DictionaryElement(int offset, int length, int pageNum){
        this.offset = offset;
        this.length = length;
        this.pageNum = pageNum;
    }

    public int getOffset(){
        return this.offset;
    }

    public int getLength(){
        return this.length;
    }

    public int getPageNum(){
        return this.pageNum;
    }
}
