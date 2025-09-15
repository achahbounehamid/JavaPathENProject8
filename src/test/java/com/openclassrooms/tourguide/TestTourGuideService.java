package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import tripPricer.Provider;

/**
 * Tests unitaires du service applicatif {@link TourGuideService}.
 *
 * <p><b>Objectifs pédagogiques :</b>
 * <ul>
 *   <li>Vérifier la récupération/traçage de la localisation utilisateur.</li>
 *   <li>Vérifier l’ajout et la récupération d’utilisateurs internes.</li>
 *   <li>Vérifier la sélection des 5 attractions les plus proches.</li>
 *   <li>Vérifier la récupération de 10 offres de voyage (TripPricer).</li>
 * </ul>
 */
public class TestTourGuideService {

	/**
	 * Cas nominal : {@link TourGuideService#trackUserLocation(User)} renvoie une localisation
	 * associée au bon {@code userId}.
	 *
	 * <p><b>Attendu :</b> l’ID de l’utilisateur dans la {@link VisitedLocation} correspond
	 * à l’ID du {@link User} fourni.</p>
	 */
	@Test
	public void getUserLocation() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		InternalTestHelper.setInternalUserNumber(0);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		VisitedLocation visitedLocation = tourGuideService.trackUserLocation(user);
		tourGuideService.tracker.stopTracking();
		assertTrue(visitedLocation.userId.equals(user.getUserId()));
	}

	/**
	 * Vérifie l’ajout d’utilisateurs et leur récupération par nom.
	 *
	 * <p><b>Attendu :</b> après {@code addUser}, {@code getUser} renvoie les instances exactes
	 * ajoutées pour chaque nom d’utilisateur.</p>
	 */
	@Test
	public void addUser() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		InternalTestHelper.setInternalUserNumber(0);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		User user2 = new User(UUID.randomUUID(), "jon2", "000", "jon2@tourGuide.com");

		tourGuideService.addUser(user);
		tourGuideService.addUser(user2);

		User retrivedUser = tourGuideService.getUser(user.getUserName());
		User retrivedUser2 = tourGuideService.getUser(user2.getUserName());

		tourGuideService.tracker.stopTracking();

		assertEquals(user, retrivedUser);
		assertEquals(user2, retrivedUser2);
	}

	/**
	 * Vérifie la récupération de la liste de tous les utilisateurs.
	 *
	 * <p><b>Attendu :</b> la collection retournée par {@code getAllUsers()} contient
	 * les utilisateurs précédemment ajoutés.</p>
	 */
	@Test
	public void getAllUsers() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		InternalTestHelper.setInternalUserNumber(0);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		User user2 = new User(UUID.randomUUID(), "jon2", "000", "jon2@tourGuide.com");

		tourGuideService.addUser(user);
		tourGuideService.addUser(user2);

		List<User> allUsers = tourGuideService.getAllUsers();

		tourGuideService.tracker.stopTracking();

		assertTrue(allUsers.contains(user));
		assertTrue(allUsers.contains(user2));
	}

	/**
	 * Vérifie que {@code trackUserLocation} associe bien la position au bon utilisateur.
	 *
	 * <p><b>Attendu :</b> l’ID dans la {@link VisitedLocation} est égal à l’ID de l’utilisateur passé.</p>
	 */
	@Test
	public void trackUser() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		InternalTestHelper.setInternalUserNumber(0);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		VisitedLocation visitedLocation = tourGuideService.trackUserLocation(user);

		tourGuideService.tracker.stopTracking();

		assertEquals(user.getUserId(), visitedLocation.userId);
	}

	/**
	 * Vérifie que le service retourne exactement <b>5 attractions</b> les plus proches
	 * du point visité fourni (peu importe la distance).
	 *
	 * <p><b>Attendu :</b> la liste renvoyée par {@code getNearByAttractions} contient 5 éléments.</p>
	 */
	@Test
	public void getNearbyAttractions() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		InternalTestHelper.setInternalUserNumber(0);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		VisitedLocation visitedLocation = tourGuideService.trackUserLocation(user);
		System.out.println("User location: lat = " + visitedLocation.location.latitude + ", lon = " + visitedLocation.location.longitude);

		List<Attraction> attractions = tourGuideService.getNearByAttractions(visitedLocation);

		tourGuideService.tracker.stopTracking();

		assertEquals(5, attractions.size());
	}

	/**
	 * Vérifie que {@code getTripDeals} retourne 10 offres (duplication contrôlée si besoin).
	 *
	 * <p><b>Attendu :</b> la liste des {@link Provider} contient 10 éléments.</p>
	 */
	@Test
	public void getTripDeals() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		InternalTestHelper.setInternalUserNumber(0);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");

		List<Provider> providers = tourGuideService.getTripDeals(user);

		tourGuideService.tracker.stopTracking();

		assertEquals(10, providers.size());
	}
}
