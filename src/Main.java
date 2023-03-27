
import java.io.IOException;

import java.util.Scanner;


public class Main {

    public static void main(String[] args) {

        SPIMI spimi;
        try {
            spimi = new SPIMI("src/cranCol",50000000 ,true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        spimi.generateIndex();

        System.out.println("Enter your query. Write STOP to end search");
        Scanner sc = new Scanner(System.in);
        while (true){
            String query = sc.nextLine();
            if (query.equals("STOP"))break;
            System.out.println(query + " : " + spimi.search(query));
        }

    }
}
