package ru.otus.java.basic.model;

import java.time.LocalDateTime;

public class Ban {
    private int userId;
    private LocalDateTime banEndTime;

    public Ban(int userId, LocalDateTime banEndTime) {
        this.userId = userId;
        this.banEndTime = banEndTime;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public LocalDateTime getBanEndTime() {
        return banEndTime;
    }

    public void setBanEndTime(LocalDateTime banEndTime) {
        this.banEndTime = banEndTime;
    }

    @Override
    public String toString() {
        return "Ban{" +
                "userId=" + userId +
                ", banEndTime=" + banEndTime +
                '}';
    }
}
