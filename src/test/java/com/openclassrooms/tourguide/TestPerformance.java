
package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import org.junit.jupiter.api.Timeout;
public class TestPerformance {

	private ExecutorService getThreadPool(int userCount) {
		if (userCount <= 100) return Executors.newFixedThreadPool(10);
		if (userCount <= 1000) return Executors.newFixedThreadPool(100);
		if (userCount <= 10000) return Executors.newFixedThreadPool(300);


		return Executors.newFixedThreadPool(2000);
	}
	@Timeout(value = 20, unit = TimeUnit.MINUTES)
	@Test
	public void highVolumeTrackLocation() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		int userCount = Integer.parseInt(System.getProperty("userCount", "100000"));
		InternalTestHelper.setInternalUserNumber(userCount);

		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);
		List<User> allUsers = tourGuideService.getAllUsers();

		ExecutorService executor = getThreadPool(userCount);
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		List<CompletableFuture<Void>> futures = allUsers.stream()
				.map(user -> CompletableFuture.runAsync(() -> tourGuideService.trackUserLocation(user), executor))
				.collect(Collectors.toList());

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		executor.shutdown();

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeTrackLocation (" + userCount + " users): " +
				TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");

		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}
}
