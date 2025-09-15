package com.openclassrooms.tourguide;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.openclassrooms.tourguide.DTO.NearbyAttractionDto;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import com.openclassrooms.tourguide.DTO.NearbyAttractionDto;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import com.openclassrooms.tourguide.service.RewardsService;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tripPricer.Provider;

/**
 * Contrôleur REST principal de l'application TourGuide.
 *
 * <p><b>Rôles :</b>
 * <ul>
 *   <li>Exposer les endpoints pour récupérer la position d’un utilisateur, ses récompenses, ses offres de voyage.</li>
 *   <li>Retourner les <b>5 attractions les plus proches</b> du dernier point de l’utilisateur, avec un payload enrichi
 *       (noms, lat/long, distance en miles, points de récompense).</li>
 */
@RestController
public class TourGuideController {

    @Autowired
    TourGuideService tourGuideService;

    @Autowired
    private RewardsService rewardsService;

    /**
     * Endpoint de santé / accueil.
     * @return message simple
     */
    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }

    /**
     * Renvoie la dernière position connue de l’utilisateur (ou la calcule si nécessaire).
     *
     * @param userName nom d’utilisateur (ex. "internalUser0")
     * @return une {@link VisitedLocation}
     * @throws ResponseStatusException 404 si l’utilisateur est introuvable
     */
    @RequestMapping("/getLocation")
    public VisitedLocation getLocation(@RequestParam String userName) {
        return tourGuideService.getUserLocation(getUser(userName));
    }

    /**
     * Renvoie les <b>5 attractions les plus proches</b> du dernier point de l’utilisateur, peu importe la distance,
     * sous la forme d’un objet JSON enrichi contenant :
     * <ul>
     *   <li>Nom de l’attraction</li>
     *   <li>Lat/Long de l’attraction</li>
     *   <li>Lat/Long de l’utilisateur</li>
     *   <li>Distance (miles)</li>
     *   <li>Points de récompense (via RewardCentral)</li>
     * </ul>
     *
     * @param userName nom d’utilisateur
     * @return liste de 5 {@link NearbyAttractionDto}
     * @throws ResponseStatusException 404 si l’utilisateur est introuvable
     */
    @RequestMapping("/getNearbyAttractions")
    public List<NearbyAttractionDto> getNearbyAttractions(@RequestParam String userName) {
        // 1) Récupérer l’utilisateur et sa dernière position
        User user = tourGuideService.getUser(userName);
        VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);
        Location userLocation = visitedLocation.location;

        // 2) Trier toutes les attractions par distance croissante au point utilisateur, en garder 5
        return tourGuideService.getAllAttractions().stream()
                .sorted(Comparator.comparingDouble(attraction ->
                        rewardsService.getDistance(userLocation, new Location(attraction.latitude, attraction.longitude))
                ))
                .limit(5)
                // 3) Mapper vers un DTO enrichi (distance + points)
                .map(attraction -> {
                    double distance = rewardsService.getDistance(
                            userLocation, new Location(attraction.latitude, attraction.longitude));
                    int rewardPoints = rewardsService.getRewardPoints(attraction, user);
                    return new NearbyAttractionDto(
                            attraction.attractionName,
                            attraction.latitude,
                            attraction.longitude,
                            userLocation.latitude,
                            userLocation.longitude,
                            distance,
                            rewardPoints
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Renvoie les récompenses déjà calculées pour l’utilisateur.
     * @param userName nom d’utilisateur
     * @return liste des {@link UserReward}
     * @throws ResponseStatusException 404 si l’utilisateur est introuvable
     */
    @RequestMapping("/getRewards")
    public List<UserReward> getRewards(@RequestParam String userName) {
        return tourGuideService.getUserRewards(getUser(userName));
    }

    /**
     * Renvoie les offres de voyage (10 éléments) proposées à l’utilisateur.
     * @param userName nom d’utilisateur
     * @return liste de {@link Provider}
     * @throws ResponseStatusException 404 si l’utilisateur est introuvable
     */
    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
        return tourGuideService.getTripDeals(getUser(userName));
    }

    /**
     * Récupère l’utilisateur ou lève une 404 si introuvable.
     * @param userName nom d’utilisateur
     * @return {@link User}
     * @throws ResponseStatusException 404 si l’utilisateur n’existe pas
     */
    private User getUser(String userName) {
        User user = tourGuideService.getUser(userName);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable : " + userName);
        }
        return user;
    }
}