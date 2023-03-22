import java.io.Serializable;
import java.util.Set;

public class DictionaryPosition implements Serializable {
    int bytePosTerm;
    int bytePosList;

    public DictionaryPosition(int bytePos, int bytePosList) {
        this.bytePosTerm = bytePos;
        this.bytePosList = bytePosList;
    }

}
