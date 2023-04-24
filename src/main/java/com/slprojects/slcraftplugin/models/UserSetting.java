package com.slprojects.slcraftplugin.models;

import org.bjloquent.Model;

public class UserSetting extends Model {
    private String uuid;
    private String name;
    private String value;

    public UserSetting() {
        super.tableName = "site_userSetting";
        super.primaryKeyName = "uuid";
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
