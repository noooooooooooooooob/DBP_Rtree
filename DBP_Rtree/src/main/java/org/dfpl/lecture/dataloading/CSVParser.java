package org.dfpl.lecture.dataloading;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;

public class CSVParser {
    public static void main(String[] args) throws Exception {
        ArrayList<Borrower> list = new ArrayList<Borrower>();

        BufferedReader br = new BufferedReader(new FileReader("C:\\Users\\pisas\\Desktop\\DBP\\borrower.txt"));
        String line;
        while (true) {
            line = br.readLine();
            if(line == null) break;
            String[] elements = line.split("\t");

            String name = elements[0];
            String loanNumber = elements[1];
            Borrower borrower = new Borrower(name, loanNumber);
            list.add(borrower);
        }
        list.forEach(System.out::println);
        br.close();
    }
}
