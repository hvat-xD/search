
import java.io.IOException;


public class Main {
    public static void main(String[] args) {
        SPIMI spimi;
        try {
            spimi = new SPIMI("src/collection1",65000000 );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        spimi.generateIndex();
    }
}