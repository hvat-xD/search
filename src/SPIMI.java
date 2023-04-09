


import java.io.*;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.*;

import java.util.stream.Collectors;

import static java.lang.Math.*;


public class SPIMI {

    Tokenizer tokenizer = new Tokenizer(null);
    int docId = -1;
    int blockNumber;
    File collectionBase;
    ArrayList<DictionaryPosition> dictionaryPositions;
    Iterator<Path> directoryIterator;
    Charset encoding = StandardCharsets.UTF_8;
    int dictionaryEnd;
    int postingsEnd;
    long memorySize;
    boolean forceBuild;
    //ArrayList<Path> filePaths;
    long startTime;
    ArrayList<DockVector> documentVectors;

    public SPIMI(String path, long memorySize, boolean forceBuild) throws IOException {
        this.documentVectors = new ArrayList<>();
        this.startTime = System.nanoTime();
        this.dictionaryPositions = new ArrayList<>();
        //this.filePaths = new ArrayList<>();
        this.forceBuild = forceBuild;
        this.blockNumber = 0;
        this.memorySize = memorySize;
        collectionBase = new File(path);
        if (!collectionBase.isDirectory())throw new NotDirectoryException(path);
        directoryIterator = Files.newDirectoryStream(Path.of(path)).iterator();
    }
    private static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {

        ObjectInputStream ois = null;
        ois = new ObjectInputStream(new ByteArrayInputStream(data));

        return ois.readObject();
    }
    public static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(out);
        os.writeObject(obj);
        return out.toByteArray();
    }

    private void createVectorSpace() throws IOException, ClassNotFoundException {
        File vecFile = new File("src/vectorspace.f");
        System.out.println("creating vector space");
        int leadersNum = (int) sqrt(docId);
        Iterator<Path> iterator = Files.newDirectoryStream(collectionBase.toPath()).iterator();
        FileOutputStream fos = new FileOutputStream("src/vectorspace.f");

        int docNum = 0;
        while (docNum<leadersNum){
            //saving position in file
            long pos = fos.getChannel().position();
            Map<String, Integer> vector = new HashMap<>();
            //opening file
            Path doc = iterator.next();
            //creating vector
            if (doc.toString().endsWith(".txt")){
                tokenizer.setFilename(doc);
                tokenizer.readDocument(encoding);
                for (int j = 0; j < tokenizer.tokens.size(); j++){
                    vector.put(tokenizer.tokens.get(j), (vector.get(tokenizer.tokens.get(j))==null)?1:vector.get(tokenizer.tokens.get(j))+1);
                }
            }


            //writing vector
            fos.write(serialize(vector));
            documentVectors.add(new DockVector(pos, docNum, (int) (vecFile.length() - pos), true));
            docNum++;
        }
        while (iterator.hasNext()){
            //saving position in file
            long pos = fos.getChannel().position();
            HashMap<String, Integer> vector = new HashMap<>();
            //opening file
            Path doc = iterator.next();
            //creating vector
            if (doc.toString().endsWith(".txt")){
                tokenizer.setFilename(doc);
                tokenizer.readDocument(encoding);
                for (int j = 0; j < tokenizer.tokens.size(); j++){
                    vector.put(tokenizer.tokens.get(j), (vector.get(tokenizer.tokens.get(j))==null)?1:vector.get(tokenizer.tokens.get(j))+1);
                }
            }
            fos.write(serialize(vector));

            //todo wtf is this taking so long
            double maxSim = 0;
            int ind = -1;
            for (int i = 0; i < documentVectors.size(); i++){
                double maxPrev = maxSim;
                maxSim = max(maxSim, computeSimilarity(vector, getLeaderVector(i)));
                if (maxPrev!=maxSim)ind = i;
            }
            documentVectors.get(ind).children.add(new DockVector(pos, docNum, (int) (vecFile.length() - pos), false));
            System.out.println(docNum);
            docNum++;
        }
    }
    private double computeSimilarity(HashMap<String,Integer> f, HashMap<String, Integer> s){
        TreeSet<String> ff = new TreeSet<>(f.keySet());
        TreeSet<String> ss = new TreeSet<>(s.keySet());

        Iterator<String> fff = ff.iterator();
        Iterator<String> sss = ss.iterator();
        double sumWfIdf = 0, sumFirst = 0, sumSecond = 0;
        String first = fff.next();
        String second = sss.next();
        while (sss.hasNext() && fff.hasNext() ){

            Integer tfFirst = f.get(first), tfSecond = s.get(second);
            if (tfFirst == null) tfFirst = 0;
            if (tfSecond == null) tfSecond = 0;
            double wfIdfFirst = ((tfFirst > 0)?1 + log(tfFirst) : 0) * getIdf(first);
            double wfIdfSecond = ((tfSecond > 0)?1 + log(tfSecond) : 0) * getIdf(second);
            sumFirst+=wfIdfFirst*wfIdfFirst;
            sumSecond+=wfIdfSecond*wfIdfSecond;
            f.put(first, tfFirst==0?null:-tfFirst);
            s.put(second, tfSecond==0?null:-tfSecond);
            if (first.equals(second)){
                sumWfIdf+=wfIdfFirst*wfIdfSecond;
                first = fff.next();
                second = sss.next();
            } else if (first.compareTo(second)<0) {
                first = fff.next();
            } else {
                second = sss.next();
            }
        }
        while (fff.hasNext()){
            first = fff.next();
            Integer tfFirst = f.get(first);
            double wfIdfFirst = ((tfFirst > 0)?1 + log(tfFirst) : 0) * getIdf(first);
            sumFirst+=wfIdfFirst*wfIdfFirst;

        }
        while (sss.hasNext()){
            second = sss.next();
            Integer  tfSecond = s.get(second);
            double wfIdfSecond = ((tfSecond > 0)?1 + log(tfSecond) : 0) * getIdf(second);
            sumSecond+=wfIdfSecond*wfIdfSecond;
        }
        for (Map.Entry<String, Integer> e : f.entrySet()){
            e.setValue(e.getValue()==null?null:-e.getValue());
        }
        for (Map.Entry<String, Integer> e : s.entrySet()){
            e.setValue(e.getValue()==null?null:-e.getValue());
        }
        return sumWfIdf / (sqrt(sumFirst)*sqrt(sumSecond));
    }
    private HashMap<String, Integer> getLeaderVector(int i) throws IOException, ClassNotFoundException {
        return readVectorFromFIle(documentVectors.get(i).posInDoc, documentVectors.get(i).len);
    }
    private HashMap<String, Integer> readVectorFromFIle(long pos, int len) throws IOException, ClassNotFoundException {

        byte[] bytes = new byte[len];
        RandomAccessFile randomAccessFile = new RandomAccessFile("src/vectorspace.f","r");
        randomAccessFile.seek(pos);
        randomAccessFile.read(bytes);
        randomAccessFile.close();
        return (HashMap<String, Integer>) deserialize(bytes);
    }
    public ArrayList<Path> getDocumentsOfPosting(ArrayList<Double> posting) {
        ArrayList<Path> paths = new ArrayList<>();
        try {
            Iterator<Path> dIter = Files.newDirectoryStream(collectionBase.toPath()).iterator();
            int docId = 0;
            Path cur = dIter.next();
            for (int i = 0; i < posting.size(); i+=2){
                while(docId!=posting.get(i)){
                    cur = dIter.next();
                    docId++;
                }
                paths.add(cur);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return paths;
    }
    public ArrayList<Path> search(String query, int count) throws IOException {
        query = query.toLowerCase();
        ArrayList<Path> res = new ArrayList<>();
        ArrayList<DocumentHolder> docs = new ArrayList<>();
        ArrayList<Double> relevant = findRelevant(query);
        ArrayList<Path> paths = getDocumentsOfPosting(relevant);
        for (int i = 0; i < relevant.size(); i+=2){
            docs.add(new DocumentHolder(paths.get(i/2), relevant.get(i).intValue() ,relevant.get(i+1)));
        }
        docs.sort(Comparator.comparingDouble(o -> o.wfIdf));

        for (int i = docs.size()-1; i >=0; i--){
            if (docs.size()-1 - i > count)break;
            res.add(docs.get(i).path);
        }
        return res;
    }

    public void generateIndex() {
        if (!forceBuild){
            if (new File("src/changed.txt").exists()){
                try {
                    DataInputStream dataInputStream = new DataInputStream(new FileInputStream("src/changed.txt"));
                    long lastChangedLog = dataInputStream.readLong();
                    if (lastChangedLog != collectionBase.lastModified()){
                        rebuildIndex();
                    }
                    else {
                        deserializePositions();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                rebuildIndex();
            }
        }
        else rebuildIndex();
    }

    private static long getGarbageCollectionTime() {
        long collectionTime = 0;
        for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            collectionTime += garbageCollectorMXBean.getCollectionTime();
        }
        return collectionTime;
    }
    public double getIdf(String word){
        int low = 0, high = dictionaryPositions.size()-1;
        while (low<=high){
            int mid = low + ((high - low) /2);
            if (getTerm(mid).compareTo(word) < 0){
                low = mid+1;
            }
            else if(getTerm(mid).compareTo(word)>0){
                high = mid - 1;
            }
            else if(getTerm(mid).equals(word)){
                return dictionaryPositions.get(mid).idf;
            }
        }
        return 0;
    }
    public ArrayList<Double> searchWord(String word) throws IOException {
        ArrayList<Double> casted;
        int low = 0, high = dictionaryPositions.size()-1;
        while (low<=high){
            int mid = low + ((high - low) /2);
            if (getTerm(mid).compareTo(word) < 0){
                low = mid+1;
            }
            else if(getTerm(mid).compareTo(word)>0){
                high = mid - 1;
            }
            else if(getTerm(mid).equals(word)){
                casted = (ArrayList<Double>) getPosting(mid).parallelStream().mapToDouble(i->i)
                        .boxed().collect(Collectors.toList());
                for (int i = 1; i < casted.size(); i+=2){

                    casted.set(i,((casted.get(i)>0)?1+log(casted.get(i)):0) * dictionaryPositions.get(mid).idf);
                }
                return casted;
            }
        }
        return new ArrayList<>();
    }
    private void rebuildIndex() {

        try {
            DataOutputStream dataOutputStream = new DataOutputStream(new FileOutputStream("src/changed.txt"));
            dataOutputStream.writeLong(collectionBase.lastModified());
            Files.walk(Path.of("src/blocks"))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            Files.createDirectories(Path.of("src/blocks"));
            createBlocks();
            mergeAllBlocks();
            createVectorSpace();
            serializePositions();
        } catch (IOException e) {
            try {
                Files.createDirectories(Path.of("src/blocks"));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

    }
    private void deserializePositions() throws IOException, ClassNotFoundException {
        ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream("Positions.txt"));
        dictionaryPositions = (ArrayList<DictionaryPosition>) inputStream.readObject();
        inputStream.close();
        inputStream = new ObjectInputStream(new FileInputStream("PostingsEnd.txt"));
        postingsEnd = (int) inputStream.readObject();
        inputStream.close();
        inputStream = new ObjectInputStream(new FileInputStream("DictionaryEnd.txt"));
        dictionaryEnd = (int) inputStream.readObject();
        inputStream.close();
    }
    private void serializePositions() throws IOException {
        ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream("Positions.txt"));
        outputStream.writeObject(dictionaryPositions);
        outputStream.flush();
        outputStream.close();
        outputStream = new ObjectOutputStream(new FileOutputStream("PostingsEnd.txt"));
        outputStream.writeObject(postingsEnd);
        outputStream.flush();
        outputStream.close();
        outputStream = new ObjectOutputStream(new FileOutputStream("DictionaryEnd.txt"));
        outputStream.writeObject(dictionaryEnd);
        outputStream.flush();
        outputStream.close();
    }
    private ArrayList<Integer> getPosting(Integer i) throws IOException {
        if (i == dictionaryPositions.size()-1){
            return readFromPostings(dictionaryPositions.get(i).bytePosList, postingsEnd - dictionaryPositions.get(i).bytePosList);
        }
        return readFromPostings(dictionaryPositions.get(i).bytePosList, dictionaryPositions.get(i+1).bytePosList - dictionaryPositions.get(i).bytePosList);
    }
    private String getTerm(Integer i){
        if (i == dictionaryPositions.size()-1){
            return readFromDictionary(dictionaryPositions.get(i).bytePosTerm, dictionaryEnd -dictionaryPositions.get(i).bytePosTerm);
        }
        return readFromDictionary(dictionaryPositions.get(i).bytePosTerm, dictionaryPositions.get(i+1).bytePosTerm-dictionaryPositions.get(i).bytePosTerm);
    }
    private String readFromDictionary(int posStart, int len){
        RandomAccessFile randomAccessFile;
        byte[] bytes = new byte[len];
        try {
            randomAccessFile = new RandomAccessFile("src/dictionary.txt","r");
            randomAccessFile.seek(posStart);
            randomAccessFile.read(bytes);
            randomAccessFile.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        return new String(bytes,encoding);
    }
    private ArrayList<Integer> readFromPostings(int posStart, int len) throws IOException{
        byte[] bytes = new byte[len];
        RandomAccessFile randomAccessFile = new RandomAccessFile("src/postings.f","r");
        randomAccessFile.seek(posStart);
        randomAccessFile.read(bytes);
        randomAccessFile.close();

        return decode(bytes);
    }
    public ArrayList<Double> findRelevant(String query) throws IOException {
        double coefOfPresence = 0.3;
        String[] words = query.split("\\s+");
        ArrayList<ArrayList<Double>> postings = new ArrayList<>();
        ArrayList<Iterator<Double>> iterators = new ArrayList<>();
        ArrayList<Double> currentDocs = new ArrayList<>();
        ArrayList<Double> res = new ArrayList<>();
        for (String w : words){
            postings.add(searchWord(w));
            if (!postings.get(postings.size()-1).isEmpty()) {
                iterators.add(postings.get(postings.size() - 1).iterator());
                currentDocs.add(iterators.get(iterators.size() - 1).next());
            } else postings.remove(postings.size()-1);
        }

        while (!iterators.isEmpty()) {
            Double minDoc = currentDocs.get(0);

            for (int i = 1; i < currentDocs.size(); i++) {
                if (currentDocs.get(i) < minDoc) minDoc = currentDocs.get(i);
            }

            Double wfIdfSum = 0.0;
            for (int i = 0; i < currentDocs.size(); i++) {
                if (currentDocs.get(i).equals(minDoc)) {
                    wfIdfSum += iterators.get(i).next();
                    if (iterators.get(i).hasNext()) {
                        currentDocs.set(i, iterators.get(i).next());
                    } else {
                        currentDocs.remove(i);
                        iterators.remove(i);
                        i--;
                    }
                } else wfIdfSum *= coefOfPresence;
            }
            res.add(minDoc);
            res.add(wfIdfSum);
        }
        return res;
    }

    private void mergeAllBlocks() throws IOException {
        System.out.println("Merging blocks");

        int bytePosTerm = 0, bytePosList = 0;


        ArrayList<BufferedReader> readers = new ArrayList<>();
        ArrayList<String> currLines = new ArrayList<>();
        OutputStream outputDictionary, outputPostings;
        try {
            outputDictionary = new FileOutputStream("src/dictionary.txt");
            outputPostings = new FileOutputStream("src/postings.f");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for(int i = 1;i<=this.blockNumber;i++){
            BufferedReader reader;
            try {
                reader = new BufferedReader(new FileReader("src/blocks/block"+i+".txt"));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            readers.add(reader);
            try {
                currLines.add(reader.readLine());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


        while (!readers.isEmpty()) {

            String minString = getTermFromLine(currLines.get(0));
            for (int i = 0; i < readers.size(); i++) {
                String term = getTermFromLine(currLines.get(i));
                if (term.compareTo(minString) < 0) {
                    minString = term;
                }
            }
            ArrayList<Integer> postings = new ArrayList<>();
            for (int i = 0; i < readers.size(); i++) {
                String term = getTermFromLine(currLines.get(i));
                if (term.equals(minString)) {

                    postings.addAll(getPostingFromLine(currLines.get(i)));

                    String next;
                    if ((next = readers.get(i).readLine()) != null) {
                        currLines.set(i, next);
                    } else {
                        readers.remove(i);
                        currLines.remove(i);
                        i--;
                    }

                }
            }
            outputDictionary.write(minString.getBytes(encoding));
            outputPostings.write(encode(postings));
            dictionaryPositions.add(new DictionaryPosition(bytePosTerm, bytePosList, Math.log(docId / (postings.size() / 2))));
            bytePosTerm += minString.getBytes(encoding).length;
            bytePosList += encode(postings).length;

        }
        dictionaryEnd = bytePosTerm;
        postingsEnd = bytePosList;
    }

    private String getTermFromLine(String line){
        if (line==null)return null;
        return line.split(" : ")[0];
    }
    private ArrayList<Integer> getPostingFromLine(String line){
        if (line==null)return null;
        return decode(Base64.getDecoder().decode(line.split(" : ")[1]));
    }

    private void createBlocks(){
        System.out.println("Creating blocks");
        while (directoryIterator.hasNext())createBlock();

    }
    private void createBlock()  {
        long initialMemory = java.lang.Runtime.getRuntime().freeMemory();
        long usedMemory = 0;
        Map<String, ArrayList<Integer>> partialDictionary = new HashMap<>();
        while((usedMemory < this.memorySize) && this.directoryIterator.hasNext()){

            Path p = directoryIterator.next();


            docId++;
            if (docId%1000 == 0){
                System.out.println("Documents indexed: " + docId);
                System.out.println((System.nanoTime()-startTime)/1000000000 + " seconds to index");
                System.out.println(getGarbageCollectionTime() + " in GC");
                System.out.println(blockNumber + " block");
            }

            if (p.toString().endsWith(".txt")){
                tokenizer.setFilename(p);
                tokenizer.readDocument(encoding);
                for (int i = 0; i < tokenizer.tokens.size(); i++){
                    addToDictionary(partialDictionary,tokenizer.tokens.get(i),docId);
                }
            }
            usedMemory = initialMemory - java.lang.Runtime.getRuntime().freeMemory();
        }

        this.blockNumber++;
        writeBlockToFile(partialDictionary,blockNumber);

    }
    private void addToDictionary(Map<String,ArrayList<Integer>> dictionary,String t, int docId){
        if (!t.equals(" ") && !t.equals("'")){
            if (dictionary.get(t)==null){
                ArrayList<Integer> temp = new ArrayList<>();
                temp.add(docId);
                temp.add(1);
                dictionary.put(t, temp);
            }
            else {
                ArrayList<Integer> temp = dictionary.get(t);
                addToPosting(temp,docId,1);
                dictionary.put(t, temp);
            }
        }
    }
    private void addToPosting(ArrayList<Integer> posting, int docId, int num){
        if (docId > posting.get(posting.size()-2)){
            posting.add(docId);
            posting.add(num);
            return;
        }
        if (docId < posting.get(0)){
            posting.add(0, docId);
            posting.add(1,num);
            return;
        }
        int low = 0;
        int high = posting.size()-2;

        while (low <= high) {
            int mid = low  + ((high - low) / 2);
            mid -= mid%2;
            if (posting.get(mid) < docId) {
                low = mid + 2;
            } else if (posting.get(mid) > docId) {
                high = mid - 2;
            } else if (posting.get(mid) == docId) {
                posting.set(mid+1, posting.get(mid+1)+num);
                return;
            }
        }
        posting.add(low, docId);
        posting.add(low+1,num);
    }
    private void writeBlockToFile(Map<String,ArrayList<Integer>> dictionary, int block){
        try {

            FileOutputStream fileOutputStream = new FileOutputStream("src/blocks/block"+block+".txt");
            //System.out.println("Writing block "+ block);
            dictionary.remove("");
            ArrayList<String> keys = new ArrayList();
            keys.addAll(dictionary.keySet());
            Collections.sort(keys);
            for(int i = 0; i < keys.size(); i++){
                fileOutputStream.write((keys.get(i) + " : ").getBytes(encoding));
                fileOutputStream.write(Base64.getEncoder().encodeToString(encode(dictionary.get(keys.get(i)))).getBytes());
                fileOutputStream.write(("\n").getBytes(encoding));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static byte[] encodeNumber(int n) {
        if (n == 0) {
            return new byte[]{0};
        }
        int i = (int) (log(n) / log(128)) + 1;
        byte[] rv = new byte[i];
        int j = i - 1;
        do {
            rv[j--] = (byte) (n % 128);
            n /= 128;
        } while (j >= 0);
        rv[i - 1] += 128;
        return rv;
    }

    public static byte[] encode(List<Integer> numbers) {
        ByteBuffer buf = ByteBuffer.allocate(numbers.size() * (Integer.SIZE / Byte.SIZE));
        for (Integer number : numbers) {
            buf.put(encodeNumber(number));
        }
        buf.flip();
        byte[] rv = new byte[buf.limit()];
        buf.get(rv);
        return rv;
    }

    public static ArrayList<Integer> decode(byte[] byteStream) {
        ArrayList<Integer> numbers = new ArrayList<>();
        int n = 0;
        for (byte b : byteStream) {
            if (b == 0 && n == 0)  { numbers.add(0); }
            else if ((b & 0xff) < 128) {
                n = 128 * n + b;
            } else {
                int num = (128 * n + ((b - 128) & 0xff));
                numbers.add(num);
                n = 0;
            }
        }
        return numbers;
    }
}
