import java.util.ArrayList;

public class DockVector {
    int docId;
    int posInDoc;
    ArrayList<DockVector> children;

    public DockVector(int posInDoc, int docId, boolean leader) {
        this.posInDoc = posInDoc;
        this.docId = docId;
        if (leader)children = new ArrayList<>();
    }
}
