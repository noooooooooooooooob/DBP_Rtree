package org.dfpl.lecture;

import java.sql.*;

public class WarmingUp {
    public static void main(String[] args) throws Exception{
        String connectionString = "jdbc:mariadb://localhost:3306";
        String username = "root";
        String password = "1234";
        Connection con = DriverManager.getConnection(connectionString, username, password);
        Statement stmt = con.createStatement();

        stmt.executeUpdate("CREATE OR REPLACE DATABASE dbp00;");
        stmt.executeUpdate("USE dbp00;");
        stmt.executeUpdate("CREATE TABLE student (id INT,name VARCHAR(50),department VARCHAR(50));");
        stmt.executeUpdate("INSERT INTO student VALUES (1,'John','SW');");

        ResultSet rs = stmt.executeQuery("SELECT * FROM student;");
        while(rs.next())
        {
            int id = rs.getInt(1);
            String name = rs.getString(2);
            String department = rs.getString(3);
            System.out.println(id + " " + name + " " + department + " ");
        }

        stmt.close();

        System.out.println("Connected to the database");
    }
}
