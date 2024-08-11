package ru.otus.java.basic;

import ru.otus.java.basic.model.Role;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Timer;
import java.util.TimerTask;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;
    private LocalDateTime lastActivityTime;
    private Timer inactivityTimer;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public ClientHandler(Server server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.lastActivityTime = LocalDateTime.now();
        this.inactivityTimer = new Timer(true);
        startInactivityTimer();

        new Thread(() -> {
            try {
                System.out.println("Подключился новый клиент");
                while (true) {
                    lastActivityTime = LocalDateTime.now();
                    String message = in.readUTF();
                    if (message.equals("/exit")) {
                        sendMessage("/exitok");
                        return;
                    }
                    if (message.startsWith("/auth ")) {
                        String[] elements = message.split(" ");
                        if (elements.length != 3) {
                            sendMessage("Неверный формат команды /auth");
                            continue;
                        }
                        if (server.getUserService().authenticate(this, elements[1], elements[2])) {
                            break;
                        }
                        continue;
                    }
                    if (message.startsWith("/register ")) {
                        String[] elements = message.split(" ");
                        if (elements.length != 4) {
                            sendMessage("Неверный формат команды /register");
                            continue;
                        }
                        if (server.getUserService().registration(this, elements[1], elements[2], elements[3])) {
                            break;
                        }
                        continue;
                    }
                    sendMessage("Перед работой с чатом необходимо выполнить аутентификацию '/auth login password' или регистрацию '/register login password username'");
                }
                while (true) {
                    String message = in.readUTF();
                    lastActivityTime = LocalDateTime.now();
                    if (server.getUserService().isUserBanned(username)) {
                        sendMessage("Вы забанены и не можете отправлять сообщения.");
                        continue;
                    }
                    if (message.startsWith("/")) {
                        if (message.equals("/exit")) {
                            sendMessage("/exitok");
                            break;
                        } else if (message.startsWith("/w ")) {
                            String[] parts = message.split(" ", 3);
                            if (parts.length == 3) {
                                String recipient = parts[1];
                                String privateMessage = parts[2];
                                server.sendPrivateMessage(recipient, username + " (личное сообщение): " + privateMessage);
                            } else {
                                sendMessage("Неправильный формат личного сообщения. Используйте: /w <username> <message>");
                            }
                        } else if (message.startsWith("/kick ")) {
                            String[] parts = message.split(" ", 2);
                            if (parts.length == 2) {
                                String userToKick = parts[1];
                                Role role = server.getUserService().getUserRole(username);
                                if ("ADMIN".equals(role.getName())) {
                                    server.kickUser(userToKick);
                                } else {
                                    sendMessage("У вас нет прав для выполнения этой команды.");
                                }
                            } else {
                                sendMessage("Неправильный формат команды. Используйте: /kick <username>");
                            }
                        } else if (message.equals("/activelist")) {
                            sendMessage("Активные пользователи: " + String.join(", ", server.getActiveClients()));
                        } else if (message.startsWith("/ban ")) {
                            String[] parts = message.split(" ", 3);
                            if (parts.length >= 2) {
                                String userToBan = parts[1];
                                Role role = server.getUserService().getUserRole(username);
                                if ("ADMIN".equals(role.getName())) {
                                    long banDuration = parts.length == 3 ? Long.parseLong(parts[2]) : -1;
                                    server.getUserService().banUser(userToBan, banDuration);
                                } else {
                                    sendMessage("У вас нет прав для выполнения этой команды.");
                                }
                            } else {
                                sendMessage("Неправильный формат команды. Используйте: /ban <username> [duration_in_minutes]");
                            }
                        } else if (message.equals("/shutdown")) {
                            Role role = server.getUserService().getUserRole(username);
                            if ("ADMIN".equals(role.getName())) {
                                server.shutdown();
                            } else {
                                sendMessage("У вас нет прав для выполнения этой команды.");
                            }
                        }
                        else if (message.startsWith("/changenick ")) {
                            String[] parts = message.split(" ", 2);
                            if (parts.length == 2) {
                                String newUsername = parts[1];
                                if (server.getUserService().isUsernameAlreadyExist(newUsername)) {
                                    sendMessage("Имя пользователя уже занято.");
                                } else {
                                    String oldUsername = username;
                                    server.getUserService().changeUsername(oldUsername, newUsername);
                                    setUsername(newUsername);
                                    sendMessage("Ваше имя пользователя изменено на: " + newUsername);
                                    server.broadcastMessage(oldUsername + " изменил имя на " + newUsername);
                                }
                            } else {
                                sendMessage("Неправильный формат команды. Используйте: /changenick <new_username>");
                            }
                        }
                        continue;
                    }
                    server.broadcastMessage(username + ": " + message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                disconnect();
            }
        }).start();
    }

    public void sendMessage(String message) {
        try {
            System.out.println("Отправка сообщения: " + message);
            out.writeUTF(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        server.unsubscribe(this);
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startInactivityTimer() {
        inactivityTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (LocalDateTime.now().minusMinutes(20).isAfter(lastActivityTime)) {
                    sendMessage("Вы были отключены из-за неактивности.");
                    disconnect();
                }
            }
        }, 0, 60 * 1000);
    }
}
