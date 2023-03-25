
import java.io.IOException;



public class Main {

    public static void main(String[] args) {

        SPIMI spimi;
        try {
            spimi = new SPIMI("src/collection1",50000000 ,false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        spimi.generateIndex();
        System.out.println(spimi.search("kinda mid"));
    }
}
