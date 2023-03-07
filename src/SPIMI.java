import com.sun.jdi.ByteValue;
import com.sun.security.jgss.GSSUtil;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;



public class SPIMI {
    int blockNumber;
    File collectionBase;
    Map<String, TreeSet<Integer>> dictionary;
    Iterator<Path> directoryIterator;
    Charset encoding = StandardCharsets.UTF_8;
    int memorySize;

    public SPIMI(String path, int memorySize) throws IOException {
        this.blockNumber = 0;
        this.memorySize = memorySize;
        collectionBase = new File(path);
        if (!collectionBase.isDirectory())throw new NotDirectoryException(path);
        directoryIterator = Files.newDirectoryStream(Path.of(path)).iterator();
    }
    public void generateIndex(){
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String,TreeSet<Integer>> readBlockAndConvertToDictionary(Path filePath){

        Map<String, TreeSet<Integer>> blockDictionary = new LinkedHashMap<>();
        try (Stream<String> stream = Files.lines(filePath)) {
            stream.forEach(line -> {
                String term = line.split(" : ")[0];
                String[] p = line.split(" : ")[1].replaceAll("]","").replaceAll("\\[","").split(", ");

                TreeSet<Integer> postings = new TreeSet<>();
                for (String i : p){
                    postings.add(Integer.valueOf(i));
                }
                blockDictionary.put(term, postings);
            } );

        } catch (IOException e) {
            e.printStackTrace();
        }

        return blockDictionary;
    }
    private void mergeAllBlocks() throws IOException {
        System.out.println("Merging blocks");

        ArrayList<BufferedReader> readers = new ArrayList<>();
        ArrayList<String> currLines = new ArrayList<>();
        BufferedWriter writer;
        try {
            writer = new BufferedWriter(new FileWriter("src/dictionary.txt"));
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
            Integer min = 0;
            String minString = getTermFromLine(currLines.get(0));
            for (int i = 0; i < readers.size();i++){
                if (readers.get(i)!=null){
                    if (minString==null){
                        minString = getTermFromLine(currLines.get(i));
                    }
                    else {
                        String term = getTermFromLine(currLines.get(i));
                        if (term.compareTo(minString)<0){
                            min = i;
                            minString = term;
                        }
                    }

                }

            }
            TreeSet<Integer> all = new TreeSet<>();
            flag = false;
            for (int i = 0; i < readers.size();i++){
                if (readers.get(i)!=null){
                    flag = true;
                    String term = getTermFromLine(currLines.get(i));
                    if (term.equals(minString)){
                        all.addAll(getPostingFromLine(currLines.get(i)));
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
            writer.write(minString + " : " + all +"\n");




        }

    }
    private String getTermFromLine(String line){
        if (line==null)return null;
        return line.split(" : ")[0];
    }
    private TreeSet<Integer> getPostingFromLine(String line){
        if (line==null)return null;
        String[] nums = line.split(" : ")[1].replaceAll("\\[", "").replaceAll("]","").split(", ");
        TreeSet<Integer> res = new TreeSet<>();
        for (String s: nums){
            res.add(Integer.valueOf(s));
        }
        return res;
    }

    private void writeDictionary(Map<String,TreeSet<Integer>> dictionary){
        this.blockNumber++;

        Path file = Path.of("src/dictionary.txt");


        dictionary.remove("");

        List<String> keys = new ArrayList<>(dictionary.keySet());

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

    private void createBlocks(){
        System.out.println("Creating blocks");
        while (directoryIterator.hasNext())createBlock();
    }
    private void createBlock(){
        Tokenizer tokenizer = new Tokenizer(null);
        int docId = 0;
        int initialMemory = (int) java.lang.Runtime.getRuntime().freeMemory();
        int usedMemory = 0;
        Map<String, TreeSet<Integer>> partialDictionary = new HashMap<>();
        while(usedMemory < this.memorySize && this.directoryIterator.hasNext()){
            usedMemory = initialMemory - (int) java.lang.Runtime.getRuntime().freeMemory();
            Path p = directoryIterator.next();
            docId++;
            if (p.toString().endsWith(".txt")){
                tokenizer.setFilename(p);
                tokenizer.readDocument(encoding);
                ArrayList<String> tokens = tokenizer.getTokens();
                for (String t:tokens){
                    addToDictionary(partialDictionary,t,docId);
                }
            }
        }
        sortAndWriteBlockToFile(partialDictionary);
    }
    private void addToDictionary(Map<String,TreeSet<Integer>> dictionary,String t, int docId){
        if (!t.equals(" ") && !t.equals("'")){
            if (dictionary.get(t)==null){
                TreeSet<Integer> temp = new TreeSet<>();
                temp.add(docId);
                dictionary.put(t, temp);
            }
            else {
                TreeSet<Integer> temp = dictionary.get(t);
                temp.add(docId);
                dictionary.put(t, temp);
            }
        }
    }
    private void sortAndWriteBlockToFile(Map<String,TreeSet<Integer>> dictionary){
        this.blockNumber++;

        Path file = Path.of("src/blocks/block"+this.blockNumber+".txt");

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
}
