import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;




public class Tokenizer {

    private Path fileName;
    private String fileContents;

    public ArrayList<String> tokens;


    public Tokenizer(Path filename) {
        this.fileName = filename;
    }




    public void setFilename(Path filename) {
        this.fileName = filename;
    }





    public void readDocument(Charset charset) {
        try {
            byte[] encoded = Files.readAllBytes(fileName);
            this.fileContents = new String(encoded, charset);
            this.tokenizeDocument();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void tokenizeDocument() {
        tokens = new ArrayList<>();

        tokens.addAll(List.of(fileContents.replaceAll("[^a-zA-Z ]", "").toLowerCase().split("\\s+")));
    }





    @SuppressWarnings("unused")
    private static String apply30stopwords(String tokens){
        tokens = removeStopWords(tokens,30);
        return tokens;
    }

    @SuppressWarnings("unused")
    private static String apply150stopwords(String tokens){
        tokens = removeStopWords(tokens,150);
        return tokens;
    }




    private static String removeStopWords(String tokens, int numberOfStopwords){
        String[] stopwords = { "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any",
                "are", "aren't", "as", "at", "be", "because", "been", "before", "being", "below", "between", "both",
                "but", "by", "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing",
                "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
                "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself",
                "him", "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is",
                "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself", "no",
                "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves",
                "out", "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so",
                "some", "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then",
                "there", "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those",
                "through", "to", "too", "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're",
                "we've", "were", "weren't", "what", "what's", "when", "when's", "where", "where's", "which", "while",
                "who", "who's", "whom", "why", "why's", "with", "won't", "would", "wouldn't", "you", "you'd", "you'll",
                "you're", "you've", "your", "yours", "yourself", "yourselves" };


        //to not break the program if we put bigger amt of stop words
        if(numberOfStopwords >= stopwords.length) numberOfStopwords = stopwords.length;

        for (int i = 0; i < numberOfStopwords; i++) {

            tokens = tokens.replaceAll(stopwords[i], " ");

        }

        return tokens;
    }
}