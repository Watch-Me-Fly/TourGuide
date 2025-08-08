package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.util.InternalTestHelper;
import com.openclassrooms.tourguide.domain.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.tracking.Tracker;
import com.openclassrooms.tourguide.domain.model.User;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private static final ForkJoinPool forkJoinPool = new ForkJoinPool(64);

	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
		}
		logger.debug("Initializing users");
		initializeInternalUsers();
		logger.debug("Finished initializing users");
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<User.UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		if (user.getVisitedLocations().isEmpty()) {
			return trackUserLocation(user);
		}
		return user.getLastVisitedLocation();
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().toList();
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		// Ensure user has at least one visited location
		if (user.getVisitedLocations().isEmpty()) {
			trackUserLocation(user);
		}
		// get the sum of all points
		int cumulativeRewardPoints = user.getUserRewards().stream()
				                          .mapToInt(User.UserReward::getRewardPoints)
				                          .sum();

		// get all the providers for each attraction
		List<Provider> allProviders = getProvidersPerAttraction(user, cumulativeRewardPoints);
		// limit results to 10 each time
		List<Provider> providers = allProviders.stream()
						.limit(10)
						.toList();

		user.setTripDeals(providers);
		return providers;
	}

	private List<Provider> getProvidersPerAttraction(User user, int cumulativeRewardPoints)  {
		List<Provider> allProviders = new ArrayList<>();
		List<Attraction> attractions = gpsUtil.getAttractions();
		attractions.forEach(attraction -> {
			List<Provider> result = tripPricer.getPrice(
					tripPricerApiKey,
					attraction.attractionId,
					user.getUserPreferences().getNumberOfAdults(),
					user.getUserPreferences().getNumberOfChildren(),
					user.getUserPreferences().getTripDuration(),
					cumulativeRewardPoints);
			allProviders.addAll(result);
		});
		return allProviders;
	}

	public void trackAllUsersLocations(List<User> users) {
		List<CompletableFuture<Void>> futures = users.stream()
				.map(user -> CompletableFuture.runAsync(() ->
						trackUserLocation(user), forkJoinPool))
				.toList();
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
	}

	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation;
		if (user.getVisitedLocations().isEmpty()) {
			visitedLocation = gpsUtil.getUserLocation(user.getUserId());
			user.addToVisitedLocations(visitedLocation);
		} else {
			visitedLocation = user.getLastVisitedLocation();
		}
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

	public List<NearbyAttractionDTO> getNearByAttractions(VisitedLocation visitedLocation) {
		// prepare a list to return
		List<NearbyAttractionDTO> nearbyAttractions = new ArrayList<>();
		UUID userId = visitedLocation.userId;
		Location userLocation = visitedLocation.location;

		// needs : a user's id, and a visited location
		if (userId == null || userLocation == null) {
			System.err.println("Visited location is incomplete");
			return nearbyAttractions;
		}

		// get the attractions
		List<Attraction> attractionList;
		try {
			attractionList = gpsUtil.getAttractions();
			if (attractionList == null || attractionList.isEmpty()) {
				System.err.println("No attractions found");
				return nearbyAttractions;
			}
		} catch (Exception e) {
			System.err.println("Error while getting nearby attractions : " + e.getMessage());
			return nearbyAttractions;
		}

		// find the attractions
		for (Attraction attraction : attractionList) {
			try {
				double distance = rewardsService.getDistance(attraction, userLocation);
				int rewardPoints = rewardsService.getRewardPoints(attraction, userId);

				NearbyAttractionDTO dto = new NearbyAttractionDTO(
						attraction.attractionName,
						attraction.latitude,
						attraction.longitude,
						userLocation.latitude,
						userLocation.longitude,
						distance,
						rewardPoints
				);
				nearbyAttractions.add(dto);

			} catch (Exception e) {
				System.err.println("Error retrieving attraction : " + e.getMessage());
				return nearbyAttractions;
			}
		}

		// sort by distance then return closest 5
		return nearbyAttractions.stream()
				.sorted(Comparator.comparingDouble(dto ->
						dto.attractionDistance))
				.limit(5)
				.toList();
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}