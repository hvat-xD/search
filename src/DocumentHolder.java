import java.nio.file.Path;

public class DocumentHolder {
    Path path;
    int docId;
    double wfIdf;

    public DocumentHolder(Path path, int docId, double wfIdf) {
        this.path = path;
        this.docId = docId;
        this.wfIdf = wfIdf;
    }
    @Override
    public String toString(){
        return path.toString()+ " " + docId + " " + wfIdf;
    }
}
