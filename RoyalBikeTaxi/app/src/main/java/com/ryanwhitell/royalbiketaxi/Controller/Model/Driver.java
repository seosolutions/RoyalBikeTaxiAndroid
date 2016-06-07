package com.ryanwhitell.royalbiketaxi.Controller.Model;

/**
 * Created by Ryan on 5/25/2016.
 */
public class Driver {

    public String name;
    public String password;
    public String number;

    public Driver() {
    }

    public Driver(String name, String password, String number) {
        this.name = name;
        this.password = password;
        this.number = number;
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

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }
}
