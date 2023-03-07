
import java.io.IOException;


public class Main {
    public static void main(String[] args) {
        SPIMI spimi;
        try {
            spimi = new SPIMI("src/collection2",65000000 );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        spimi.generateIndex();
        System.out.println(spimi.readAll());
    }
}