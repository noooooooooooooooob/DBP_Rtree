package org.dfpl.lecture;

import java.util.*;

public class MyArrayList implements List<Integer> {
	private Integer[] data;
    private int size;

	public MyArrayList(){
        size = 0;
        data = new Integer[size];
    }
    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        if(size == 0) return true;
        return false;
    }

    @Override
    public boolean contains(Object o) {
        if(!(o instanceof Integer)) return false;
        Integer check = (Integer)o;
        for(int i = 0; i < size; i++){
            if(check == data[i]) return true;
        }
        return false;
    }

    @Override
    public Iterator<Integer> iterator() {
        return null;
    }

    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return null;
    }

    @Override
    public boolean add(Integer integer) {
        Integer[] newData = new Integer[++size];
//        System.arraycopy(data, 0, newData, 0, size);
        for(int i = 0; i < size - 1; i++)
        {
            newData[i] = data[i];
        }
        newData[size - 1] = integer;
        data = newData;
        return true;
    }

    @Override
    public boolean remove(Object o) {
        if(!(o instanceof Integer)) return false;
        Integer check = (Integer)o;
        for(int i = 0; i < size; i++){
            if(check == data[i])
            {
                for(int j = i; j < size - 1; j++)
                {
                    data[j] = data[j+1];
                }
                size--;
                Integer[] newdata = new Integer[size];
                for(int k=0; k < size; k++)
                {
                    newdata[k] = data[k];
                }
                data = newdata;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends Integer> c) {
        return false;
    }

    @Override
    public boolean addAll(int index, Collection<? extends Integer> c) {
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {
        size = 0;
        data = new Integer[size];
    }

    @Override
    public Integer get(int index) {
        if(index < 0 || index >= size) return null;
        return data[index];
    }

    @Override
    public Integer set(int index, Integer element) {
        if(index < 0 || index >= size) return 0;
        data[index] = element;
        return data[index];
    }

    @Override
    public void add(int index, Integer element) {

    }

    @Override
    public Integer remove(int index) {
        return 0;
    }

    @Override
    public int indexOf(Object o) {
        return 0;
    }

    @Override
    public int lastIndexOf(Object o) {
        return 0;
    }

    @Override
    public ListIterator<Integer> listIterator() {
        return null;
    }

    @Override
    public ListIterator<Integer> listIterator(int index) {
        return null;
    }

    @Override
    public List<Integer> subList(int fromIndex, int toIndex) {
        return List.of();
    }

    @Override
    public String toString() {
        String result = "";
        for(var d : data)
        {
            result += " " + d.toString();
        }
        return result;
    }
}
