package com.openclassrooms.tourguide.domain.model;

import java.util.*;

import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import tripPricer.Provider;

public class User {
	private final UUID userId;
	private final String userName;
	private String phoneNumber;
	private String emailAddress;
	private Date latestLocationTimestamp;
	private final List<VisitedLocation> visitedLocations = Collections.synchronizedList(new ArrayList<>());
	private final List<UserReward> userRewards = Collections.synchronizedList(new ArrayList<>());
	private UserPreferences userPreferences = new UserPreferences();
	private List<Provider> tripDeals = new ArrayList<>();
	public User(UUID userId, String userName, String phoneNumber, String emailAddress) {
		this.userId = userId;
		this.userName = userName;
		this.phoneNumber = phoneNumber;
		this.emailAddress = emailAddress;
	}
	
	public UUID getUserId() {
		return userId;
	}
	
	public String getUserName() {
		return userName;
	}
	
	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}
	
	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}
	
	public String getEmailAddress() {
		return emailAddress;
	}
	
	public void setLatestLocationTimestamp(Date latestLocationTimestamp) {
		this.latestLocationTimestamp = latestLocationTimestamp;
	}
	
	public Date getLatestLocationTimestamp() {
		return latestLocationTimestamp;
	}
	
	public void addToVisitedLocations(VisitedLocation visitedLocation) {
		visitedLocations.add(visitedLocation);
	}
	
	public List<VisitedLocation> getVisitedLocations() {
		return visitedLocations;
	}
	
	public void clearVisitedLocations() {
		visitedLocations.clear();
	}
	public void addUserReward(UserReward userReward) {
		synchronized (userRewards) {
			boolean alreadyAdded = userRewards.stream()
					.noneMatch(r -> r.attraction.attractionName.equals(
							userReward.attraction.attractionName
					));
			if (alreadyAdded) {
				userRewards.add(userReward);
			}
		}
	}
	
	public List<UserReward> getUserRewards() {
		return userRewards;
	}
	
	public UserPreferences getUserPreferences() {
		return userPreferences;
	}
	
	public void setUserPreferences(UserPreferences userPreferences) {
		this.userPreferences = userPreferences;
	}

	public VisitedLocation getLastVisitedLocation() {
		return visitedLocations.get(visitedLocations.size() - 1);
	}
	
	public void setTripDeals(List<Provider> tripDeals) {
		this.tripDeals = tripDeals;
	}
	
	public List<Provider> getTripDeals() {
		return tripDeals;
	}

	public static class UserPreferences {

		private int attractionProximity = Integer.MAX_VALUE;
		private int tripDuration = 1;
		private int ticketQuantity = 1;
		private int numberOfAdults = 1;
		private int numberOfChildren = 0;

		public UserPreferences() {
		}

		public void setAttractionProximity(int attractionProximity) {
			this.attractionProximity = attractionProximity;
		}

		public int getAttractionProximity() {
			return attractionProximity;
		}

		public int getTripDuration() {
			return tripDuration;
		}

		public void setTripDuration(int tripDuration) {
			this.tripDuration = tripDuration;
		}

		public int getTicketQuantity() {
			return ticketQuantity;
		}

		public void setTicketQuantity(int ticketQuantity) {
			this.ticketQuantity = ticketQuantity;
		}

		public int getNumberOfAdults() {
			return numberOfAdults;
		}

		public void setNumberOfAdults(int numberOfAdults) {
			this.numberOfAdults = numberOfAdults;
		}

		public int getNumberOfChildren() {
			return numberOfChildren;
		}

		public void setNumberOfChildren(int numberOfChildren) {
			this.numberOfChildren = numberOfChildren;
		}

	}

	public static class UserReward {

		public final VisitedLocation visitedLocation;
		public final Attraction attraction;
		private int rewardPoints;
		public UserReward(VisitedLocation visitedLocation, Attraction attraction, int rewardPoints) {
			this.visitedLocation = visitedLocation;
			this.attraction = attraction;
			this.rewardPoints = rewardPoints;
		}

		public UserReward(VisitedLocation visitedLocation, Attraction attraction) {
			this.visitedLocation = visitedLocation;
			this.attraction = attraction;
		}

		public void setRewardPoints(int rewardPoints) {
			this.rewardPoints = rewardPoints;
		}

		public int getRewardPoints() {
			return rewardPoints;
		}

	}
}
