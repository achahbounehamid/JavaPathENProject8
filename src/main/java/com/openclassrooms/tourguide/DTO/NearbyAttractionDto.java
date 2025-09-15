package com.openclassrooms.tourguide.DTO;
/**
 * DTO renvoyé par l'endpoint /getNearbyAttractions.
 *
 * <p>Contient les informations nécessaires pour présenter les 5 attractions
 * les plus proches de l'utilisateur :</p>
 * <ul>
 *   <li>Nom et coordonnées de l'attraction</li>
 *   <li>Coordonnées de l'utilisateur (point de référence)</li>
 *   <li>Distance entre l'utilisateur et l'attraction (en <b>miles</b>)</li>
 *   <li>Points de récompense potentiels pour l'attraction</li>
 * </ul>
 *
 * <p><b>Remarques :</b>
 * <ul>
 *   <li>Champs publics pour une sérialisation JSON simple via Jackson.</li>
 *   <li>Si tu veux une version immuable (conseillée en prod), vois l'exemple ci-dessous.</li>
 * </ul>
 */
public class NearbyAttractionDto {
    public String attractionName;
    public double attractionLatitude;
    public double attractionLongitude;
    public double userLatitude;
    public double userLongitude;
    public double distance;
    public int rewardPoints;
    /**
     * Construit un DTO d'attraction proche.
     *
     * @param attractionName nom de l'attraction
     * @param attractionLat  latitude de l'attraction
     * @param attractionLong longitude de l'attraction
     * @param userLat        latitude de l'utilisateur
     * @param userLong       longitude de l'utilisateur
     * @param distance       distance en miles
     * @param rewardPoints   points de récompense potentiels
     */
    public NearbyAttractionDto(String attractionName, double attractionLat, double attractionLong,
                               double userLat, double userLong, double distance, int rewardPoints) {
        this.attractionName = attractionName;
        this.attractionLatitude = attractionLat;
        this.attractionLongitude = attractionLong;
        this.userLatitude = userLat;
        this.userLongitude = userLong;
        this.distance = distance;
        this.rewardPoints = rewardPoints;
    }
}
