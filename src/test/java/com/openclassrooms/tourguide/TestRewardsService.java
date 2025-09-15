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

/**
 * Tests unitaires du service de récompenses (RewardsService).
		*
		* <p><b>Objectifs pédagogiques :</b>
		* <ul>
 *   <li>Valider l’attribution d’une récompense lorsqu’un utilisateur visite une attraction.</li>
		*   <li>Vérifier la logique de proximité “large” d’une attraction (distance ≤ 200 miles).</li>
		*   <li>Vérifier qu’avec un buffer maximal, l’utilisateur est récompensé pour <i>toutes</i> les attractions.</li>
		* </ul>
		*
		* <p><b>Contexte technique :</b>
		* <ul>
 *   <li>Les tests utilisent des utilisateurs internes (via {@code InternalTestHelper}).</li>
		*   <li>Le {@code Tracker} lance par {@code TourGuideService} est arrêté en fin de test
 *       pour éviter de garder un thread vivant en arrière-plan.</li>
		* </ul>
		*
		* <p><b>Bonnes pratiques :</b> si un test échoue, penser à arrêter le tracker dans un bloc {@code finally}
 * pour garantir un arrêt propre des threads.</p>
		*/
public class TestRewardsService {

	/**
	 * Cas nominal : lorsqu'une visite est enregistrée <b>exactement</b> sur l'une des attractions,
	 * une et une seule récompense doit être attribuée à l’utilisateur.
	 *
	 * <p><b>Étapes :</b>
	 * <ol>
	 *   <li>Créer un utilisateur sans historiques internes.</li>
	 *   <li>Ajouter une visite positionnée sur la première attraction retournée par {@code GpsUtil}.</li>
	 *   <li>Appeler {@code trackUserLocation(user)} qui déclenche le calcul des récompenses.</li>
	 * </ol>
	 *
	 * <p><b>Attendu :</b> la liste des récompenses de l’utilisateur contient exactement 1 élément.</p>
	 */
	@Test
	public void userGetRewards() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(0);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		Attraction attraction = gpsUtil.getAttractions().get(0);
		user.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date()));
		tourGuideService.trackUserLocation(user);
		List<UserReward> userRewards = user.getUserRewards();
		tourGuideService.tracker.stopTracking();
		assertTrue(userRewards.size() == 1);
	}

	/**
	 * Vérifie que la méthode {@code isWithinAttractionProximity} considère une attraction
	 * comme “proche” d’elle-même (distance nulle).
	 *
	 * <p><b>Attendu :</b> distance = 0 mile ≤ 200 miles ⇒ renvoie {@code true}.</p>
	 */
	@Test
	public void isWithinAttractionProximity() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		Attraction attraction = gpsUtil.getAttractions().get(0);
		assertTrue(rewardsService.isWithinAttractionProximity(attraction, attraction));
	}

	/**
	 * Vérifie que l’utilisateur reçoit une récompense pour <b>chaque attraction</b>
	 * lorsque la zone de proximité (proximity buffer) est étendue à son maximum.
	 *
	 * <p><b>Contexte :</b>
	 * <ul>
	 *   <li>{@code setProximityBuffer(Integer.MAX_VALUE)} rend toutes les attractions “proches”.</li>
	 *   <li>Un utilisateur interne est généré ; on calcule ses récompenses.</li>
	 * </ul>
	 *
	 * <p><b>Attendu :</b> le nombre de récompenses de l’utilisateur est égal au nombre total d’attractions.</p>
	 *
	 * <p><b>Robustesse :</b> le calcul dans {@code RewardsService} utilise des copies défensives des listes pour
	 * éviter toute {@code ConcurrentModificationException} si le tracker modifie les visites en parallèle.</p>
	 */
	//@Disabled
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

