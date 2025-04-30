package com.openclassrooms.tourguide;
public class NearbyAttractionDto {
    public String attractionName;
    public double attractionLatitude;
    public double attractionLongitude;
    public double userLatitude;
    public double userLongitude;
    public double distance;
    public int rewardPoints;

    public NearbyAttractionDto(String attractionName, double attractionLat, double attractionLong,
                               double userLat, double userLong, double distance, int rewardPoints) {
        this.attractionName = attractionName;
        this.attractionLatitude = attractionLat;
        this.attractionLongitude = attractionLong;
        this.userLatitude = userLat;
        this.userLongitude = userLong;
        this.distance = distance;
        this.rewardPoints = rewardPoints;
    }
}
