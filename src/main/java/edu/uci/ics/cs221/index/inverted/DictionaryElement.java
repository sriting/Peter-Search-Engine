package edu.uci.ics.cs221.index.inverted;

public class DictionaryElement {
    int offset;
    int docIDListlength;
    int pageNum;
    int positionListOffset;
    int positionOffsetListLength;

    public DictionaryElement(int offset, int docIDListlength, int pageNum, int positionOffset, int positionOffsetListLength){
        this.offset = offset;
        this.docIDListlength = docIDListlength;
        this.pageNum = pageNum;
        this.positionListOffset = positionOffset;
        this.positionOffsetListLength = positionOffsetListLength;
    }

    public int getOffset(){
        return this.offset;
    }

    public int getdocIDListLength(){
        return this.docIDListlength;
    }

    public int getPageNum(){
        return this.pageNum;
    }

    public int getPositionListOffset(){ return this.positionListOffset;}

    public int getPositionOffsetListLength(){
        return this.positionOffsetListLength;
    }
}
