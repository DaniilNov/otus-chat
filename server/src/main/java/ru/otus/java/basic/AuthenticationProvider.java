package ru.otus.java.basic;

public interface AuthenticationProvider {
    void initialize();

    boolean authenticate(ClientHandler clientHandler, String login, String password);

    boolean registration(ClientHandler clientHandler, String login, String password, String username);

    boolean removeUserByLogin(String login);

    Role getUserRole(String username);
}
