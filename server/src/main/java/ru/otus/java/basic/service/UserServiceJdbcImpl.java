package ru.otus.java.basic.service;

import ru.otus.java.basic.ClientHandler;
import ru.otus.java.basic.Server;
import ru.otus.java.basic.model.Ban;
import ru.otus.java.basic.model.Role;
import ru.otus.java.basic.model.User;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class UserServiceJdbcImpl implements UserService {
    private static final String DATABASE_URL = "jdbc:postgresql://localhost:5432/postgres";

    private static final String USERS_QUERY = "SELECT * FROM chat.users";
    private static final String USER_ROLES_QUERY = """
            select r.id, r.name from chat.roles r
            join chat.users_to_roles ur ON r.id = ur.role_id
            WHERE user_id = ?
            ORDER BY id
            """;

    private static final String USER_DELETE_QUERY = "DELETE FROM chat.users u WHERE u.login = ?";
    private static final String USER_CREATE_QUERY = "INSERT INTO chat.users (id, login, password, username) VALUES (?, ?, ?, ?)";
    private static final String USER_ROLE_CREATE_QUERY = "INSERT INTO chat.users_to_roles (user_id, role_id) VALUES (?, ?)";

    private static final String IS_ADMIN_QUERY = """
            select count(1) from chat.roles r
            join chat.users_to_roles ur ON r.id = ur.role_id
            WHERE user_id = ? and r.name = 'ADMIN'
            """;

    private static final String BAN_USER_QUERY = """
                INSERT INTO chat.bans (user_id, ban_end_time)
                VALUES (?, ?)
                ON CONFLICT (user_id)
                DO UPDATE SET ban_end_time = EXCLUDED.ban_end_time
            """;
    private static final String CHECK_BAN_QUERY = "SELECT ban_end_time FROM chat.bans WHERE user_id = ?";

    private static final String CHANGE_USERNAME_QUERY = "UPDATE chat.users SET username = ? WHERE username = ?";

    private final Connection connection;
    private Server server;
    private List<User> allUsers;

    public UserServiceJdbcImpl(Server server) throws SQLException {
        this.server = server;
        this.allUsers = new ArrayList<>();
        connection = DriverManager.getConnection(DATABASE_URL, "postgres", "postgres123");
    }

    @Override
    public void initialize() {
        System.out.println("Сервис аутентификации запущен: Database режим");
    }

    @Override
    public List<User> getAll() {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(USERS_QUERY)) {
                while (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    String login = resultSet.getString(2);
                    String password = resultSet.getString(3);
                    String username = resultSet.getString(4);
                    User user = new User(id, login, password, username);
                    allUsers.add(user);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        try (PreparedStatement ps = connection.prepareStatement(USER_ROLES_QUERY)) {
            for (User user : allUsers) {
                ps.setInt(1, user.getId());
                List<Role> roles = new ArrayList<>();
                try (ResultSet resultSet = ps.executeQuery()) {
                    while (resultSet.next()) {
                        int id = resultSet.getInt("id");
                        String name = resultSet.getString(2);
                        Role role = new Role(id, name);
                        roles.add(role);
                    }
                    user.setRoles(roles);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return allUsers;
    }

    @Override
    public boolean isAdmin(int userId) {
        int flag = 0;
        try (PreparedStatement ps = connection.prepareStatement(IS_ADMIN_QUERY)) {
            ps.setInt(1, userId);
            try (ResultSet resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    flag = resultSet.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return flag == 1;
    }

    @Override
    public boolean authenticate(ClientHandler clientHandler, String login, String password) {
        String authUsername = getUsernameByLoginAndPassword(login, password);
        if (authUsername == null) {
            clientHandler.sendMessage("Некорретный логин/пароль");
            return false;
        }
        if (server.isUsernameBusy(authUsername)) {
            clientHandler.sendMessage("Указанная учетная запись уже занята");
            return false;
        }
        clientHandler.setUsername(authUsername);
        server.subscribe(clientHandler);
        clientHandler.sendMessage("/authok " + authUsername);
        return true;
    }

    @Override
    public boolean registration(ClientHandler clientHandler, String login, String password, String username) {
        if (login.trim().length() < 3 || password.trim().length() < 6 || username.trim().length() < 1) {
            clientHandler.sendMessage("Логин 3+ символа, Пароль 6+ символов, Имя пользователя 1+ символ");
            return false;
        }
        if (isLoginAlreadyExist(login)) {
            clientHandler.sendMessage("Указанный логин уже занят");
            return false;
        }
        if (isUsernameAlreadyExist(username)) {
            clientHandler.sendMessage("Указанное имя пользователя уже занято");
            return false;
        }
        createUser(login, password, username);
        clientHandler.setUsername(username);
        server.subscribe(clientHandler);
        clientHandler.sendMessage("/regok " + username);
        return true;
    }

    @Override
    public boolean removeUserByLogin(String login) {
        boolean isRemoved = false;
        try (PreparedStatement ps = connection.prepareStatement(USER_DELETE_QUERY)) {
            ps.setString(1, login);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                isRemoved = getAll().removeIf(user -> user.getLogin().equals(login));
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
        return isRemoved;
    }


    @Override
    public Role getUserRole(String username) {
        for (User user : getAll()) {
            if (user.getUsername().equals(username)) {
                for (Role role : user.getRoles()) {
                    return role;
                }
            }
        }
        return null;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getUsernameByLoginAndPassword(String login, String password) {
        for (User u : getAll()) {
            if (u.getLogin().equals(login) && u.getPassword().equals(password)) {
                return u.getUsername();
            }
        }
        return null;
    }

    private boolean isLoginAlreadyExist(String login) {
        for (User u : getAll()) {
            if (u.getLogin().equals(login)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isUsernameAlreadyExist(String username) {
        for (User u : getAll()) {
            if (u.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    public void createUser(String login, String password, String username) {
        int randomId = new Random().nextInt(Integer.MAX_VALUE);

        try (PreparedStatement psUser = connection.prepareStatement(USER_CREATE_QUERY);
             PreparedStatement psRole = connection.prepareStatement(USER_ROLE_CREATE_QUERY)) {

            psUser.setInt(1, randomId);
            psUser.setString(2, login);
            psUser.setString(3, password);
            psUser.setString(4, username);
            int userRowsAffected = psUser.executeUpdate();

            if (userRowsAffected > 0) {
                int managerRoleId = 2;
                psRole.setInt(1, randomId);
                psRole.setInt(2, managerRoleId);
                psRole.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Ошибка при создании пользователя", ex);
        }
    }

    @Override
    public void banUser(String username, long durationInMinutes) {
        int userId = getUserIdByUsername(username);
        if (userId == -1) {
            throw new IllegalArgumentException("Пользователь не найден");
        }

        LocalDateTime banEndTime = durationInMinutes > 0 ? LocalDateTime.now().plusMinutes(durationInMinutes)
                : LocalDateTime.of(9999, 12, 31, 23, 59, 59);
        Ban ban = new Ban(userId, banEndTime);
        System.out.println("Блокировка пользователя: " + ban); // Отладочное сообщение

        try (PreparedStatement ps = connection.prepareStatement(BAN_USER_QUERY)) {
            ps.setInt(1, ban.getUserId());
            ps.setTimestamp(2, Timestamp.valueOf(ban.getBanEndTime()));
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Ошибка при блокировке пользователя", ex);
        }
    }

    @Override
    public boolean isUserBanned(String username) {
        int userId = getUserIdByUsername(username);
        if (userId == -1) {
            return false;
        }

        try (PreparedStatement ps = connection.prepareStatement(CHECK_BAN_QUERY)) {
            ps.setInt(1, userId);
            try (ResultSet resultSet = ps.executeQuery()) {
                if (resultSet.next()) {
                    Timestamp banEndTime = resultSet.getTimestamp(1);
                    if (banEndTime != null && banEndTime.toLocalDateTime().isAfter(LocalDateTime.now())) {
                        return true;
                    }
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Ошибка при проверке блокировки пользователя", ex);
        }
        return false;
    }

    private int getUserIdByUsername(String username) {
        for (User user : getAll()) {
            if (user.getUsername().equals(username)) {
                return user.getId();
            }
        }
        return -1;
    }

    @Override
    public void changeUsername(String oldUsername, String newUsername) {
        try (PreparedStatement ps = connection.prepareStatement(CHANGE_USERNAME_QUERY)) {
            ps.setString(1, newUsername);
            ps.setString(2, oldUsername);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Ошибка при изменении имени пользователя", ex);
        }
    }
}
