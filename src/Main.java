
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
            spimi = new SPIMI("src/cranCol",50000000 ,false);
            spimi.generateIndex();
            BufferedReader bufferedReaderQry = new BufferedReader(new FileReader("src/cran.qry"));
            BufferedReader bufferedReaderEv = new BufferedReader(new FileReader("src/cranqrel"));
            String queryP = bufferedReaderQry.readLine();
            String evalL = bufferedReaderEv.readLine();
            ArrayList<String> query = new ArrayList<>();
            int cur = 1;
            while ((queryP = bufferedReaderQry.readLine())!=null){
                if (queryP.startsWith(".I")){
                    String q = "";
                    for (String s : query){
                        q= q+s;
                    }
                    q = q.substring(0, q.length()-1);

                    ArrayList<Integer> relevantDocs = new ArrayList<>();
                    int counter = 0;
                    while (Integer.parseInt(evalL.split("\\s+")[0]) == cur){

                        String[] what = evalL.split(" ");
                        if (!what[2].equals("-1")){
                            relevantDocs.add(Integer.parseInt(what[1]));
                            counter++;
                        }
                        evalL = bufferedReaderEv.readLine();
                    }

                    ArrayList<Path> mine = spimi.search(q, (counter-1)*2);
                    ArrayList<Integer> mineInts = new ArrayList<>();
                    for (Path p : mine){
                        mineInts.add(getDocFromPath(p));
                    }

                    double tp = 0, fp = 0;
                    for (int m : mineInts){
                        if (relevantDocs.contains(m))tp++;
                        else fp++;
                    }
                    double P = tp/(tp+fp);
                    double R = tp/(double) (counter-1);
                    System.out.println("query #" + cur + " P = " + P + " R = " + R);
                    query.clear();
                    cur++;
                }
                else if (!queryP.startsWith(".")){
                    query.add(queryP);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }



    }
}
