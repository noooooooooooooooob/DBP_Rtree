package org.dfpl.lecture.database.assignment.assignment21011755;

import java.io.*;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.ArrayList;

public class Assignment {
    public static void main(String[] args) throws Exception {
        runAssignment1();
        runAssignment2();
        runAssignment3();
    }

    // ====================== Assignment 1 ======================
    static void runAssignment1() throws Exception {
        ArrayList<LibraryBook> list = loadBooksFromCSV(
                "d:\\대전광역시 서구_갈마도서관 도서현황_20250826.csv",
                "euc-kr"
        );

        System.out.println("\n Assignment 1 \n");

//        for(int i=0;i<100;i++){ // 너무 많아서 잘리는 거 대비용 테스트
//            System.out.println(list.get(i).toString());
//        }

        for (LibraryBook b : list) {
            System.out.println(b.toString());
        }
    }

    // ====================== Assignment 2 ======================
    static void runAssignment2() throws Exception {
        Connection con = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306",
                "root",
                "1234"
        );
        Statement stmt = con.createStatement();

        System.out.println("\n Assignment 2 \n");

        stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS mydb21011755;");
        stmt.executeUpdate("USE mydb21011755;");
        stmt.executeUpdate("DROP TABLE IF EXISTS library_book;");
        stmt.executeUpdate(
                "CREATE TABLE library_book (" +
                        " title VARCHAR(300)," +
                        " author VARCHAR(200)," +
                        " publisher VARCHAR(200)," +
                        " pub_year VARCHAR(100)" +
                        ");"
        );

        printQueryResult(stmt, "DESCRIBE library_book;");

        stmt.close();
        con.close();
    }
    static void runAssignment3() throws Exception {
        ArrayList<LibraryBook> list = loadBooksFromCSV(
                "d:\\대전광역시 서구_갈마도서관 도서현황_20250826.csv",
                "euc-kr"
        );

        System.out.println("\n Assignment 3 \n");

        Connection con = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306",
                "root",
                "1234"
        );
        Statement stmt = con.createStatement();

        stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS mydb21011755;");
        stmt.executeUpdate("USE mydb21011755;");

        // 테이블 덮어쓰기
        stmt.executeUpdate("DROP TABLE IF EXISTS library_book;");
        stmt.executeUpdate(
                "CREATE TABLE library_book (" +
                        " title VARCHAR(500)," +
                        " author VARCHAR(300)," +
                        " publisher VARCHAR(300)," +
                        " pub_year VARCHAR(100)" +
                        ");"
        );

        PreparedStatement ps = con.prepareStatement(
                "INSERT INTO library_book " +
                        "VALUES (?, ?, ?, ?);"
        );

        for (LibraryBook b : list) {
            ps.setString(1, b.title);
            ps.setString(2, b.author);
            ps.setString(3, b.publisher);
            ps.setString(4, b.pubYear);
            ps.executeUpdate();
        }

        System.out.println("\n[1] 가장 오래된 출판년도 10권\n");
        printQueryResult(stmt,
                "SELECT title, author, publisher, pub_year " +
                        "FROM library_book " +
                        "WHERE pub_year REGEXP '^[0-9]+$' " +
                        "ORDER BY CAST(pub_year AS UNSIGNED) ASC " +
                        "LIMIT 10;"
        );

        System.out.println("\n[2] 출판년도별 도서 수\n");
        printQueryResult(stmt,
                "SELECT pub_year, COUNT(*) AS cnt " +
                        "FROM library_book " +
                        "WHERE pub_year REGEXP '^[0-9]{4}$' " +
                        "GROUP BY pub_year " +
                        "ORDER BY pub_year ASC;"
        );

        ps.close();
        stmt.close();
        con.close();
    }

    static ArrayList<String> parseCSVLine(String line) {
        ArrayList<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes; // 따옴표 토글
                }
            } else if (ch == ',' && !inQuotes) {
                result.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        result.add(cur.toString().trim());

        return result;
    }

    static ArrayList<LibraryBook> loadBooksFromCSV(String path, String encoding) throws Exception {
        BufferedReader br = new BufferedReader(
                new FileReader(path, Charset.forName(encoding))
        );

        ArrayList<LibraryBook> list = new ArrayList<>();
        String line = br.readLine();

        while ((line = br.readLine()) != null) {
            ArrayList<String> colList = parseCSVLine(line);
            if (colList.size() < 4) continue;

            String title     = colList.get(0);
            String author    = colList.get(1);
            String publisher = colList.get(2);
            String pubYear   = colList.get(3);

            list.add(new LibraryBook(title, author, publisher, pubYear));
        }

        br.close();
        return list;
    }

    static void printQueryResult(Statement stmt, String sql) throws Exception {
        ResultSet rs = stmt.executeQuery(sql);
        ResultSetMetaData md = rs.getMetaData();
        int colCount = md.getColumnCount();

        // 컬럼명 출력
        for (int i = 1; i <= colCount; i++) {
            System.out.print(md.getColumnLabel(i) + "\t");
        }
        System.out.println();

        // 데이터 출력
        while (rs.next()) {
            for (int i = 1; i <= colCount; i++) {
                System.out.print(rs.getString(i) + "\t");
            }
            System.out.println();
        }

        rs.close();
    }
}
