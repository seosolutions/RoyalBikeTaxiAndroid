package com.ryanwhitell.royalbiketaxi.Controller.Models;

/**
 * Created by Ryan on 5/25/2016.
 */
public class Driver {

    public String name;
    public String password;
    public String phoneNumber;

    public Driver() {
    }

    public Driver(String name, String password, String phoneNumber) {
        this.name = name;
        this.password = password;
        this.phoneNumber = phoneNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
