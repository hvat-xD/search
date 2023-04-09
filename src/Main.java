
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;


public class Main {
    public static int getDocFromPath(Path p){
        String num = p.toString().replaceAll("[^1-9]", "");
        return Integer.parseInt(num);
    }

    public static void main(String[] args) {

        SPIMI spimi;
        try {
            spimi = new SPIMI("src/cranCol",50000000 ,true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        spimi.generateIndex();

//        System.out.println("Enter your query. Write STOP to end search");
//        Scanner sc = new Scanner(System.in);
//        while (true){
//            String query = sc.nextLine();
//            if (query.equals("STOP"))break;
//            try {
//                System.out.println(query + " : " + spimi.search(query, 10));
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }



    }
}
