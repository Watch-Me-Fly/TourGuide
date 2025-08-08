package com.openclassrooms.tourguide.service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}
	public long getProximityBuffer() {return proximityBuffer;}
	
	public void calculateRewards(User user) {
		List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = new ArrayList<>(gpsUtil.getAttractions());
		Set<String> rewardedAttractions = new HashSet<>();

		for (VisitedLocation visitedLocation : userLocations) {
			for (Attraction attraction : attractions) {

				boolean isNotYetAwarded = !rewardedAttractions.contains(attraction.attractionName);

				if (isNotYetAwarded && nearAttraction(visitedLocation, attraction))
				{
					int nbOfPoints = getRewardPoints(attraction, user.getUserId());
					user.addUserReward(new UserReward(visitedLocation, attraction, nbOfPoints));
					rewardedAttractions.add(attraction.attractionName);
				}
			}
		}
	}
	public void calculateRewardsForMultipleUsers(List<User> users) {
		List<CompletableFuture<Void>> futures = users.stream()
				.map(user -> CompletableFuture.runAsync(() -> {
					calculateRewards(user);
				}))
				.toList();
		CompletableFuture
				.allOf(futures.toArray(new CompletableFuture[0]))
				.join();
	}
	
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}

	public int getRewardPoints(Attraction attraction, UUID userId) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, userId);
	}
	
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}

}
