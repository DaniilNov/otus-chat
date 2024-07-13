package ru.otus.java.basic;

import java.sql.SQLException;

public class ServerApplication {
    public static void main(String[] args) throws SQLException {
        new Server(8189).start();
    }
}