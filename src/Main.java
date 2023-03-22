
import java.io.IOException;
import java.sql.Time;
import java.util.Timer;
import java.util.concurrent.TimeUnit;


public class Main {
    public static void main(String[] args) {
        long startTime = System.nanoTime();
        SPIMI spimi;
        try {
            spimi = new SPIMI("src/collection2",6500000 ,true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        spimi.generateIndex();
        System.out.println((System.nanoTime()-startTime)/1000000000 + " seconds to index");
    }
}