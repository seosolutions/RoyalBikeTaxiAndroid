package com.ryanwhitell.royalbiketaxi.Controller.Model;

/**
 * Created by Ryan on 5/25/2016.
 */
public class Driver {

    public String name;
    public String password;

    public Driver() {
    }

    public Driver(String name, String password) {
        this.name = name;
        this.password = password;
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
}
