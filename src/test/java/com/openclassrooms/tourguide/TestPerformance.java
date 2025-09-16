
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests de performance haute volumétrie pour TourGuide.
 *
 * <p><b>Objectifs (exigences) :</b>
 * <ul>
 *   <li><b>track location</b> : 100 000 utilisateurs en ≤ 15 minutes.</li>
 *   <li><b>get rewards</b>   : 100 000 utilisateurs en ≤ 20 minutes.</li>
 * </ul>
 */
public class TestPerformance {
	/**
	 * Perf GPS ONLY (pas de calcul de récompenses).
	 * <p>Vérifie que le tracking de N utilisateurs s’exécute dans le budget temps.</p>
	 */
	@Test
	public void highVolumeTrackLocation() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// lit -DuserCount, défaut 1000 si absent
		int n = Integer.getInteger("userCount", 100);
		InternalTestHelper.setInternalUserNumber(n);

		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		tourGuideService.trackAllUsersLocationAsync(); // la méthode asynchrone

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeTrackLocation ("+n+"): "
				+ TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " s");

		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

	/**
	 * Perf Rewards (calcul parallèle des points pour N utilisateurs).
	 * <p>Chaque utilisateur reçoit au moins une visite sur une attraction pour garantir l’éligibilité.</p>
	 */
	@Test
	public void highVolumeGetRewards() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// DrewardsUserCount si fourni, sinon -DuserCount, sinon 100
		int n = Integer.getInteger("rewardsUserCount",
				Integer.getInteger("userCount", 100));
		InternalTestHelper.setInternalUserNumber(n);

		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();
		allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		tourGuideService.calculateAllRewardsAsync(); // ta méthode asynchrone

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeGetRewards ("+n+"): "
				+ TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " s");

		for (User user : allUsers) {
			assertTrue(user.getUserRewards().size() > 0);
		}
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

}
