package com.example.soundfriends.utils;

import java.util.UUID;

public class uuid {
    public static String createTransactionID() {
        return UUID.randomUUID().toString().replaceAll("-", "").toUpperCase();
    }
}
