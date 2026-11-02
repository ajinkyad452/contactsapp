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
        return ContactInitials.fromName(name);
    }
}
