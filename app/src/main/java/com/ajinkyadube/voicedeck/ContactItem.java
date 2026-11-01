package com.ajinkyadube.voicedeck;

import android.net.Uri;

public class ContactItem {
    public long id;
    public String name = "";
    public String phone = "";
    public String email = "";
    public String company = "";
    public String birthday = "";
    public String address = "";
    public Uri photo;

    public String initials() {
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 0 || name.trim().isEmpty()) return "?";
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}
