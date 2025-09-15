package com.openclassrooms.tourguide.helper;
/**
 * Helper de tests pour configurer le nombre d'utilisateurs internes
 * créés par {@code TourGuideService} lors de l'initialisation.
 *
 * <p>Usage typique dans un test :</p>
 * <pre>
 *   InternalTestHelper.setInternalUserNumber(0);
 *   TourGuideService service = new TourGuideService(gpsUtil, rewardsService);
 * </pre>
 *
 * <p>Note : valeur statique → à fixer avant de construire le service.</p>
 */
public class InternalTestHelper {

	// Set this default up to 100,000 for testing
	private static int internalUserNumber = 100;
	
	public static void setInternalUserNumber(int internalUserNumber) {
		InternalTestHelper.internalUserNumber = internalUserNumber;
	}
	/** @return le nombre courant d'utilisateurs internes à créer. */
	public static int getInternalUserNumber() {
		return internalUserNumber;
	}
}
