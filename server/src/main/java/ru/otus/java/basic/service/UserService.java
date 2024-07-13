package ru.otus.java.basic.service;

import ru.otus.java.basic.ClientHandler;
import ru.otus.java.basic.model.Role;
import ru.otus.java.basic.model.User;

import java.util.List;

public interface UserService extends AutoCloseable {
    void initialize();

    List<User> getAll();

    boolean isAdmin(int userId);

    boolean authenticate(ClientHandler clientHandler, String login, String password);

    boolean registration(ClientHandler clientHandler, String login, String password, String username);

    boolean removeUserByLogin(String login);

    Role getUserRole(String username);

}
