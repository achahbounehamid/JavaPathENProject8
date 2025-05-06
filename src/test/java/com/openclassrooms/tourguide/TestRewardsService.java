package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import gpsUtil.location.Attraction;


//@Disabled("À corriger après avoir terminé l’étape 3 - le test échoue actuellement")
public class TestRewardsService {

	@Test
	public void userGetRewards() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(0);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);
        // Crée un utilisateur
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		// Simule une visite à l'emplacement d'une attraction
		Attraction attraction = gpsUtil.getAttractions().get(0);
//		user.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date()));corriger
		Location location = new Location(attraction.latitude, attraction.longitude);
		VisitedLocation visitedLocation = new VisitedLocation(user.getUserId(), location, new Date());
		user.addToVisitedLocations(visitedLocation);

		// Calcul des récompenses
		tourGuideService.trackUserLocation(user);

		List<UserReward> userRewards = user.getUserRewards();
		tourGuideService.tracker.stopTracking();
		// vérifie qu'au moins une récompense a été ajoutée
		assertTrue(userRewards.size() > 1);
	}

	@Test
	public void isWithinAttractionProximity() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		Attraction attraction = gpsUtil.getAttractions().get(0);
		assertTrue(rewardsService.isWithinAttractionProximity(attraction, attraction));
	}

//	@Disabled Needs fixed - can throw ConcurrentModificationException
	@Test
	public void nearAllAttractions() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		rewardsService.setProximityBuffer(Integer.MAX_VALUE);

		InternalTestHelper.setInternalUserNumber(1);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		rewardsService.calculateRewards(tourGuideService.getAllUsers().get(0));
		List<UserReward> userRewards = tourGuideService.getUserRewards(tourGuideService.getAllUsers().get(0));
		tourGuideService.tracker.stopTracking();

		assertEquals(gpsUtil.getAttractions().size(), userRewards.size());
	}

}
