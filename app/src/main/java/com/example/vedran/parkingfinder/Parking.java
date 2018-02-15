package com.example.vedran.parkingfinder;

import java.io.Serializable;
import java.text.DecimalFormat;

public class Parking implements
        Serializable {

    private String address;
    private Double latitude;
    private Double longitude;
    private Float distance;

    public Parking(String address, Double latitude, Double longitude, Float distance) {
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distance = distance;
    }

    public Parking() {
        this.address = "N/A";
        this.distance = 00.00f;
    }

    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getDistance() {
        DecimalFormat df = new DecimalFormat("##.00");
        if(distance > 1000.00f){
            return df.format(distance/1000.00f) + " km";
        }
        else{
            return df.format(distance) + " m";
        }
    }
    public Float getFloatDistance(){
        return distance;
    }

    public void setAddress(String address) { this.address = address; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public void setDistance(Float distance) { this.distance = distance; }

}
