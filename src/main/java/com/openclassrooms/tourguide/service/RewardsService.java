package com.openclassrooms.tourguide.service;

import java.util.List;

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

	public void calculateRewards(User user) {
		this.setProximityBuffer(Integer.MAX_VALUE);
		List<VisitedLocation> userLocations = user.getVisitedLocations();
		List<Attraction> attractions = gpsUtil.getAttractions();

		for (VisitedLocation visitedLocation : userLocations) {
			Location location = visitedLocation.location;

			if (location instanceof Attraction a) {
				location = new Location(a.latitude, a.longitude);
			}

			if (location == null) continue;

			for (Attraction attraction : attractions) {
				boolean alreadyRewarded = user.getUserRewards().stream()
						.anyMatch(r -> r.attraction.attractionName.equals(attraction.attractionName));

				if (!alreadyRewarded && getDistance(attraction, location) <= proximityBuffer) {

					user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
				}
			}
		}
	}

public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
	// Si proximityBuffer est très grand, on considère que toutes les attractions sont proches
	if (proximityBuffer == Integer.MAX_VALUE) {
		return true;
	}
	return getDistance(attraction, location) <= proximityBuffer;
}

private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
	return getDistance(attraction, visitedLocation.location) <= proximityBuffer;
}

//modification sur la méthode getRewardPoints avant privite
	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
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
