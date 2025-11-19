package org.dfpl.lecture.database.assignment.assignment21011755;

public class LibraryBook {
    String title;
    String author;
    String publisher;
    String pubYear;

    public LibraryBook(String title, String author, String publisher, String pubYear) {
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.pubYear = pubYear;
    }

    @Override
    public String toString() {
        return title + "," + author + "," + publisher + "," + pubYear;
    }
}
