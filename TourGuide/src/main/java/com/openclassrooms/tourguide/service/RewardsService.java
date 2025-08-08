package com.openclassrooms.tourguide.service;

import java.util.*;
import java.util.concurrent.*;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.domain.model.User;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;
	private static final ForkJoinPool forkJoinPool = new ForkJoinPool(64);
	private static final int BATCH_SIZE = 1000;

	// proximity in miles
	private int attractionProximityRange = 200;
	private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;

	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final List<Attraction> cachedAttractions;
	private final Map<String, Integer> rewardCache = new ConcurrentHashMap<>();

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
		cachedAttractions = gpsUtil.getAttractions();
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}
	public long getProximityBuffer() {
		return proximityBuffer;
	}
	
	public void calculateRewards(User user) {
		List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
		Set<String> rewardedAttractions = new HashSet<>();

		for (VisitedLocation visitedLocation : userLocations) {
			for (Attraction attraction : cachedAttractions) {

				if (!rewardedAttractions.contains(attraction.attractionName)
						&& nearAttraction(visitedLocation, attraction))
				{
					String cacheKey = attraction.attractionId + "_" + user.getUserId();
					int points = rewardCache.computeIfAbsent(cacheKey, k ->
							     rewardsCentral.getAttractionRewardPoints(
										 attraction.attractionId, user.getUserId()
								 ));
					user.addUserReward(new User.UserReward(visitedLocation, attraction, points));
					rewardedAttractions.add(attraction.attractionName);
				}
			}
		}
	}
	public void calculateRewardsForMultipleUsers(List<User> users) {
		for (int batch = 0; batch < users.size(); batch += BATCH_SIZE) {
			int end = Math.min(batch + BATCH_SIZE, users.size());
			List<User> batchUsers = users.subList(batch, end);

			List<CompletableFuture<Void>> futures = batchUsers.stream()
					.map(user ->
							CompletableFuture.runAsync(() ->
							calculateRewards(user), forkJoinPool))
					.toList();
			CompletableFuture.allOf(
					futures.toArray(new CompletableFuture[0]))
					.join();
		}
	}
	
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) <= attractionProximityRange;
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) <= proximityBuffer;
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
        return STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
	}

	public void shutdown() {
		forkJoinPool.shutdown();
	}

}
