import com.sun.jdi.ByteValue;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.security.jgss.GSSUtil;

import javax.management.MBeanServer;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static java.lang.Math.log;


public class SPIMI {
    int docId = -1;
    int blockNumber;
    File collectionBase;
    ArrayList<DictionaryPosition> dictionaryPositions;
    Iterator<Path> directoryIterator;
    Charset encoding = StandardCharsets.UTF_8;
    int dictionaryEnd;
    int postingsEnd;
    int memorySize;
    boolean forceBuild;
    //ArrayList<Path> filePaths;

    public SPIMI(String path, int memorySize, boolean forceBuild) throws IOException {
        this.dictionaryPositions = new ArrayList<>();
        //this.filePaths = new ArrayList<>();
        this.forceBuild = forceBuild;
        this.blockNumber = 0;
        this.memorySize = memorySize;
        collectionBase = new File(path);
        if (!collectionBase.isDirectory())throw new NotDirectoryException(path);
        directoryIterator = Files.newDirectoryStream(Path.of(path)).iterator();
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
                try {
                    DataOutputStream dataOutputStream = new DataOutputStream(new FileOutputStream("src/changed.txt"));
                    dataOutputStream.writeLong(collectionBase.lastModified());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                rebuildIndex();
            }
        }
        else rebuildIndex();
    }

    private void rebuildIndex() {
        try {
            Files.walk(Path.of("src/blocks"))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            Files.createDirectories(Path.of("src/blocks"));
        } catch (IOException e) {
            try {
                Files.createDirectories(Path.of("src/blocks"));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        createBlocks();
        try {
            mergeAllBlocks();
            serializePositions();
        } catch (IOException e) {
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

    public ArrayList<String> getTerms(){
        ArrayList<String> words = new ArrayList<>();

        for (int i = 0; i <  dictionaryPositions.size() - 1; i++){
            DictionaryPosition cur = dictionaryPositions.get(i);
            DictionaryPosition nex = dictionaryPositions.get(i+1);
            words.add(readFromDictionary(cur.bytePosTerm, nex.bytePosTerm-cur.bytePosTerm));

        }

        words.add(readFromDictionary(dictionaryPositions.get( dictionaryPositions.size()-1).bytePosTerm, dictionaryEnd -dictionaryPositions.get( dictionaryPositions.size()-1).bytePosTerm));
        return words;
    }
    public ArrayList<ArrayList<Integer>> getPostings(){
        ArrayList<ArrayList<Integer>> postings = new ArrayList<>();
        for (int i = 0; i < dictionaryPositions.size() -1; i++){
            DictionaryPosition cur = dictionaryPositions.get(i);
            DictionaryPosition nex = dictionaryPositions.get(i+1);
            postings.add(readFromPostings(cur.bytePosList, nex.bytePosList-cur.bytePosList));
        }
        postings.add(readFromPostings(dictionaryPositions.get( dictionaryPositions.size()-1).bytePosList, postingsEnd - dictionaryPositions.get( dictionaryPositions.size()-1).bytePosList));
        return postings;
    }
    public String readFromDictionary(int posStart, int len){
        RandomAccessFile randomAccessFile;
        byte[] bytes = new byte[len];
        try {
            randomAccessFile = new RandomAccessFile("src/dictionary.txt","r");
            randomAccessFile.seek(posStart);
            randomAccessFile.read(bytes);
            randomAccessFile.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        return new String(bytes,encoding);
    }
    public ArrayList<Integer> readFromPostings(int posStart, int len){
        RandomAccessFile randomAccessFile;
        byte[] bytes = new byte[len];
        try {
            randomAccessFile = new RandomAccessFile("src/postings.f","r");
            randomAccessFile.seek(posStart);
            randomAccessFile.read(bytes);
            randomAccessFile.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return decode(bytes);
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

        boolean flag = true;
        while (flag) {
            String minString = getTermFromLine(currLines.get(0));
            for (int i = 0; i < readers.size();i++){
                if (readers.get(i)!=null){
                    if (minString==null){
                        minString = getTermFromLine(currLines.get(i));
                    }
                    else {
                        String term = getTermFromLine(currLines.get(i));
                        if (term.compareTo(minString)<0){
                            minString = term;
                        }
                    }

                }

            }
            ArrayList<Integer> postings = new ArrayList<>();
            flag = false;
            for (int i = 0; i < readers.size();i++){
                if (readers.get(i)!=null){
                    flag = true;
                    String term = getTermFromLine(currLines.get(i));
                    if (term.equals(minString)){

                        postings.addAll(getPostingFromLine(currLines.get(i)));

                        String next;
                        if ((next = readers.get(i).readLine())!=null){
                            currLines.set(i,next);
                        }
                        else {
                            readers.set(i,null);
                            currLines.set(i,null);

                        }
                    }
                }
            }
            if(minString!=null){
                outputDictionary.write(minString.getBytes(encoding));
                outputPostings.write(encode(postings));
                dictionaryPositions.add(new DictionaryPosition(bytePosTerm, bytePosList));
                bytePosTerm+=minString.getBytes(encoding).length;
                bytePosList+=encode(postings).length;
            }

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
        String[] nums = line.split(" : ")[1].replaceAll("\\[", "").replaceAll("]","").split(", ");
        ArrayList<Integer> res = new ArrayList<>();
        for (String s: nums){
            res.add(Integer.valueOf(s));
        }
        return res;
    }

    private void createBlocks(){
        System.out.println("Creating blocks");
        while (directoryIterator.hasNext())createBlock();

    }
    private void createBlock(){

        Tokenizer tokenizer = new Tokenizer(null);

        int initialMemory = (int) java.lang.Runtime.getRuntime().freeMemory();
        int usedMemory = 0;
        Map<String, ArrayList<Integer>> partialDictionary = new TreeMap<>();
        while(usedMemory < this.memorySize && this.directoryIterator.hasNext()){
            usedMemory = initialMemory - (int) java.lang.Runtime.getRuntime().freeMemory();
            Path p = directoryIterator.next();
            //filePaths.add(p);
            docId++;
            //System.out.println("Documents indexed: " + docId);
            if (p.toString().endsWith(".txt")){
                tokenizer.setFilename(p);
                tokenizer.readDocument(encoding);
                ArrayList<String> tokens = tokenizer.getTokens();
                for (String t:tokens){
                    addToDictionary(partialDictionary,t,docId);
                }
            }
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
        for (int i = 0 ; i < posting.size(); i+=2){
            if (posting.get(i)==docId){
                posting.set(i+1, posting.get(i+1)+num);
                return;
            }
            if (posting.get(i)>docId){
                posting.add(i, docId);
                posting.add(i+1,num);
                return;
            }
        }
        posting.add(docId);
        posting.add(num);
    }
    private void writeBlockToFile(Map<String,ArrayList<Integer>> dictionary, int block){

        Path file = Path.of("src/blocks/block"+block+".txt");

        //System.out.println("Writing block "+ block);


        dictionary.remove("");

        List<String> keys = new ArrayList<>(dictionary.keySet());
        Collections.sort(keys);

        List<String> lines = new ArrayList<>();
        for(String key : keys){
            String index = key + " : " + dictionary.get(key).toString();
            lines.add(index);
        }
        try {
            Files.write(file, lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static byte[] encodeNumber(int n) {
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
        ArrayList<Integer> numbers = new ArrayList<Integer>();
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
