package edu.uci.ics.cs221.index.inverted;

import com.google.common.base.Preconditions;
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
    public InvertedIndexList indexList = new InvertedIndexList();
    private int docID = 0;
    public int segmentCount = 0;
    private int dicBufferLen = 0;
    private int mergeBufferLen = 0;

    // a structure to store all in-memory document;
    public Map<Integer, Document> inMemDocStore = new HashMap<>();


    private InvertedIndexManager(String indexFolder, Analyzer analyzer) {
        this.analyzer = analyzer;
        this.indexFolder = indexFolder;
    }

    /**
     * Creates an inverted index manager with the folder and an analyzer
     */
    public static InvertedIndexManager createOrOpen(String indexFolder, Analyzer analyzer) {
        try {
            Path indexFolderPath = Paths.get(indexFolder);
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
     * Adds a document to the inverted index.
     * Document should live in a in-memory buffer until `flush()` is called to write the segment to disk.
     * @param document
     */
    public void addDocument(Document document) {
        // stem words and delete stop words
        List<String> wordList = analyzer.analyze(document.getText());
        // add all words into inverted index list with current docID
        for(String word : wordList){
            indexList.addDocID(word, docID);
            // add length into dictionary buffer size
            dicBufferLen += word.getBytes(StandardCharsets.UTF_8).length + 16;
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
        Path dictPath = Paths.get(dictFile);
        Path listPath = Paths.get(listFile);

        if(inMemDocStore.size()>0) {
            DocumentStore documentStore = MapdbDocStore.createOrOpen(storeFile);
            PageFileChannel dictChannel = PageFileChannel.createOrOpen(dictPath);
            PageFileChannel listChannel = PageFileChannel.createOrOpen(listPath);

            writeDocStore(documentStore,inMemDocStore);
            writeDictAndList(dictChannel,listChannel,indexList,dicBufferLen);

            documentStore.close();
            dictChannel.close();
            listChannel.close();

            //clear invertedIndexList and in-memory document store
            indexList.deleteAll();
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
     * write document Store into files.
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
     * write dictionary and inverted list into files.
     */
    public void writeDictAndList(PageFileChannel dictChannel, PageFileChannel listChannel,InvertedIndexList invertedList, int dicLen){
        // write dictionary file with element format : wordLen(4) | keyword(arbitrary) | offset(4) | length(4) | page num(4)
        // write inverted list file
        Iterator iterator = invertedList.indexList.entrySet().iterator();
        ByteBuffer dictByteBuffer = ByteBuffer.allocate(dicLen);
        ByteBuffer listByteBuffer = ByteBuffer.allocate(PageFileChannel.PAGE_SIZE);
        if(dicLen == dicBufferLen){
            dicBufferLen = 0;
        }
        int offset = 0;
        int listPageNum = 0;
        while (iterator.hasNext()) {
            Map.Entry<String, List<Integer>> entry = (Map.Entry) iterator.next();
            // add one keyword
            String str = entry.getKey();
            List<Integer> list = entry.getValue();

            byte[] keyword = str.getBytes(StandardCharsets.UTF_8);
            int wordLen = keyword.length;

            int length = list.size() * 4;
            if (!listByteBuffer.hasRemaining()) {
                listPageNum++;offset=0;
                listChannel.appendPage(listByteBuffer);
                listByteBuffer.clear();
            }
            dictByteBuffer.putInt(wordLen);
            dictByteBuffer.put(keyword);
            dictByteBuffer.putInt(offset);
            dictByteBuffer.putInt(length);
            dictByteBuffer.putInt(listPageNum);

            // add one inverted list
            if (listByteBuffer.remaining() < list.size() * 4) {
                int remainNum = listByteBuffer.remaining() / 4;
                for (int i = 0; i < remainNum; i++) {
                    listByteBuffer.put(ByteBuffer.allocate(4).putInt(list.get(i)).array());
                }
                listChannel.appendPage(listByteBuffer);
                listByteBuffer.clear();
                listPageNum++;
                offset = 0;
                for (int j = remainNum; j < list.size(); j++) {
                    listByteBuffer.put(ByteBuffer.allocate(4).putInt(list.get(j)).array());
                    offset += 4;
                }
            } else {
                for (int j = 0; j < list.size(); j++) {
                    listByteBuffer.put(ByteBuffer.allocate(4).putInt(list.get(j)).array());
                    offset += 4;
                }
            }
        }
        dictChannel.appendAllBytes(dictByteBuffer);

        // append the last page of list
        if (listByteBuffer.position() > 0) {
            appendLastPage(listByteBuffer, listChannel);
        }
    }

    /**
     * append the last page of ByteBuffer.
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
            Path dictPath1 = Paths.get(dictFile1);
            Path listPath1 = Paths.get(listFile1);
            DocumentStore documentStore1 = MapdbDocStore.createOrOpen(docFile1);
            PageFileChannel dictChannel1 = PageFileChannel.createOrOpen(dictPath1);
            PageFileChannel listChannel1 = PageFileChannel.createOrOpen(listPath1);

            String dictFile2 = indexFolder+ "Dictionary" + (i*2+1) + ".txt";
            String listFile2 = indexFolder + "InvertedList" + (i*2+1) + ".txt";
            String docFile2 = indexFolder+ "MapdbDocStore"+ (i*2+1) +".db";
            Path dictPath2 = Paths.get(dictFile2);
            Path listPath2 = Paths.get(listFile2);
            DocumentStore documentStore2 = MapdbDocStore.createOrOpen(docFile2);
            PageFileChannel dictChannel2 = PageFileChannel.createOrOpen(dictPath2);
            PageFileChannel listChannel2 = PageFileChannel.createOrOpen(listPath2);

            // merge documents of documentStore2 into documentStore1
            int doc1Size = (int)documentStore1.size();
            for(int k = 0; k < documentStore2.size(); k++){
                documentStore1.addDocument(doc1Size+k,documentStore2.getDocument(k));
            }

            // merge inverted list of invertedIndexList2 into invertedIndexList1
            String dictFile3 = indexFolder+ "Dictionary" + (8+i) + ".txt";
            String listFile3 = indexFolder + "InvertedList" + (8+i) + ".txt";
            Path dictPath3 = Paths.get(dictFile3);
            Path listPath3 = Paths.get(listFile3);
            PageFileChannel dictChannel3 = PageFileChannel.createOrOpen(dictPath3);
            PageFileChannel listChannel3 = PageFileChannel.createOrOpen(listPath3);
            InvertedIndexList invertedList = new InvertedIndexList();
            Map<String, DictionaryElement> dictionary1 = readDictionary(dictChannel1);
            Map<String, DictionaryElement> dictionary2 = readDictionary(dictChannel2);
            Iterator it1 = dictionary1.entrySet().iterator();
            int pageCount1 = 0; int pageCount2 = 0;
            ByteBuffer thisPage1 = listChannel1.readPage(pageCount1);
            ByteBuffer nextPage1 = listChannel1.readPage(pageCount1+1);
            ByteBuffer thisPage2 = listChannel2.readPage(pageCount2);
            ByteBuffer nextPage2 = listChannel2.readPage(pageCount2+1);
            while(it1.hasNext()){
                Map.Entry<String, DictionaryElement> entry = (Map.Entry)it1.next();
                DictionaryElement dictEle = entry.getValue();
                if(dictEle.getPageNum() != pageCount1){
                    pageCount1 = dictEle.getPageNum();
                    thisPage1.clear();nextPage1.clear();
                    thisPage1 = listChannel1.readPage(pageCount1);
                    nextPage1 = listChannel1.readPage(pageCount1+1);
                }
                invertedList.addList(entry.getKey(),readInvertedList(thisPage1, nextPage1,dictEle));
            }
            Iterator it2 = dictionary2.entrySet().iterator();
            while(it2.hasNext()){
                Map.Entry<String, DictionaryElement> entry = (Map.Entry)it2.next();
                DictionaryElement dictEle = entry.getValue();
                if(dictEle.getPageNum() != pageCount2){
                    pageCount2 = dictEle.getPageNum();
                    thisPage2.clear();nextPage2.clear();
                    thisPage2 = listChannel2.readPage(pageCount2);
                    nextPage2 = listChannel2.readPage(pageCount2+1);
                }
                List<Integer> list2 = readInvertedList(thisPage2,nextPage2,dictEle);
                for(int j=0; j<list2.size();j++){
                    list2.set(j,doc1Size+list2.get(j));
                }
                invertedList.addList(entry.getKey(),list2);
            }
            writeDictAndList(dictChannel3, listChannel3, invertedList,mergeBufferLen);
            mergeBufferLen = 0;

            documentStore1.close();documentStore2.close();
            dictChannel1.close();dictChannel2.close();
            listChannel1.close();listChannel2.close();

            //delete files
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

            // change file name of merged segment
            File dictFileName = new File(dictFile3);
            dictFileName.renameTo(new File(indexFolder+ "Dictionary" + i + ".txt"));
            File listFileName = new File(listFile3);
            listFileName.renameTo(new File(indexFolder + "InvertedList" + i + ".txt"));
            File docFileName = new File(docFile1);
            docFileName.renameTo(new File(indexFolder+ "MapdbDocStore"+ i +".db"));
        }
        segmentCount = segmentCount/2;
    }

    /**
     * get dictionary from file
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
            offsetLen = wordLen + 16;
            byte[] keywordArr = Arrays.copyOfRange(dictionary,i+4, i+4+wordLen);
            String keyword = new String(keywordArr, StandardCharsets.UTF_8);
            byte[] offsetArr = Arrays.copyOfRange(dictionary,i+4+wordLen, i+8+wordLen);
            int offset = ByteBuffer.wrap(offsetArr).getInt();
            byte[] lengthArr = Arrays.copyOfRange(dictionary,i+8+wordLen, i+12+wordLen);
            int length = ByteBuffer.wrap(lengthArr).getInt();
            byte[] pageNumArr = Arrays.copyOfRange(dictionary,i+12+wordLen, i+16+wordLen);
            int pageNum = ByteBuffer.wrap(pageNumArr).getInt();

            // break the for loop if the list has not next keyword
            if (keyword.isEmpty()) {
                break;
            }

            // add element into dictionary map
            DictionaryElement dictElem = new DictionaryElement(offset,length,pageNum);
            dictList.put(keyword,dictElem);
        }
        return dictList;
    }

    /**
     * get inverted list from file according to keyword
     */
    public List<Integer> readInvertedList(ByteBuffer thisPage, ByteBuffer nextPage, DictionaryElement dictElem){
        int length = dictElem.getLength();
        int offset = dictElem.getOffset();

        // get the inverted list of docID of the keyword
        byte[] docIDList = thisPage.array();
        byte[] docIDArr = new byte[length];
        if(offset+length > PageFileChannel.PAGE_SIZE){
            byte[] docIDArr1 = Arrays.copyOfRange(docIDList, offset, PageFileChannel.PAGE_SIZE);
            docIDList = nextPage.array();
            byte[] docIDArr2 = Arrays.copyOfRange(docIDList, 0, offset+length-PageFileChannel.PAGE_SIZE);
            System.arraycopy(docIDArr1, 0, docIDArr, 0, docIDArr1.length);
            System.arraycopy(docIDArr2, 0, docIDArr, docIDArr1.length, docIDArr2.length);
        }else{
            docIDArr = Arrays.copyOfRange(docIDList, offset, offset+length);
        }

//        System.out.println(length);
        List<Integer> indexList = new ArrayList<>();
        for(int j = 0; j < length; j += 4){
            byte[] docIDNum = Arrays.copyOfRange(docIDArr,j, j+4);
            indexList.add(ByteBuffer.wrap(docIDNum).getInt());
        }
        return indexList;
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
                ByteBuffer thisPage = listChannel.readPage(dictionary.get(keyword).getPageNum());
                ByteBuffer nextPage = listChannel.readPage(dictionary.get(keyword).getPageNum()+1);
                List<Integer> invertedList = readInvertedList(thisPage, nextPage, dictionary.get(keyword));
                System.out.println(invertedList.size());
                for(int index : invertedList){
                    System.out.println(index);
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
            int pageNum = 0; boolean isFirst = true;
            ByteBuffer thisPage =  listChannel.readPage(pageNum);
            ByteBuffer nextPage = listChannel.readPage(pageNum+1);

            for(String keyword : keywords) {
                Map<String, DictionaryElement> dictionary = readDictionary(dictChannel);
                if (dictionary.containsKey(keyword)) {
                    if(dictionary.get(keyword).getPageNum() != pageNum) {
                        pageNum = dictionary.get(keyword).getPageNum();
                        thisPage.clear(); nextPage.clear();
                        thisPage = listChannel.readPage(pageNum);
                        nextPage = listChannel.readPage(pageNum);
                    }
                    List<Integer> invertedList = readInvertedList(thisPage, nextPage, dictionary.get(keyword));

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
            int pageNum = 0;
            Set<Integer> orList = new HashSet<>();
            ByteBuffer thisPage =  listChannel.readPage(pageNum);
            ByteBuffer nextPage = listChannel.readPage(pageNum+1);

            for(String keyword : keywords) {
                Map<String, DictionaryElement> dictionary = readDictionary(dictChannel);
                if (dictionary.containsKey(keyword)) {
                    if(dictionary.get(keyword).getPageNum() != pageNum) {
                        pageNum = dictionary.get(keyword).getPageNum();
                        thisPage.clear(); nextPage.clear();
                        thisPage = listChannel.readPage(pageNum);
                        nextPage = listChannel.readPage(pageNum);
                    }
                    orList.addAll(readInvertedList(thisPage, nextPage, dictionary.get(keyword)));
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
        Path dictPath = Paths.get(dictFile);
        Path listPath = Paths.get(listFile);
        if(!Files.exists(dictPath) || !Files.exists(listPath) || !Files.exists(Paths.get(docFile))) {
            return null;
        }
        DocumentStore documentStore = MapdbDocStore.createOrOpen(docFile);
        PageFileChannel dictChannel = PageFileChannel.createOrOpen(dictPath);
        PageFileChannel listChannel = PageFileChannel.createOrOpen(listPath);

        Map<String, List<Integer>> invertedLists = getInvertedList(dictChannel, listChannel);

        Map<Integer, Document> documents = getDocumentStore(documentStore);

        documentStore.close();
        dictChannel.close();
        listChannel.close();

        return new InvertedIndexSegmentForTest(invertedLists,documents);
    }

    public Map<String, List<Integer>> getInvertedList(PageFileChannel dictChannel, PageFileChannel listChannel){
        Map<String, List<Integer>> invertedLists = new HashMap<>();
        int pageCount = 0;
        ByteBuffer dictBuffer = dictChannel.readAllPages();
        ByteBuffer listBuffer = listChannel.readPage(pageCount);
        byte[] dictionary = dictBuffer.array();
        byte[] docIDList = listBuffer.array();
        int offsetLen = 0;
        for(int i = 0; i<dictBuffer.capacity(); i+=offsetLen){
            // get keyword and its information from dictionary
            byte[] wordLenArr = Arrays.copyOfRange(dictionary,i, i+4);
            int wordLen = ByteBuffer.wrap(wordLenArr).getInt();
            offsetLen = wordLen + 16;
            byte[] keywordArr = Arrays.copyOfRange(dictionary,i+4, i+4+wordLen);
            String keyword = new String(keywordArr,StandardCharsets.UTF_8);
            keyword = keyword.trim();
            byte[] offsetArr = Arrays.copyOfRange(dictionary,i+4+wordLen, i+8+wordLen);
            int offset = ByteBuffer.wrap(offsetArr).getInt();
            byte[] lengthArr = Arrays.copyOfRange(dictionary,i+8+wordLen, i+12+wordLen);
            int length = ByteBuffer.wrap(lengthArr).getInt();
            byte[] pageNumArr = Arrays.copyOfRange(dictionary,i+12+wordLen, i+16+wordLen);
            int pageNum = ByteBuffer.wrap(pageNumArr).getInt();

            // break the for loop if the list has not next keyword
            if(keyword.isEmpty()){
                break;
            }

            // get the inverted list of docID of the keyword
            byte[] docIDArr = new byte[length];
            if(pageNum != pageCount){
                listBuffer.clear();
                listBuffer = listChannel.readPage(pageNum);
                docIDList = listBuffer.array();
                pageCount = pageNum;
            }else if(offset+length > PageFileChannel.PAGE_SIZE){
                System.arraycopy(docIDList,offset,docIDArr,0,PageFileChannel.PAGE_SIZE-offset);
                listBuffer.clear();
                listBuffer = listChannel.readPage(pageNum+1);
                docIDList = listBuffer.array();
                pageCount++;
                System.arraycopy(docIDList,0,docIDArr,PageFileChannel.PAGE_SIZE-offset,length-PageFileChannel.PAGE_SIZE+offset);
            }else{
                System.arraycopy(docIDList,offset,docIDArr,0,length);
            }

            List<Integer> indexList = new ArrayList<>();
            for(int j = 0; j < length; j += 4){
                byte[] docIDNum = Arrays.copyOfRange(docIDArr,j, j+4);
                indexList.add(ByteBuffer.wrap(docIDNum).getInt());
            }

            // add keyword and index list into Map
            invertedLists.put(keyword,indexList);
        }
        return invertedLists;
    }

    public Map<Integer, Document> getDocumentStore(DocumentStore documentStore){
        Map<Integer, Document> mapDocStore = new HashMap<>();

        Iterator mapDocStoreIt = documentStore.iterator();
        while(mapDocStoreIt.hasNext()){
            Map.Entry<Integer, Document> entry = (Map.Entry)mapDocStoreIt.next();
            mapDocStore.put(entry.getKey(),entry.getValue());
        }

        return mapDocStore;
    }
}
