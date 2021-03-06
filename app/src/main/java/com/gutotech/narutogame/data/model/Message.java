package com.gutotech.narutogame.data.model;

import androidx.annotation.Nullable;

public class Message {
    private String id;
    private String sender;
    private String message;

    public Message() {
    }

    public Message(String sender, String message) {
        this.sender = sender;
        this.message = message;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof Message)) {
            return false;
        }
        return getId().equals(((Message) obj).getId());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
