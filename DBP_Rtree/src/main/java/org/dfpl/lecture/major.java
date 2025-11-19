package org.dfpl.lecture;

import java.util.ArrayList;

public class major {
    public static void main(String[] args) {
        MyArrayList list = new MyArrayList();
        for(int i=0;i<10;i++){
            list.add(i);
        }
        System.out.println(list);
        list.remove(Integer.valueOf(0));
        System.out.println(list);
    }
}