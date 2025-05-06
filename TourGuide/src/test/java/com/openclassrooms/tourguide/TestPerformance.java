package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.time.StopWatch;
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
import java.util.concurrent.CompletableFuture;

public class TestPerformance {


	@Test
	public void highVolumeTrackLocation() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(1000); // augmenter  à 1000, 10_000...

		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		//  Utilisation de CompletableFuture pour paralléliser le traitement
		List<CompletableFuture<Void>> futures = allUsers.stream()
				.map(user -> CompletableFuture.runAsync(() -> tourGuideService.trackUserLocation(user)))
				.collect(Collectors.toList());

		//  Attendre que tous les traitements soient terminés
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeTrackLocation: Time Elapsed: " +
				TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");

		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}
	@Test
	public void highVolumeGetRewards() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(1000);
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();

		// Chaque utilisateur "visite" cette attraction
		allUsers.forEach(u ->
				u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date()))
		);

		// Parallélisation du calcul des récompenses
		List<CompletableFuture<Void>> futures = allUsers.stream()
				.map(user -> CompletableFuture.runAsync(() ->
						rewardsService.calculateRewards(user)))
				.collect(Collectors.toList());

		// Attendre la fin de toutes les tâches
		futures.forEach(CompletableFuture::join);

		// Vérifier les résultats
		for (User user : allUsers) {
			assertTrue(user.getUserRewards().size() > 0);
		}

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeGetRewards: Time Elapsed: " +
				TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");

		assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
				TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}
}
