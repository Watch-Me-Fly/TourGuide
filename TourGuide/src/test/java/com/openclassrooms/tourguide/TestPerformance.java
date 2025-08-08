package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.*;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

public class TestPerformance {

	private static Logger logger = LoggerFactory.getLogger(TestPerformance.class);
	private static GpsUtil gpsUtil;
	private static RewardsService rewardsService;
	private static TourGuideService tourGuideService;
	private StopWatch stopWatch;

	@BeforeAll
	public static void init() {
		logger.debug("init beforeAll");
		gpsUtil = new GpsUtil();
		rewardsService = new RewardsService(gpsUtil, new RewardCentral());
	}

	@BeforeEach
	public void setup() {
		logger.debug("setup beforeEach");

		InternalTestHelper.setInternalUserNumber(100000);

		stopWatch = new StopWatch();
		tourGuideService = new TourGuideService(gpsUtil, rewardsService);
	}

	@AfterEach
	void tearDown() {
		logger.debug("tearDown afterEach");

		tourGuideService.tracker.stopTracking();
		stopWatch.stop();
	}

	/*
	 * A note on performance improvements:
	 * 
	 * The number of users generated for the high volume tests can be easily
	 * adjusted via this method:
	 * 
	 * InternalTestHelper.setInternalUserNumber(100000);
	 * 
	 * 
	 * These tests can be modified to suit new solutions, just as long as the
	 * performance metrics at the end of the tests remains consistent.
	 * 
	 * These are performance metrics that we are trying to hit:
	 * 
	 * highVolumeTrackLocation: 100,000 users within 15 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(15) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 *
	 * highVolumeGetRewards: 100,000 users within 20 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */

	@DisplayName("Measure how long it takes to track location for a large number of users")
	@Test
	public void highVolumeTrackLocation() {
		logger.info("Starting highVolumeTrackLocation");

		// user's list
		List<User> allUsers = tourGuideService.getAllUsers();
		stopWatch.start();

		// track locations
		tourGuideService.trackAllUsersLocations(allUsers);

		long limitTo15minutes = TimeUnit.MINUTES.toSeconds(15);
		long totalTime = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());

		System.out.println("highVolumeTrackLocation: Time Elapsed: " + totalTime + " seconds.");
		// verify the total time is less than or equal to 15 minutes
		assertTrue(limitTo15minutes >= totalTime);
	}

	@DisplayName("Measure how long it takes to calculate rewards for a large number of users")
	@Test
	public void highVolumeGetRewards() {
		logger.info("Starting highVolumeGetRewards");

		stopWatch.start();
		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();

		allUsers.forEach(u ->
				u.addToVisitedLocations(
						new VisitedLocation(u.getUserId(), attraction,
						new Date())));

		// act
		rewardsService.calculateRewardsForMultipleUsers(allUsers);

		// assert all users were rewarded
		allUsers.forEach(user -> {
			assertFalse(user.getUserRewards().isEmpty());
		});

		long limitTo20minutes = TimeUnit.MINUTES.toSeconds(20);
		long totalTime = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());

		// verify the total time is less than or equal to 20 minutes
		System.out.println("highVolumeGetRewards: Time Elapsed: " + totalTime + " seconds.");
		assertTrue(limitTo20minutes >= totalTime);
	}

}
