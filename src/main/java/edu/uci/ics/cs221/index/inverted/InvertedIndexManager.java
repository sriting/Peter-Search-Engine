package edu.uci.ics.cs221.index.inverted;

import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import edu.uci.ics.cs221.analysis.Analyzer;
import edu.uci.ics.cs221.storage.Document;
import edu.uci.ics.cs221.storage.DocumentStore;
import edu.uci.ics.cs221.storage.MapdbDocStore;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * This class manages an disk-based inverted index and all the documents in the inverted index.
 *
 * Please refer to the project 2 wiki page for implementation guidelines.
 */
public class InvertedIndexManager {

    /**
     * The default flush threshold, in terms of number of documents.
     * For example, a new Segment should be automatically created whenever there's 1000 documents in the buffer.
     *
     * In test cases, the default flush threshold could possibly be set to any number.
     */
    public static int DEFAULT_FLUSH_THRESHOLD = 1000;

    /**
     * The default merge threshold, in terms of number of segments in the inverted index.
     * When the number of segments reaches the threshold, a merge should be automatically triggered.
     *
     * In test cases, the default merge threshold could possibly be set to any number.
     */
    public static int DEFAULT_MERGE_THRESHOLD = 8;

    public Analyzer analyzer;
    public String indexFolder;
    public Compressor compressor;
    public InvertedIndexList indexList = new InvertedIndexList();
    public PositionList positionList = new PositionList();
    private int docID = 0;
    public int segmentCount = 0;
    private int dicBufferLen = 0;
    private int mergeBufferLen = 0;

    // a structure to store all in-memory document;
    public Map<Integer, Document> inMemDocStore = new HashMap<>();


    private InvertedIndexManager(String indexFolder, Analyzer analyzer) {
        this.analyzer = analyzer;
        this.indexFolder = indexFolder;
        this.compressor = new NaiveCompressor();
    }

    private InvertedIndexManager(String indexFolder, Analyzer analyzer, Compressor compressor) {
        this.analyzer = analyzer;
        this.indexFolder = indexFolder;
        this.compressor = compressor;
    }

    /**
     * Creates an inverted index manager with the folder and an analyzer
     */
    public static InvertedIndexManager createOrOpen(String indexFolder, Analyzer analyzer) {
        try {
            indexFolder = indexFolder + "/";
            Path indexFolderPath = Paths.get(indexFolder).resolve("");
            if (Files.exists(indexFolderPath) && Files.isDirectory(indexFolderPath)) {
                if (Files.isDirectory(indexFolderPath)) {
                    return new InvertedIndexManager(indexFolder, analyzer);
                } else {
                    throw new RuntimeException(indexFolderPath + " already exists and is not a directory");
                }
            } else {
                Files.createDirectories(indexFolderPath);
                return new InvertedIndexManager(indexFolder, analyzer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates a positional index with the given folder, analyzer, and the compressor.
     * Compressor must be used to compress the inverted lists and the position lists.
     *
     */
    public static InvertedIndexManager createOrOpenPositional(String indexFolder, Analyzer analyzer, Compressor compressor) {
        try {
            indexFolder = indexFolder + "/";
            Path indexFolderPath = Paths.get(indexFolder).resolve("");
            if (Files.exists(indexFolderPath) && Files.isDirectory(indexFolderPath)) {
                if (Files.isDirectory(indexFolderPath)) {
                    return new InvertedIndexManager(indexFolder, analyzer, compressor);
                } else {
                    throw new RuntimeException(indexFolderPath + " already exists and is not a directory");
                }
            } else {
                Files.createDirectories(indexFolderPath);
                return new InvertedIndexManager(indexFolder, analyzer, compressor);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Adds a document to the inverted index.
     * Document should live in a in-memory buffer until `flush()` is called to write the segment to disk.
     * @param document
     */
    public void addDocument(Document document) {
        // stem words and delete stop words
        List<String> wordList = analyzer.analyze(document.getText());
        // add all words into inverted index list with current docID
        for(int i=0; i<wordList.size(); i++){
            // add docID into inverted index list
            indexList.addDocID(wordList.get(i), docID);
            // add the positional index into position list
            positionList.addPosition(wordList.get(i), docID, i);
            // add length into dictionary buffer size
            dicBufferLen += wordList.get(i).getBytes(StandardCharsets.UTF_8).length + 28;
        }
        // add this Document into in-memory document store
        inMemDocStore.put(docID, document);
        // add docID for next Document
        docID++;
        // automatic flush when number of document reaches threshold
        if(inMemDocStore.size() == DEFAULT_FLUSH_THRESHOLD){
            flush();
        }

    }

    /**
     * Flushes all the documents in the in-memory segment buffer to disk. If the buffer is empty, it should not do anything.
     * flush() writes the segment to disk containing the posting list and the corresponding document store.
     */
    public void flush() {
        String dictFile = indexFolder+ "Dictionary" + segmentCount + ".txt";
        String listFile = indexFolder + "InvertedList" + segmentCount + ".txt";
        String storeFile = indexFolder+ "MapdbDocStore"+ segmentCount +".db";
        String positionFile = indexFolder + "PositionList" + segmentCount + ".txt";

        if(inMemDocStore.size()>0) {
            DocumentStore documentStore = MapdbDocStore.createOrOpen(storeFile);
            PageFileChannel dictChannel = PageFileChannel.createOrOpen(Paths.get(dictFile));
            PageFileChannel listChannel = PageFileChannel.createOrOpen(Paths.get(listFile));
            PageFileChannel positionChannel = PageFileChannel.createOrOpen(Paths.get(positionFile));

            writeDocStore(documentStore,inMemDocStore);

            writeDictInvertedPosition(dictChannel,listChannel,positionChannel,positionList,indexList,dicBufferLen);

            documentStore.close();
            dictChannel.close();
            listChannel.close();
            positionChannel.close();

            //clear invertedIndexList, positionList and in-memory document store
            indexList.deleteAll();
            positionList.deleteAll();
            inMemDocStore.clear();

            segmentCount++;
            // automatic merge when number of segments reaches threshold
            if (segmentCount >= DEFAULT_MERGE_THRESHOLD) {
                mergeAllSegments();
            }
        }
        docID=0;
    }
    /**
     * write document Store into this segment.
     */
    public void writeDocStore(DocumentStore documentStore, Map<Integer, Document> MemDocStore){
        // write all documents from in-memory document store into document database
    Iterator it = MemDocStore.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Document> entry = (Map.Entry) it.next();
            documentStore.addDocument(entry.getKey(), entry.getValue());
        }
    }

    /**
     * write dictionary, inverted list and positional list into this segment.
     */
    public void writeDictInvertedPosition(PageFileChannel dictChannel, PageFileChannel listChannel, PageFileChannel positionChannel, PositionList positionList, InvertedIndexList invertedList, int dicLen){
        // write inverted list file
        Iterator iterator = invertedList.indexList.entrySet().iterator();
        ByteBuffer dictByteBuffer = ByteBuffer.allocate(dicLen);
        ByteBuffer listByteBuffer = ByteBuffer.allocate(PageFileChannel.PAGE_SIZE);
        ByteBuffer posiByteBuffer = ByteBuffer.allocate(PageFileChannel.PAGE_SIZE);
        int positionLength = 0;
        if(dicLen == dicBufferLen){
            dicBufferLen = 0;
        }
        if(compressor == null){
            throw new UnsupportedOperationException();
        }
        int offset = 0; int positionListOffset;
        int listPageNum = 0;
        while (iterator.hasNext()) {
            Map.Entry<String, List<Integer>> entry = (Map.Entry) iterator.next();
            String str = entry.getKey();
            List<Integer> list = entry.getValue();
            // positionOffsetList records the offset (the tail index) of each position list
            List<Integer> positionOffsetList = new ArrayList<>();

            // encode the inverted list of position index list
            byte[] invertedArr = compressor.encode(list);

            // add each position list of each docID for this keyword into position list file
            positionListOffset = positionLength;

            // records the numbers of position list of this keyword in current docID
            List<Integer> positionNumLen = new ArrayList<>();

            for (int i = 0; i < list.size(); i++){
                byte[] positionArr = compressor.encode(positionList.getPositionList(str, list.get(i)));
                positionNumLen.add(positionList.getPositionList(str, list.get(i)).size());

                if(posiByteBuffer.remaining() < positionArr.length){
                    int start = 0;
                    while(posiByteBuffer.remaining() < (positionArr.length-start)){
                        int remainLen = posiByteBuffer.remaining();
                        byte[] first = Arrays.copyOfRange(positionArr, start, start + remainLen);
                        posiByteBuffer.put(first);
                        positionChannel.appendPage(posiByteBuffer);
                        posiByteBuffer.clear();
                        start += remainLen;
                    }
                    byte[] second = Arrays.copyOfRange(positionArr, start, positionArr.length);
                    posiByteBuffer.put(second);
                }else{
                    posiByteBuffer.put(positionArr);
                }
                positionLength += positionArr.length;
                positionOffsetList.add(positionLength);
            }

            // encode the offset list of position list and the numbers of position list of all terms
            byte[] positionOffsetArr = compressor.encode(positionOffsetList);
            Compressor naiveCompressor = new NaiveCompressor();
            byte[] positionNumLenArr = naiveCompressor.encode(positionNumLen);

            // add one keyword into dictionary
            byte[] keyword = str.getBytes(StandardCharsets.UTF_8);
            int wordLen = keyword.length;
            if (!listByteBuffer.hasRemaining()) {
                listPageNum++;offset=0;
                listChannel.appendPage(listByteBuffer);
                listByteBuffer.clear();
            }
            // write dictionary file with format : wordLen | keyword | docIDList offset | docIDList length | page num | positionList offset | offsetList length | positionNumList length
            dictByteBuffer.putInt(wordLen);
            dictByteBuffer.put(keyword);
            dictByteBuffer.putInt(offset);
            dictByteBuffer.putInt(invertedArr.length);
            dictByteBuffer.putInt(listPageNum);
            dictByteBuffer.putInt(positionListOffset);
            dictByteBuffer.putInt(positionOffsetArr.length);
            dictByteBuffer.putInt(positionNumLenArr.length);

            // add one inverted list into inverted list file
            if (listByteBuffer.remaining() >= invertedArr.length){
                listByteBuffer.put(invertedArr);
                offset += invertedArr.length;
            }else{
                int stored = 0;
                do{
                    int remainLen = listByteBuffer.remaining();
                    byte[] temp = Arrays.copyOfRange(invertedArr, stored, remainLen+stored);
                    listByteBuffer.put(temp);
                    stored += remainLen;
                    listChannel.appendPage(listByteBuffer);
                    listByteBuffer.clear(); listPageNum++; offset = 0;
                }while(listByteBuffer.remaining() < invertedArr.length-stored);
                if(stored < invertedArr.length) {
                    byte[] remain = Arrays.copyOfRange(invertedArr, stored, invertedArr.length);
                    listByteBuffer.put(remain);
                    offset += (invertedArr.length - stored);
                }
            }

            // add one offset list of position index list into inverted list file
            if (listByteBuffer.remaining() >= positionOffsetArr.length){
                listByteBuffer.put(positionOffsetArr);
                offset += positionOffsetArr.length;
            }else{
                int stored = 0;
                byte[] combine = new byte[positionOffsetArr.length];
                do{
                    int remainLen = listByteBuffer.remaining();
                    byte[] temp = Arrays.copyOfRange(positionOffsetArr,stored,stored+remainLen);
                    System.arraycopy(temp,0,combine,stored,temp.length);
                    listByteBuffer.put(temp);
                    stored += remainLen;
                    listChannel.appendPage(listByteBuffer);
                    listByteBuffer.clear(); listPageNum++; offset= 0;
                }while(listByteBuffer.remaining() < positionOffsetArr.length-stored);
                if(stored < positionOffsetArr.length) {
                    byte[] temp = Arrays.copyOfRange(positionOffsetArr,stored, positionOffsetArr.length);
                    System.arraycopy(temp,0,combine,stored,temp.length);
                    listByteBuffer.put(temp);
                    offset += (positionOffsetArr.length - stored);
                }
            }

            // add one numbers list of position index list into inverted list file
            if (listByteBuffer.remaining() >= positionNumLenArr.length) {
                listByteBuffer.put(positionNumLenArr);
                offset += positionNumLenArr.length;
            } else {
                int stored = 0;
                byte[] combine = new byte[positionNumLenArr.length];
                do {
                    int remainLen = listByteBuffer.remaining();
                    byte[] temp = Arrays.copyOfRange(positionNumLenArr, stored, stored + remainLen);
                    System.arraycopy(temp, 0, combine, stored, temp.length);
                    listByteBuffer.put(temp);
                    stored += remainLen;
                    listChannel.appendPage(listByteBuffer);
                    listByteBuffer.clear();
                    listPageNum++;
                    offset = 0;
                } while (listByteBuffer.remaining() < positionNumLenArr.length - stored);
                if (stored < positionNumLenArr.length) {
                    byte[] temp = Arrays.copyOfRange(positionNumLenArr, stored, positionNumLenArr.length);
                    System.arraycopy(temp, 0, combine, stored, temp.length);
                    listByteBuffer.put(temp);
                    offset += (positionNumLenArr.length - stored);
                }
            }
        }
        dictChannel.appendAllBytes(dictByteBuffer);

        // append the last page of list
        if (listByteBuffer.position() > 0) {
            appendLastPage(listByteBuffer, listChannel);
        }
        if (posiByteBuffer.position() > 0){
            appendLastPage(posiByteBuffer, positionChannel);
        }
    }


    /**
     * append the last page of ByteBuffer into this segment.
     */
    public void appendLastPage(ByteBuffer byteBuffer, PageFileChannel channel){
        byte zero = 0;
        for(int i=byteBuffer.position(); i< PageFileChannel.PAGE_SIZE; i++){
            byteBuffer.put(zero);
        }
        channel.appendPage(byteBuffer);
        byteBuffer.clear();
    }


    /**
     * Merges all the disk segments of the inverted index pair-wise.
     */
    public void mergeAllSegments() {
        Preconditions.checkArgument(getNumSegments() % 2 == 0);
        for(int i = 0; i < segmentCount/2; i++){
            String dictFile1 = indexFolder+ "Dictionary" + (i*2) + ".txt";
            String listFile1 = indexFolder + "InvertedList" + (i*2) + ".txt";
            String docFile1 = indexFolder+ "MapdbDocStore"+ (i*2) +".db";
            String posiFile1 = indexFolder + "PositionList" + (i*2) + ".txt";
            DocumentStore documentStore1 = MapdbDocStore.createOrOpen(docFile1);
            PageFileChannel dictChannel1 = PageFileChannel.createOrOpen(Paths.get(dictFile1));
            PageFileChannel listChannel1 = PageFileChannel.createOrOpen(Paths.get(listFile1));
            PageFileChannel posiChannel1 = PageFileChannel.createOrOpen(Paths.get(posiFile1));

            String dictFile2 = indexFolder+ "Dictionary" + (i*2+1) + ".txt";
            String listFile2 = indexFolder + "InvertedList" + (i*2+1) + ".txt";
            String docFile2 = indexFolder+ "MapdbDocStore"+ (i*2+1) +".db";
            String posiFile2 = indexFolder + "PositionList" + (i*2+1) + ".txt";
            DocumentStore documentStore2 = MapdbDocStore.createOrOpen(docFile2);
            PageFileChannel dictChannel2 = PageFileChannel.createOrOpen(Paths.get(dictFile2));
            PageFileChannel listChannel2 = PageFileChannel.createOrOpen(Paths.get(listFile2));
            PageFileChannel posiChannel2 = PageFileChannel.createOrOpen(Paths.get(posiFile2));

            // merge documents of documentStore2 into documentStore1
            int doc1Size = (int)documentStore1.size();
            for(int k = 0; k < documentStore2.size(); k++){
                documentStore1.addDocument(doc1Size+k,documentStore2.getDocument(k));
            }

            // merge inverted list of invertedIndexList2 into invertedIndexList1
            String dictFile3 = indexFolder+ "Dictionary" + (DEFAULT_MERGE_THRESHOLD+i) + ".txt";
            String listFile3 = indexFolder + "InvertedList" + (DEFAULT_MERGE_THRESHOLD+i) + ".txt";
            String posiFile3 = indexFolder + "PositionList" + (DEFAULT_MERGE_THRESHOLD+i) + ".txt";
            PageFileChannel dictChannel3 = PageFileChannel.createOrOpen(Paths.get(dictFile3));
            PageFileChannel listChannel3 = PageFileChannel.createOrOpen(Paths.get(listFile3));
            PageFileChannel posiChannel3 = PageFileChannel.createOrOpen(Paths.get(posiFile3));
            InvertedIndexList invertedList = new InvertedIndexList();
            PositionList positionalList = new PositionList();
            Map<String, DictionaryElement> dictionary1 = readDictionary(dictChannel1);
            Map<String, DictionaryElement> dictionary2 = readDictionary(dictChannel2);
            Iterator it1 = dictionary1.entrySet().iterator();
            while(it1.hasNext()){
                Map.Entry<String, DictionaryElement> entry = (Map.Entry)it1.next();
                DictionaryElement dictEle = entry.getValue();
                List<Integer> invList = readInvertedList(listChannel1,dictEle);
                List<Integer> posOffsetList = readPositionOffsetList(listChannel1,dictEle);
                Map<Integer, List<Integer>> positionMap = readPositionIndexList(posiChannel1,dictEle.getPositionListOffset(),entry.getKey(),invList,posOffsetList);
                positionalList.addPositionLists(entry.getKey(),positionMap);
                invertedList.addList(entry.getKey(),invList);
            }
            Iterator it2 = dictionary2.entrySet().iterator();
            while(it2.hasNext()){
                Map.Entry<String, DictionaryElement> entry = (Map.Entry)it2.next();
                DictionaryElement dictEle = entry.getValue();
                List<Integer> invList = readInvertedList(listChannel2,dictEle);
                List<Integer> posOffsetList = readPositionOffsetList(listChannel2,dictEle);
                Map<Integer, List<Integer>> positionMap = readPositionIndexList(posiChannel2,dictEle.getPositionListOffset(),entry.getKey(),invList,posOffsetList);
                Map<Integer, List<Integer>> newPositionMap = new HashMap<>();
                // shift docID of all elements in inverted List and position Map
                for(int j=0; j<invList.size();j++){
                    invList.set(j,doc1Size+invList.get(j));
                }
                for(Map.Entry<Integer, List<Integer>> position : positionMap.entrySet()){
                    newPositionMap.put((doc1Size+position.getKey()), position.getValue());
                }
                positionalList.addPositionLists(entry.getKey(),newPositionMap);
                invertedList.addList(entry.getKey(),invList);
            }
            writeDictInvertedPosition(dictChannel3, listChannel3, posiChannel3, positionalList, invertedList, mergeBufferLen);
            mergeBufferLen = 0;

            documentStore1.close();documentStore2.close();
            dictChannel1.close();dictChannel2.close();
            listChannel1.close();listChannel2.close();

            // delete files
            File dict1fileAddr = new File(dictFile1);
            dict1fileAddr.delete();
            File list1fileAddr = new File(listFile1);
            list1fileAddr.delete();
            File dict2fileAddr = new File(dictFile2);
            dict2fileAddr.delete();
            File list2fileAddr = new File(listFile2);
            list2fileAddr.delete();
            File doc2fileAddr = new File(docFile2);
            doc2fileAddr.delete();
            File posi1fileAddr = new File(posiFile1);
            posi1fileAddr.delete();
            File posi2fileAddr = new File(posiFile2);
            posi2fileAddr.delete();

            // rename files of the merged segment
            File dictFileName = new File(dictFile3);
            dictFileName.renameTo(new File(indexFolder+ "Dictionary" + i + ".txt"));
            File listFileName = new File(listFile3);
            listFileName.renameTo(new File(indexFolder + "InvertedList" + i + ".txt"));
            File posiFileNmae = new File(posiFile3);
            posiFileNmae.renameTo(new File(indexFolder + "PositionList" + i + ".txt"));
            File docFileName = new File(docFile1);
            docFileName.renameTo(new File(indexFolder+ "MapdbDocStore"+ i +".db"));
        }
        segmentCount = segmentCount/2;
    }

    /**
     * get the dictionary from this segment
     */
    public Map<String, DictionaryElement> readDictionary(PageFileChannel dictChannel){
        Map<String, DictionaryElement> dictList = new HashMap<>();
        ByteBuffer dictBuffer = dictChannel.readAllPages();
        byte[] dictionary = dictBuffer.array();
        int offsetLen = 0; mergeBufferLen += dictionary.length;
        for(int i = 0; i<dictionary.length; i+=offsetLen) {
            // get keyword and its information from dictionary
            byte[] wordLenArr = Arrays.copyOfRange(dictionary,i, i+4);
            int wordLen = ByteBuffer.wrap(wordLenArr).getInt();
            offsetLen = wordLen + 28;
            byte[] keywordArr = Arrays.copyOfRange(dictionary,i+4, i+4+wordLen);
            String keyword = new String(keywordArr, StandardCharsets.UTF_8);
            byte[] docIDListOffsetArr = Arrays.copyOfRange(dictionary,i+4+wordLen, i+8+wordLen);
            int docIDListOffset = ByteBuffer.wrap(docIDListOffsetArr).getInt();
            byte[] lengthArr = Arrays.copyOfRange(dictionary,i+8+wordLen, i+12+wordLen);
            int length = ByteBuffer.wrap(lengthArr).getInt();
            byte[] pageNumArr = Arrays.copyOfRange(dictionary,i+12+wordLen, i+16+wordLen);
            int pageNum = ByteBuffer.wrap(pageNumArr).getInt();
            byte[] positionListOffsetArr = Arrays.copyOfRange(dictionary,i+16+wordLen, i+20+wordLen);
            int positionListOffset = ByteBuffer.wrap(positionListOffsetArr).getInt();
            byte[] offsetListLengthArr = Arrays.copyOfRange(dictionary,i+20+wordLen, i+24+wordLen);
            int offsetListLength = ByteBuffer.wrap(offsetListLengthArr).getInt();
            byte[] positionNumLenArr = Arrays.copyOfRange(dictionary, i + 24 + wordLen, i + 28 + wordLen);
            int posNumListLen = ByteBuffer.wrap(positionNumLenArr).getInt();

            // break the for loop if the list has not next keyword
            if (keyword.isEmpty()) {
                break;
            }

            // add element into dictionary map
            DictionaryElement dictElem = new DictionaryElement(docIDListOffset, length, pageNum, positionListOffset, offsetListLength, posNumListLen);

            dictList.put(keyword,dictElem);
        }
        return dictList;
    }

    /**
     * get the inverted list of the keyword from this segment
     */
    public List<Integer> readInvertedList(PageFileChannel listChannel, DictionaryElement dictElem){
        int pageNum = dictElem.getPageNum();
        int docIDListlength = dictElem.getdocIDListLength();
        int offset = dictElem.getOffset();

        ByteBuffer listBuffer = listChannel.readPage(pageNum);

        // get the inverted list of docID of the keyword
        byte[] docIDList = listBuffer.array();
        byte[] docIDArr = new byte[docIDListlength];
        int increasingSize;
        if(dictElem.getOffset()+docIDListlength > PageFileChannel.PAGE_SIZE){
            for(int i=0; i<docIDListlength; i+=increasingSize) {
                if(docIDListlength+offset-i > PageFileChannel.PAGE_SIZE){
                    byte[] docIDSubArr = Arrays.copyOfRange(docIDList, offset, PageFileChannel.PAGE_SIZE);
                    System.arraycopy(docIDSubArr, 0, docIDArr, i, docIDSubArr.length);
                    listBuffer.clear(); pageNum++; offset = 0;
                    listBuffer = listChannel.readPage(pageNum);
                    docIDList = listBuffer.array();
                    increasingSize = docIDSubArr.length;
                }else{
                    byte[] docIDSubArr = Arrays.copyOfRange(docIDList, offset, offset+docIDListlength-i);
                    System.arraycopy(docIDSubArr, 0, docIDArr, i, docIDSubArr.length);
                    increasingSize = docIDSubArr.length;
                }
            }
        }else{
            docIDArr = Arrays.copyOfRange(docIDList, offset, offset+docIDListlength);
        }

        return compressor.decode(docIDArr);
    }

    /**
     * get the offset list of the position list of the keyword from this segment
     */
    public List<Integer> readPositionOffsetList(PageFileChannel listChannel, DictionaryElement dictElem){
        int offset;
        int docIDListlength = dictElem.getdocIDListLength();
        int pageNum = dictElem.getPageNum();
        int posOffsetListLen = dictElem.getPositionOffsetListLength();

        offset = (dictElem.getOffset()+docIDListlength)% PageFileChannel.PAGE_SIZE;
        pageNum += ((dictElem.getOffset()+docIDListlength) / PageFileChannel.PAGE_SIZE);
        ByteBuffer listBuffer = listChannel.readPage(pageNum);

        // get the inverted list of docID of the keyword
        byte[] posOffsetList = listBuffer.array();
        byte[] posOffsetArr = new byte[posOffsetListLen];
        int increasingSize = 0;
        if(offset+posOffsetListLen > PageFileChannel.PAGE_SIZE){
            for(int i=0; i<posOffsetListLen; i+=increasingSize) {
                if(posOffsetListLen+offset-i > PageFileChannel.PAGE_SIZE){
                    byte[] posOffsetSubArr = Arrays.copyOfRange(posOffsetList, offset, PageFileChannel.PAGE_SIZE);
                    System.arraycopy(posOffsetSubArr, 0, posOffsetArr, i, posOffsetSubArr.length);
                    listBuffer.clear(); pageNum++;offset = 0;
                    listBuffer = listChannel.readPage(pageNum);
                    posOffsetList = listBuffer.array();
                    increasingSize = posOffsetSubArr.length;
                }else{
                    byte[] posOffsetSubArr = Arrays.copyOfRange(posOffsetList, offset, offset+posOffsetListLen-i);
                    System.arraycopy(posOffsetSubArr, 0, posOffsetArr, i, posOffsetSubArr.length);
                    increasingSize = posOffsetSubArr.length;
                }
            }
        }else{
            posOffsetArr = Arrays.copyOfRange(posOffsetList, offset, offset+posOffsetListLen);
        }
        return compressor.decode(posOffsetArr);
    }

    /**
     * get the numbers of the position list of each document containing the keyword from this segment
     */
    public List<Integer> readPositionNumList(PageFileChannel listChannel, DictionaryElement dictElem) {
        int offset;
        int docIDListlength = dictElem.getdocIDListLength();
        int pageNum = dictElem.getPageNum();
        int posOffsetListLen = dictElem.getPositionOffsetListLength();
        int posNumListLength = dictElem.getPositionNumLength();

        offset = (dictElem.getOffset() + docIDListlength + posOffsetListLen) % PageFileChannel.PAGE_SIZE;
        pageNum += ((dictElem.getOffset() + docIDListlength + posOffsetListLen) / PageFileChannel.PAGE_SIZE);
        ByteBuffer listBuffer = listChannel.readPage(pageNum);

        // get the inverted list of docID of the keyword
        byte[] posNumList = listBuffer.array();
        byte[] posNumListArr = new byte[posNumListLength];
        int increasingSize = 0;
        if (offset + posNumListLength > PageFileChannel.PAGE_SIZE) {
            for (int i = 0; i < posNumListLength; i += increasingSize) {
                if (posNumListLength + offset - i > PageFileChannel.PAGE_SIZE) {
                    byte[] posNumListSubArr = Arrays.copyOfRange(posNumList, offset, PageFileChannel.PAGE_SIZE);
                    System.arraycopy(posNumListSubArr, 0, posNumListArr, i, posNumListSubArr.length);
                    listBuffer.clear();
                    pageNum++;
                    offset = 0;
                    listBuffer = listChannel.readPage(pageNum);
                    posNumList = listBuffer.array();
                    increasingSize = posNumListSubArr.length;
                } else {
                    byte[] posNumListSubArr = Arrays.copyOfRange(posNumList, offset, offset + posNumListLength - i);
                    System.arraycopy(posNumListSubArr, 0, posNumListArr, i, posNumListSubArr.length);
                    increasingSize = posNumListSubArr.length;
                }
            }
        } else {
            posNumListArr = Arrays.copyOfRange(posNumList, offset, offset + posNumListLength);
        }
        Compressor naiveCompressor = new NaiveCompressor();
        return naiveCompressor.decode(posNumListArr);
    }

    /**
     * get all the position index lists of the keyword from this segment
     */
    public Map<Integer, List<Integer>> readPositionIndexList(PageFileChannel posChannel, int start, String keyword, List<Integer> invertedList,List<Integer> posOffsetList){
        int length = posOffsetList.get(posOffsetList.size()-1) - start;
        byte[] positionArr = new byte[length];
        int pageNum = start/PageFileChannel.PAGE_SIZE;
        ByteBuffer posBuffer = posChannel.readPage(pageNum);
        int offset = start % PageFileChannel.PAGE_SIZE;
        byte[] postionsList = posBuffer.array();
        Map<Integer, List<Integer>> positionIndexList = new HashMap<>();

        // get all the position index lists
        int increasingSize = 0;
        if(offset+length > PageFileChannel.PAGE_SIZE){
            for(int i=0; i<length; i+=increasingSize) {
                if(length+offset-i > PageFileChannel.PAGE_SIZE){
                    byte[] posOffsetSubArr = Arrays.copyOfRange(postionsList, offset, PageFileChannel.PAGE_SIZE);
                    System.arraycopy(posOffsetSubArr, 0, positionArr, i, posOffsetSubArr.length);
                    posBuffer.clear(); pageNum++;
                    posBuffer = posChannel.readPage(pageNum);
                    postionsList = posBuffer.array();
                    increasingSize = posOffsetSubArr.length;
                }else{
                    byte[] posOffsetSubArr = Arrays.copyOfRange(postionsList, offset, offset+length-i);
                    System.arraycopy(posOffsetSubArr, 0, positionArr, i, posOffsetSubArr.length);
                    increasingSize = posOffsetSubArr.length;
                }
                offset = 0;
            }
        }else{
            positionArr = Arrays.copyOfRange(postionsList, offset, offset+length);
        }
        int posOffset; int docuID; int lastOffset = start;
        for(int i = 0; i < invertedList.size(); i++){
            posOffset = posOffsetList.get(i); docuID = invertedList.get(i);
            positionIndexList.put(docuID, compressor.decode(Arrays.copyOfRange(positionArr, lastOffset-start, posOffset-start)));
            lastOffset = posOffset;
        }
        return positionIndexList;
    }


    /**
     * Performs a single keyword search on the inverted index.
     * You could assume the analyzer won't convert the keyword into multiple tokens.
     * If the keyword is empty, it should not return anything.
     *
     * @param keyword keyword, cannot be null.
     * @return a iterator of documents matching the query
     */
    public Iterator<Document> searchQuery(String keyword) {
        Preconditions.checkNotNull(keyword);
        if(keyword==null){
            return null;
        }
        keyword = analyzer.analyze(keyword).get(0);
        List<Document> queryResult = new ArrayList<>();

        for(int i = 0; i<segmentCount; i++){
            String dictFile = indexFolder+ "Dictionary" + i + ".txt";
            String listFile = indexFolder + "InvertedList" + i + ".txt";
            String docFile = indexFolder+ "MapdbDocStore"+ i +".db";
            Path dictPath = Paths.get(dictFile);
            Path listPath = Paths.get(listFile);
            DocumentStore documentStore = MapdbDocStore.createOrOpen(docFile);
            PageFileChannel dictChannel = PageFileChannel.createOrOpen(dictPath);
            PageFileChannel listChannel = PageFileChannel.createOrOpen(listPath);

            Map<String, DictionaryElement> dictionary = readDictionary(dictChannel);
            if(dictionary.containsKey(keyword)){
                List<Integer> invertedList = readInvertedList(listChannel, dictionary.get(keyword));
                for(int index : invertedList){
                    queryResult.add(documentStore.getDocument(index));
                }
            }

            documentStore.close();dictChannel.close();listChannel.close();
        }

        return queryResult.iterator();
    }

    /**
     * Performs an AND boolean search on the inverted index.
     *
     * @param keywords a list of keywords in the AND query
     * @return a iterator of documents matching the query
     */
    public Iterator<Document> searchAndQuery(List<String> keywords) {
        Preconditions.checkNotNull(keywords);
        if(keywords==null){
            return null;
        }
        for(int i = 0; i<keywords.size(); i++){
            if(!keywords.get(i).isEmpty()) {
                keywords.set(i, analyzer.analyze(keywords.get(i)).get(0));
            }
        }

        List<Document> queryResult = new ArrayList<>();
        for(int i = 0; i<segmentCount; i++){
            String dictFile = indexFolder+ "Dictionary" + i + ".txt";
            String listFile = indexFolder + "InvertedList" + i + ".txt";
            String docFile = indexFolder+ "MapdbDocStore"+ i +".db";
            Path dictPath = Paths.get(dictFile);
            Path listPath = Paths.get(listFile);
            DocumentStore documentStore = MapdbDocStore.createOrOpen(docFile);
            PageFileChannel dictChannel = PageFileChannel.createOrOpen(dictPath);
            PageFileChannel listChannel = PageFileChannel.createOrOpen(listPath);
            List<Integer> andList = new ArrayList<>();
            boolean isFirst = true;
            Map<String, DictionaryElement> dictionary = readDictionary(dictChannel);

            for(String keyword : keywords) {
                if (dictionary.containsKey(keyword)) {
                    List<Integer> invertedList = readInvertedList(listChannel, dictionary.get(keyword));

                    if(isFirst){
                        // add docIDs into list
                        andList = invertedList;
                        isFirst = false;
                    }else {
                        // AND operation on two lists with binary search
                        List<Integer> newAndList = new ArrayList<>();
                        for(int k=0; k<andList.size();k++){
                            int id = andList.get(k);
                            int left=0; int right = invertedList.size() - 1; int mid=0;
                            while(left<=right){
                                mid = (left+right)/2;
                                if(id == invertedList.get(mid)){
                                    newAndList.add(id);
                                    break;
                                }else{
                                    if(id < invertedList.get(mid)){
                                        right = mid -1;
                                    }else{
                                        left = mid + 1;
                                    }
                                }
                            }
                        }
                        andList = newAndList;
                    }
                }else{
                    break;
                }
            }

            for (Integer index : andList) {
                queryResult.add(documentStore.getDocument(index));
            }

            documentStore.close();dictChannel.close();listChannel.close();
        }

        return queryResult.iterator();
    }

    /**
     * Performs an OR boolean search on the inverted index.
     *
     * @param keywords a list of keywords in the OR query
     * @return a iterator of documents matching the query
     */
    public Iterator<Document> searchOrQuery(List<String> keywords) {
        Preconditions.checkNotNull(keywords);
        if(keywords==null){
            return null;
        }
        for(int i = 0; i<keywords.size(); i++){
            if(!keywords.get(i).isEmpty()) {
                keywords.set(i, analyzer.analyze(keywords.get(i)).get(0));
            }
        }
        List<Document> queryResult = new ArrayList<>();
        for(int i = 0; i<segmentCount; i++){
            String dictFile = indexFolder+ "Dictionary" + i + ".txt";
            String listFile = indexFolder + "InvertedList" + i + ".txt";
            String docFile = indexFolder+ "MapdbDocStore"+ i +".db";
            Path dictPath = Paths.get(dictFile);
            Path listPath = Paths.get(listFile);
            DocumentStore documentStore = MapdbDocStore.createOrOpen(docFile);
            PageFileChannel dictChannel = PageFileChannel.createOrOpen(dictPath);
            PageFileChannel listChannel = PageFileChannel.createOrOpen(listPath);
            Set<Integer> orList = new HashSet<>();
            Map<String, DictionaryElement> dictionary = readDictionary(dictChannel);

            for(String keyword : keywords) {
                if (dictionary.containsKey(keyword)) {
                    List<Integer> list = readInvertedList(listChannel, dictionary.get(keyword));
                    orList.addAll(list);
                }
            }
            for (Integer index : orList) {
                queryResult.add(documentStore.getDocument(index));
            }

            documentStore.close();dictChannel.close();listChannel.close();
        }

        return queryResult.iterator();
    }

    /**
     * Performs a phrase search on a positional index.
     * Phrase search means the document must contain the consecutive sequence of keywords in exact order.
     *
     * You could assume the analyzer won't convert each keyword into multiple tokens.
     * Throws UnsupportedOperationException if the inverted index is not a positional index.
     *
     * @param phrase, a consecutive sequence of keywords
     * @return a iterator of documents matching the query
     */
    public Iterator<Document> searchPhraseQuery(List<String> phrase) {
        Preconditions.checkNotNull(phrase);
        if(phrase==null){
            return null;
        }
        if(compressor == null){
            throw new UnsupportedOperationException();
        }
        for(int i = 0; i<phrase.size(); i++){
            if(!phrase.get(i).isEmpty()) {
                if(!analyzer.analyze(phrase.get(i)).isEmpty()) {
                    phrase.set(i, analyzer.analyze(phrase.get(i)).get(0));
                }else{
                    phrase.remove(i); i--;
                }
            }
        }
        List<Document> queryResult = new ArrayList<>();
        for(int i = 0; i<segmentCount; i++){
            String dictFile = indexFolder+ "Dictionary" + i + ".txt";
            String listFile = indexFolder + "InvertedList" + i + ".txt";
            String docFile = indexFolder+ "MapdbDocStore"+ i +".db";
            String posiFile = indexFolder + "PositionList" + i + ".txt";
            DocumentStore documentStore = MapdbDocStore.createOrOpen(docFile);
            PageFileChannel dictChannel = PageFileChannel.createOrOpen(Paths.get(dictFile));
            PageFileChannel listChannel = PageFileChannel.createOrOpen(Paths.get(listFile));
            PageFileChannel posiChannel = PageFileChannel.createOrOpen(Paths.get(posiFile));
            Set<Integer> tempDocIDList = new HashSet<>();

            Map<String, DictionaryElement> dictionary = readDictionary(dictChannel);
            boolean continueSearch = true;
            PositionList tempPosList = new PositionList();

            // add all position index lists of all query keywords
            for(String keyword : phrase) {
                if (!dictionary.containsKey(keyword)) {
                    continueSearch = false;
                    break;
                }else{
                    DictionaryElement dicElem = dictionary.get(keyword);
                    List<Integer> docIDList = readInvertedList(listChannel, dicElem);
                    List<Integer> offsetLis = readPositionOffsetList(listChannel,dicElem);
                    Map<Integer, List<Integer>> positionMap = readPositionIndexList(posiChannel,dicElem.positionListOffset,keyword,docIDList,offsetLis);
                    tempPosList.addPositionLists(keyword,positionMap);
                    tempDocIDList.addAll(docIDList);
                }
            }

            // remove docIDs which doesn't contain the consecutive sequence of keywords
            Set<Integer> resultList = new HashSet<>();
            if(continueSearch){
                for(Integer docuID : tempDocIDList){
                    boolean continueThisDoc = true;
                    List<Integer> tempIndex = new ArrayList<>();
                    for(int j = 0; j < phrase.size(); j++){
                        String keyword = phrase.get(j);
                        if(j == 0) {
                            tempIndex = tempPosList.getPositionList(keyword, docuID);
                            if(tempIndex == null)
                                break;
                        }else if(continueThisDoc){
                            continueThisDoc = false;
                            List<Integer> posList = tempPosList.getPositionList(keyword, docuID);
                            if(posList == null){
                                tempIndex = null;
                                break;
                            }else {
                                for (int k = 0; k < tempIndex.size(); k++) {
                                    if (posList.contains(tempIndex.get(k) + 1)) {
                                        continueThisDoc = true;
                                        tempIndex.set(k, tempIndex.get(k) + 1);
                                    } else {
                                        tempIndex.remove(k);
                                        k--;
                                    }
                                }
                            }
                        }
                    }
                    if(tempIndex != null && !tempIndex.isEmpty()){
                        resultList.add(docuID);
                    }
                }
            }

            for (Integer index : resultList) {
                queryResult.add(documentStore.getDocument(index));
            }

            documentStore.close();dictChannel.close();listChannel.close();
        }

        return queryResult.iterator();
    }

    /**
     * Performs top-K ranked search using TF-IDF.
     * Returns an iterator that returns the top K documents with highest TF-IDF scores.
     * <p>
     * Each element is a pair of <Document, Double (TF-IDF Score)>.
     * <p>
     * If parameter `topK` is null, then returns all the matching documents.
     * <p>
     * Unlike Boolean Query and Phrase Query where order of the documents doesn't matter,
     * for ranked search, order of the document returned by the iterator matters.
     *
     * @param keywords, a list of keywords in the query
     * @param topK,     number of top documents weighted by TF-IDF, all documents if topK is null
     * @return a iterator of top-k ordered documents matching the query
     */
    public Iterator<Pair<Document, Double>> searchTfIdf(List<String> keywords, Integer topK) {
        // maxheap
        PriorityQueue<Double> scoreQueue = new PriorityQueue<>(11, new Comparator<Double>() {
            @Override
            public int compare(Double d1, Double d2) {
                return d2.compareTo(d1);
            }
        });
        List<Pair<Document, Double>> possibleDoc = new ArrayList<>();

        // analyze keywords of query
        List<String> analyzedKW = new ArrayList<>();
        for (int i = 0; i < keywords.size(); i++) {
            if (!keywords.get(i).isEmpty() && analyzer.analyze(keywords.get(i)).size() > 0) {
                analyzedKW.add(analyzer.analyze(keywords.get(i)).get(0));
            }
        }
        keywords = analyzedKW;

        // store the term frequencies of query and compute IDF of all keywords in all segments
        Map<String, Integer> queryTf = new HashMap<>();
        Map<String, Double> idf = new HashMap<>();
        for (String keyword : keywords) {
            int freq = 1;
            if (queryTf.containsKey(keyword)) {
                freq += queryTf.get(keyword);
            }
            queryTf.put(keyword, freq);
            int docNum = 0;
            int docFreq = 0;
            for (int i = 0; i < segmentCount; i++) {
                docNum += getNumDocuments(i);
                docFreq += getDocumentFrequency(i, keyword);
            }
            idf.put(keyword, Math.log10((double) docNum / docFreq));
        }

        // compute TF-IDF and get k documents with top-k scores
        for (int i = 0; i < segmentCount; i++) {
            String dictFile = indexFolder + "Dictionary" + i + ".txt";
            String listFile = indexFolder + "InvertedList" + i + ".txt";
            PageFileChannel dictChannel = PageFileChannel.createOrOpen(Paths.get(dictFile));
            PageFileChannel listChannel = PageFileChannel.createOrOpen(Paths.get(listFile));
            Map<String, DictionaryElement> dictionary = readDictionary(dictChannel);
            // Map stores < docID, score>
            Map<Integer, Double> dotProductAccumulator = new HashMap<>();
            Map<Integer, Double> vectorLengthAccumulator = new HashMap<>();
            for (String keyword : keywords) {
                if (dictionary.containsKey(keyword)) {
                    List<Integer> docIDList = readInvertedList(listChannel, dictionary.get(keyword));
                    List<Integer> positionNumList = readPositionNumList(listChannel, dictionary.get(keyword));
                    for (int j = 0; j < docIDList.size(); j++) {
                        double tfIdf = positionNumList.get(j) * idf.get(keyword);
                        double dotProduct;
                        double vectorLength;
                        int docIDNum = docIDList.get(j);
                        if (dotProductAccumulator.containsKey(docIDNum)) {
                            dotProduct = dotProductAccumulator.get(docIDNum);
                            vectorLength = vectorLengthAccumulator.get(docIDNum);
                        } else {
                            dotProduct = 0;
                            vectorLength = 0;
                        }
                        dotProduct += tfIdf * queryTf.get(keyword) * idf.get(keyword);
                        dotProductAccumulator.put(docIDNum, dotProduct);
                        vectorLength += tfIdf * tfIdf;
                        vectorLengthAccumulator.put(docIDNum, vectorLength);
                    }
                }
            }
            String docFile = indexFolder + "MapdbDocStore" + i + ".db";
            DocumentStore documentStore = MapdbDocStore.createOrOpen(docFile);
            Iterator<Integer> docIDIter = documentStore.keyIterator();
            while (docIDIter.hasNext()) {
                int currDocId = docIDIter.next();
                if (dotProductAccumulator.containsKey(currDocId)) {
                    double score = 0.0;
                    if (dotProductAccumulator.get(currDocId) != 0 || vectorLengthAccumulator.get(currDocId) != 0) {
                        score = dotProductAccumulator.get(currDocId) / Math.sqrt(vectorLengthAccumulator.get(currDocId));
                    }
                    scoreQueue.add(score);
                    possibleDoc.add(new Pair<>(documentStore.getDocument(currDocId), score));
                }
            }

            dictChannel.close();
            listChannel.close();
            documentStore.close();
        }

        List<Pair<Document, Double>> result = new ArrayList<>();
        int pollLen = scoreQueue.size();
        // take top-k elements if the size of queue > k
        if (topK != null && pollLen > topK) {
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

    /**
     * Returns the total number of documents within the given segment.
     */
    public int getNumDocuments(int segmentNum) {
        String storeFile = indexFolder + "MapdbDocStore" + segmentNum + ".db";
        DocumentStore documentStore = MapdbDocStore.createOrOpen(storeFile);
        int size = (int) documentStore.size();
        documentStore.close();
        return size;
    }

    /**
     * Returns the number of documents containing the token within the given segment.
     * The token should be already analyzed by the analyzer. The analyzer shouldn't be applied again.
     */
    public int getDocumentFrequency(int segmentNum, String token) {
        String dictFile = indexFolder + "Dictionary" + segmentNum + ".txt";
        String listFile = indexFolder + "InvertedList" + segmentNum + ".txt";
        PageFileChannel dictChannel = PageFileChannel.createOrOpen(Paths.get(dictFile));
        PageFileChannel listChannel = PageFileChannel.createOrOpen(Paths.get(listFile));

        int docFrequency = 0;
        ByteBuffer dictBuffer = dictChannel.readAllPages();
        byte[] dictionary = dictBuffer.array();
        int offsetLen = 0;
        mergeBufferLen += dictionary.length;
        for (int i = 0; i < dictionary.length; i += offsetLen) {
            // get keyword and its information from dictionary
            byte[] wordLenArr = Arrays.copyOfRange(dictionary, i, i + 4);
            int wordLen = ByteBuffer.wrap(wordLenArr).getInt();
            offsetLen = wordLen + 28;
            byte[] keywordArr = Arrays.copyOfRange(dictionary, i + 4, i + 4 + wordLen);
            String keyword = new String(keywordArr, StandardCharsets.UTF_8);
            if (keyword.equals(token)) {
                byte[] docIDListOffsetArr = Arrays.copyOfRange(dictionary, i + 4 + wordLen, i + 8 + wordLen);
                int docIDListOffset = ByteBuffer.wrap(docIDListOffsetArr).getInt();
                byte[] lengthArr = Arrays.copyOfRange(dictionary, i + 8 + wordLen, i + 12 + wordLen);
                int length = ByteBuffer.wrap(lengthArr).getInt();
                byte[] pageNumArr = Arrays.copyOfRange(dictionary, i + 12 + wordLen, i + 16 + wordLen);
                int pageNum = ByteBuffer.wrap(pageNumArr).getInt();

                DictionaryElement dictElem = new DictionaryElement(docIDListOffset, length, pageNum, 0, 0, 0);
                docFrequency = readInvertedList(listChannel, dictElem).size();

                break;
            } else {
                // break the for loop if the list has not next keyword
                if (keyword.isEmpty()) {
                    break;
                }
            }
        }

        dictChannel.close();
        return docFrequency;
    }


    /**
     * Iterates through all the documents in all disk segments.
     */
    public Iterator<Document> documentIterator() {
        HashSet<Document> hashSet = new HashSet<>();
        for(int i = 0; i < segmentCount; i++){
            String storeFile = indexFolder+ "MapdbDocStore"+ i +".db";
            DocumentStore documentStore = MapdbDocStore.createOrOpen(storeFile);
            Iterator<Map.Entry<Integer, Document>> mapDocStoreIt = documentStore.iterator();
            while(mapDocStoreIt.hasNext()){
                hashSet.add(mapDocStoreIt.next().getValue());
            }
        }
        return hashSet.iterator();
    }

    /**
     * Deletes all documents in all disk segments of the inverted index that match the query.
     * @param keyword
     */
    public void deleteDocuments(String keyword) {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the total number of segments in the inverted index.
     * This function is used for checking correctness in test cases.
     *
     * @return number of index segments.
     */
    public int getNumSegments() {
        return segmentCount;
    }

    /**
     * Reads a disk segment into memory based on segmentNum.
     * This function is mainly used for checking correctness in test cases.
     *
     * @param segmentNum n-th segment in the inverted index (start from 0).
     * @return in-memory data structure with all contents in the index segment, null if segmentNum don't exist.
     */
    public InvertedIndexSegmentForTest getIndexSegment(int segmentNum) {
        String dictFile = indexFolder+ "Dictionary" + segmentNum + ".txt";
        String listFile = indexFolder + "InvertedList" + segmentNum + ".txt";
        String docFile = indexFolder+ "MapdbDocStore"+ segmentNum +".db";
        if(!Files.exists(Paths.get(dictFile)) || !Files.exists(Paths.get(listFile)) || !Files.exists(Paths.get(docFile))) {
            return null;
        }
        DocumentStore documentStore = MapdbDocStore.createOrOpen(docFile);
        PageFileChannel dictChannel = PageFileChannel.createOrOpen(Paths.get(dictFile));
        PageFileChannel listChannel = PageFileChannel.createOrOpen(Paths.get(listFile));

        Map<String, List<Integer>> invertedLists = getInvertedList(readDictionary(dictChannel), listChannel);

        Map<Integer, Document> documents = getDocumentStore(documentStore);

        documentStore.close();
        dictChannel.close();
        listChannel.close();

        return new InvertedIndexSegmentForTest(invertedLists,documents);
    }

    /**
     * get the dictionary of this segments.
     */

    /**
     * get all the inverted lists in this segments.
     */
    public Map<String, List<Integer>> getInvertedList(Map<String, DictionaryElement> dictionary, PageFileChannel listChannel){
        Map<String, List<Integer>> invertedLists = new HashMap<>();
        for(Map.Entry<String, DictionaryElement> entry : dictionary.entrySet()){
            // add keyword and index list into Map
            invertedLists.put(entry.getKey(),readInvertedList(listChannel,entry.getValue()));
        }
        return invertedLists;
    }

    /**
     * get all the position offset lists in this segments.
     */
    public Map<String, List<Integer>> getPositionOffsetList(Map<String, DictionaryElement> dictionary,PageFileChannel listChannel){
        Map<String, List<Integer>> positionOffsetLists = new HashMap<>();
        for(Map.Entry<String, DictionaryElement> entry : dictionary.entrySet()){
            // add keyword and index list into Map
            positionOffsetLists.put(entry.getKey(),readPositionOffsetList(listChannel,entry.getValue()));

        }
        return positionOffsetLists;
    }


    /**
     * get the Document Store in this segments.
     */

    public Map<Integer, Document> getDocumentStore(DocumentStore documentStore){
        Map<Integer, Document> mapDocStore = new HashMap<>();

        Iterator mapDocStoreIt = documentStore.iterator();
        while(mapDocStoreIt.hasNext()){
            Map.Entry<Integer, Document> entry = (Map.Entry)mapDocStoreIt.next();
            mapDocStore.put(entry.getKey(),entry.getValue());
        }

        return mapDocStore;
    }

    /**
     * Reads a disk segment of a positional index into memory based on segmentNum.
     * This function is mainly used for checking correctness in test cases.
     *
     * Throws UnsupportedOperationException if the inverted index is not a positional index.
     *
     * @param segmentNum n-th segment in the inverted index (start from 0).
     * @return in-memory data structure with all contents in the index segment, null if segmentNum don't exist.
     */
    public PositionalIndexSegmentForTest getIndexSegmentPositional(int segmentNum) {
        String dictFile = indexFolder+ "Dictionary" + segmentNum + ".txt";
        String listFile = indexFolder + "InvertedList" + segmentNum + ".txt";
        String docFile = indexFolder+ "MapdbDocStore"+ segmentNum +".db";
        String positionFile = indexFolder + "PositionList" + segmentNum + ".txt";

        if(!Files.exists(Paths.get(dictFile)) || !Files.exists(Paths.get(listFile)) || !Files.exists(Paths.get(positionFile)) || !Files.exists(Paths.get(docFile))) {
            return null;
        }
        DocumentStore documentStore = MapdbDocStore.createOrOpen(docFile);
        PageFileChannel dictChannel = PageFileChannel.createOrOpen(Paths.get(dictFile));
        PageFileChannel listChannel = PageFileChannel.createOrOpen(Paths.get(listFile));
        PageFileChannel posiChannel = PageFileChannel.createOrOpen(Paths.get(positionFile));

        Map<String, DictionaryElement> dictionary = readDictionary(dictChannel);

        Map<String, List<Integer>> invertedLists = getInvertedList(dictionary, listChannel);

        Map<String, List<Integer>> positionOffsetLists = getPositionOffsetList(dictionary, listChannel);

        Map<Integer, Document> documents = getDocumentStore(documentStore);

        Table<String, Integer, List<Integer>> positions = getPositionIndexList(dictionary, invertedLists, positionOffsetLists, posiChannel);

        documentStore.close();
        dictChannel.close();
        listChannel.close();
        posiChannel.close();

        return new PositionalIndexSegmentForTest(invertedLists, documents, positions);
    }

    /**
     * get all the position index lists of this segments.
     */
    public Table<String, Integer, List<Integer>> getPositionIndexList(Map<String, DictionaryElement> dictionary, Map<String, List<Integer>> invertedLists, Map<String, List<Integer>> positionOffsetLists, PageFileChannel posiChannel){
        PositionList positionIndexList = new PositionList();
        for(Map.Entry<String, DictionaryElement> entry : dictionary.entrySet()){
            String keyword = entry.getKey();
            int start = entry.getValue().getPositionListOffset();
            Map<Integer, List<Integer>> positionLists = readPositionIndexList(posiChannel,start,keyword,invertedLists.get(keyword), positionOffsetLists.get(keyword));
            positionIndexList.addPositionLists(keyword, positionLists);
        }
        return positionIndexList.returnTable();
    }
}
