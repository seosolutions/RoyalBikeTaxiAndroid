package com.ryanwhitell.royalbiketaxi.Controller.Models;

import com.google.android.gms.maps.model.LatLng;

/**
 * Created by Ryan on 6/2/2016.
 */
public class DriverLocation implements Comparable{
    public String name;
    public LatLng location;
    public Double distance;

    public DriverLocation() {
    }

    public DriverLocation(LatLng location, String name) {
        this.location = location;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LatLng getLocation() {
        return location;
    }

    public void setLocation(LatLng location) {
        this.location = location;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(LatLng customerLocation) {
        this.distance = Math.sqrt((Math.pow(this.location.latitude - customerLocation.latitude, 2) +
                Math.pow(this.location.longitude - customerLocation.longitude, 2)));
    }

    @Override
    public int compareTo(Object another) {
        DriverLocation other = (DriverLocation) another;
        if (this.distance <= other.getDistance()) return -1;
        else return 1;
    }
}
