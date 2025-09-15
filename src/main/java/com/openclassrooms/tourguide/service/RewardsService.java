package com.openclassrooms.tourguide.service;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

/**
 * Service métier responsable du calcul et de l'attribution des récompenses (reward points)
 * aux utilisateurs en fonction de leurs visites et de la proximité des attractions.
 *
 * <p><b>Responsabilités :</b>
 * <ul>
 *   <li>Déterminer si un utilisateur est suffisamment proche d'une attraction pour être éligible à une récompense.</li>
 *   <li>Éviter les doublons de récompenses pour une même attraction.</li>
 *   <li>Calculer les distances (en miles) entre deux coordonnées géographiques.</li>
 * </ul>
 */
@Service
public class RewardsService {
	// Facteur de conversion des milles nautiques vers les miles statutaires.
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// Rayon de proximité par défaut (en miles) pour l'attribution d'une récompense.
	private int defaultProximityBuffer = 10;

	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	/**
	 * @param gpsUtil        fournisseur des attractions et localisations
	 * @param rewardCentral  fournisseur des points de récompense
	 */
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}
	/**
	 * Définit le rayon de proximité (en miles) utilisé pour déterminer si une visite
	 * donne droit à une récompense pour une attraction.
	 *
	 * @param proximityBuffer rayon (miles), par ex. 10
	 */
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	//Réinitialise le rayon de proximité au paramètre par défaut.
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Calcule et enregistre les récompenses manquantes pour un utilisateur.
	 *
	 * <p><b>Stratégie :</b> pour chaque localisation visitée par l'utilisateur, on itère sur
	 * toutes les attractions. Si l'utilisateur n'a pas encore de récompense pour cette attraction
	 * et que la distance est inférieure ou égale au {@code proximityBuffer}, on ajoute une
	 * {@link UserReward} avec les points retournés par {@link RewardCentral}.
	 *
	 * <p><b>Robustesse :</b> utilisation de copies défensives des listes pour éviter les
	 * {@code ConcurrentModificationException}.
	 *
	 * @param user utilisateur ciblé
	 */
	public void calculateRewards(User user){
		// Copie défensive pour éviter ConcurrentModification si une autre thread ajoute une visite
		List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = new ArrayList<>(gpsUtil.getAttractions());

		for (VisitedLocation visitedLocation : userLocations) {
			for (Attraction attraction : attractions) {
				boolean notAlreadyRewarded = user.getUserRewards().stream()
						.noneMatch(r -> r.attraction.attractionId.equals(attraction.attractionId));
				if (notAlreadyRewarded && nearAttraction(visitedLocation, attraction)) {
					user.addUserReward(new UserReward(
							visitedLocation, attraction, getRewardPoints(attraction, user)
					));
				}
			}
		}
	}

public boolean isWithinAttractionProximity(Attraction attraction, Location location){
	return getDistance(attraction, location) <= attractionProximityRange;
}
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) <=proximityBuffer;
	}

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
