import java.util.ArrayList;

public class DockVector {
    int docId;
    int len;
    long posInDoc;
    ArrayList<DockVector> children;

    public DockVector(long posInDoc, int docId, int len, boolean leader) {
        this.len = len;
        this.posInDoc = posInDoc;
        this.docId = docId;
        if (leader)children = new ArrayList<>();
    }
}
