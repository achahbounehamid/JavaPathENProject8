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


@RestController
public class TourGuideController {

	@Autowired
	TourGuideService tourGuideService;

    @Autowired
    private RewardsService rewardsService;

    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }
    
    @RequestMapping("/getLocation") 
    public VisitedLocation getLocation(@RequestParam String userName) {
    	return tourGuideService.getUserLocation(getUser(userName));
    }


    //  TODO: Change this method to no longer return a List of Attractions.
    //  Instead: Get the closest five tourist attractions to the user - no matter how far away they are.
    //  Return a new JSON object that contains:
    // Name of Tourist attraction,
    // Tourist attractions lat/long,
    // The user's location lat/long,
    // The distance in miles between the user's location and each of the attractions.
    // The reward points for visiting each Attraction.
    //    Note: Attraction reward points can be gathered from RewardsCentral

@RequestMapping("/getNearbyAttractions")
public List<NearbyAttractionDto> getNearbyAttractions(@RequestParam String userName) {
    User user = tourGuideService.getUser(userName);
    VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);
    Location userLocation = visitedLocation.location;

    return tourGuideService.getAllAttractions().stream()
            .sorted(Comparator.comparingDouble(attraction ->
                    rewardsService.getDistance(userLocation, new Location(attraction.latitude, attraction.longitude))
            ))
            .limit(5)
            .map(attraction -> {
                double distance = rewardsService.getDistance(userLocation, new Location(attraction.latitude, attraction.longitude));
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


    @RequestMapping("/getRewards") 
    public List<UserReward> getRewards(@RequestParam String userName) {
    	return tourGuideService.getUserRewards(getUser(userName));
    }
       
    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
    	return tourGuideService.getTripDeals(getUser(userName));
    }

private User getUser(String userName) {
    User user = tourGuideService.getUser(userName);
    if (user == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable : " + userName);
    }
    return user;
}

//    private User getUser(String userName) {
//        return tourGuideService.getUser(userName);
//    }

}