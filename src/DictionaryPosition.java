import java.io.Serializable;
import java.util.Set;

public class DictionaryPosition implements Serializable {
    double idf;
    int bytePosTerm;
    int bytePosList;

    public DictionaryPosition(int bytePos, int bytePosList, double idf) {
        this.bytePosTerm = bytePos;
        this.bytePosList = bytePosList;
        this.idf = idf;
    }
    public double idf(){
        return idf;
    }

}
