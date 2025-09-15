package com.openclassrooms.tourguide.user;

import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
/**
 * Représente une récompense obtenue par un utilisateur pour la visite
 * d'une attraction donnée.
 *
 * <p><b>Composition :</b>
 * <ul>
 *   <li>{@link #visitedLocation} : la localisation (visite) qui a déclenché l'éligibilité.</li>
 *   <li>{@link #attraction} : l'attraction concernée par la récompense.</li>
 *   <li>{@link #rewardPoints} : le nombre de points attribués.</li>
 */
public class UserReward {
	/**
	 * Construit une récompense complète (avec le nombre de points).
	 *
	 * @param visitedLocation la localisation de l'utilisateur au moment de la visite
	 * @param attraction      l'attraction récompensée
	 * @param rewardPoints    le nombre de points accordés
	 */

	public final VisitedLocation visitedLocation;
	public final Attraction attraction;
	private int rewardPoints;
	public UserReward(VisitedLocation visitedLocation, Attraction attraction, int rewardPoints) {
		this.visitedLocation = visitedLocation;
		this.attraction = attraction;
		this.rewardPoints = rewardPoints;
	}
	/**
	 * Construit une récompense sans préciser les points (valeur par défaut 0).
	 * <p>Les points pourront être définis plus tard via {@link #setRewardPoints(int)}.</p>
	 *
	 * @param visitedLocation la localisation de l'utilisateur au moment de la visite
	 * @param attraction      l'attraction récompensée
	 */
	public UserReward(VisitedLocation visitedLocation, Attraction attraction) {
		this.visitedLocation = visitedLocation;
		this.attraction = attraction;
	}
	/**
	 * Définit (ou met à jour) le nombre de points.
	 * @param rewardPoints points à enregistrer
	 */
	public void setRewardPoints(int rewardPoints) {
		this.rewardPoints = rewardPoints;
	}

	/**
	 * @return le nombre de points attribués pour cette récompense
	 */
	public int getRewardPoints() {
		return rewardPoints;
	}
	
}
