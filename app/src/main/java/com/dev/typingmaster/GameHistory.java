package com.app.typingmaster;

public class GameHistory {
    private String mode;
    private int duration;
    private int score;
    private String dateTime; // This replaces timestamp

    public GameHistory(String mode, int duration, int score, String dateTime) {
        this.mode = mode;
        this.duration = duration;
        this.score = score;
        this.dateTime = dateTime;
    }

    public String getMode() {
        return mode;
    }

    public int getDuration() {
        return duration;
    }

    public int getScore() {
        return score;
    }

    public String getDateTime() {
        return dateTime;
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }
}