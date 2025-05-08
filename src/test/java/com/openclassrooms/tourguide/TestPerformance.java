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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Timeout;

//@Disabled
public class TestPerformance {
	@Test
	@Timeout(value = 10, unit = TimeUnit.MINUTES)
	public void highVolumeTrackLocation() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		int userCount = 1000;
		InternalTestHelper.setInternalUserNumber(userCount);

		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);
		List<User> allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// Pool de threads avec taille limitée
		ExecutorService executor = Executors.newFixedThreadPool(100);

		List<CompletableFuture<Void>> futures = allUsers.stream()
				.map(user -> CompletableFuture.runAsync(() -> tourGuideService.trackUserLocation(user), executor))
				.collect(Collectors.toList());

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		executor.shutdown(); // bien fermer le pool

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeTrackLocation: Time Elapsed: " +
				TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");

		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}


	@Test
	@Timeout(value = 10, unit = TimeUnit.MINUTES)
	public void highVolumeGetRewards() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		int userCount = 1000;
		InternalTestHelper.setInternalUserNumber(userCount);

		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();

		// Chaque utilisateur "visite" cette attraction
		allUsers.forEach(user ->
				user.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date()))
		);

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// Pool de threads limité
		ExecutorService executor = Executors.newFixedThreadPool(100);

		// Parallélisation du calcul des récompenses
		List<CompletableFuture<Void>> futures = allUsers.stream()
				.map(user -> CompletableFuture.runAsync(() -> rewardsService.calculateRewards(user), executor))
				.collect(Collectors.toList());

		// Attendre la fin de toutes les tâches
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		executor.shutdown();

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
