package ru.otus.java.basic;

import ru.otus.java.basic.service.UserService;
import ru.otus.java.basic.service.UserServiceJdbcImpl;

import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private int port;
    private List<ClientHandler> clients;
    private UserService userService;

    public UserService getUserService() {
        return userService;
    }

    public Server(int port) throws SQLException {
        this.port = port;
        this.clients = new ArrayList<>();
        this.userService = new UserServiceJdbcImpl(this);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Сервер запущен на порту: " + port);
            userService.initialize();
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(this, socket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void subscribe(ClientHandler clientHandler) {
        broadcastMessage("В чат зашел: " + clientHandler.getUsername());
        clients.add(clientHandler);
    }

    public synchronized void unsubscribe(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        broadcastMessage("Из чата вышел: " + clientHandler.getUsername());
    }

    public synchronized void broadcastMessage(String message) {
        for (ClientHandler c : clients) {
            c.sendMessage(message);
        }
    }

    public boolean isUsernameBusy(String username) {
        for (ClientHandler c : clients) {
            if (c.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void sendPrivateMessage(String recipientUsername, String message) {
        for (ClientHandler client : clients) {
            if (client.getUsername().equals(recipientUsername)) {
                client.sendMessage(message);
                break;
            }
        }
    }

    public synchronized void kickUser(String username) {
        for (ClientHandler client : clients) {
            if (client.getUsername().equals(username)) {
                client.sendMessage("Вы были отключены от чата администратором.");
                client.disconnect();
                break;
            }
        }
    }
}
