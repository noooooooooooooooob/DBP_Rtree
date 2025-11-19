package org.dfpl.lecture.dataloading;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;

public class JSONParser {
    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader("C:\\Users\\pisas\\Desktop\\DBP\\borrower.txt"));
        JSONObject obj = new JSONObject();
        obj.put("customer_name","Adams");
        obj.put("account_number","L-16");

        Iterator<String> iterator = obj.keys();
        String next;
        while(iterator.hasNext()){
            next = iterator.next();
            System.out.println(next);
        }
    }
}
