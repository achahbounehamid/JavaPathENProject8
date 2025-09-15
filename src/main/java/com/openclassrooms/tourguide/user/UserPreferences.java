package com.openclassrooms.tourguide.user;

/**
 * Préférences de voyage d'un utilisateur.
 *
 * <p><b>Rôle :</b> regrouper les paramètres utilisés par les services de tarification
 * et de recommandation (durée du voyage, quantité de tickets, composition du groupe, etc.).</p>
 *
 * <p><b>Valeurs par défaut :</b>
 * <ul>
 *   <li>{@code attractionProximity} = {@link Integer#MAX_VALUE} (proximité maximale : désactive le filtrage strict)</li>
 *   <li>{@code tripDuration} = 1 (jour)</li>
 *   <li>{@code ticketQuantity} = 1</li>
 *   <li>{@code numberOfAdults} = 1</li>
 *   <li>{@code numberOfChildren} = 0</li>
 */
public class UserPreferences {
	
	private int attractionProximity = Integer.MAX_VALUE;
	private int tripDuration = 1;
	private int ticketQuantity = 1;
	private int numberOfAdults = 1;
	private int numberOfChildren = 0;
	
	public UserPreferences() {
	}
	
	public void setAttractionProximity(int attractionProximity) {
		this.attractionProximity = attractionProximity;
	}
	
	public int getAttractionProximity() {
		return attractionProximity;
	}
	
	public int getTripDuration() {
		return tripDuration;
	}

	public void setTripDuration(int tripDuration) {
		this.tripDuration = tripDuration;
	}

	public int getTicketQuantity() {
		return ticketQuantity;
	}

	public void setTicketQuantity(int ticketQuantity) {
		this.ticketQuantity = ticketQuantity;
	}
	
	public int getNumberOfAdults() {
		return numberOfAdults;
	}

	public void setNumberOfAdults(int numberOfAdults) {
		this.numberOfAdults = numberOfAdults;
	}

	public int getNumberOfChildren() {
		return numberOfChildren;
	}

	public void setNumberOfChildren(int numberOfChildren) {
		this.numberOfChildren = numberOfChildren;
	}

}
