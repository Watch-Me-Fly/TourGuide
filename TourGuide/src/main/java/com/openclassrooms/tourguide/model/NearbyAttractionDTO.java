package com.openclassrooms.tourguide.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NearbyAttractionDTO {

    public String attractionName;
    public double attractionLatitude;
    public double attractionLongitude;
    public double userLatitude;
    public double userLongitude;
    public double attractionDistance;
    public int rewardPoints;

}