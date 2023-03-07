import java.util.Set;

public class DictionaryPosition {
    int bytePos;
    Set<Integer> positions;

    public DictionaryPosition(int bytePos, Set<Integer> positions) {
        this.bytePos = bytePos;
        this.positions = positions;
    }

    public int getBytePos() {
        return bytePos;
    }

    public Set<Integer> getPositions() {
        return positions;
    }
}
