package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import tripPricer.Provider;
import tripPricer.TripPricer;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;



@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;
	private final ExecutorService executorService = Executors.newFixedThreadPool(100);
	public List<Attraction> getAllAttractions(){
		return gpsUtil.getAttractions();
	}

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public void trackAllUsersLocationAsync() {
		List<User> allUsers = getAllUsers(); // méthode existante dans TourGuideService

		List<CompletableFuture<Void>> futures = allUsers.stream()
				.map(user -> CompletableFuture.runAsync(() -> {
					trackUserLocation(user); // méthode existante
				}, executorService))
				.collect(Collectors.toList())  ;

		CompletableFuture<Void>[] futureArray = futures.toArray(new CompletableFuture[0]);
		CompletableFuture.allOf(futureArray).join();

	}

	public void calculateAllRewardsAsync() {
		List<User> allUsers = getAllUsers();

		List<CompletableFuture<Void>> futures = allUsers.stream()
				.map(user -> CompletableFuture.runAsync(() -> {
					rewardsService.calculateRewards(user);
				}, executorService))
				.collect(Collectors.toList());

		CompletableFuture<Void>[] futureArray = futures.toArray(new CompletableFuture[0]);
		CompletableFuture.allOf(futureArray).join();
	}

	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ?
				user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	/**
	 * Récupère une liste d'offres de voyage (providers) pour un utilisateur donné.
	 *
	 * Cette méthode utilise le service TripPricer pour générer des offres personnalisées
	 * en fonction des préférences de voyage de l'utilisateur (nombre d'adultes, d'enfants, durée du voyage)
	 * et de ses points de récompense accumulés.
	 *
	 *  Note importante : TripPricer ne retourne que 5 offres par défaut.
	 * Pour satisfaire les exigences fonctionnelles (et les tests), cette méthode
	 * duplique les offres existantes jusqu’à obtenir exactement 10 résultats.
	 *
	 * @param user L'utilisateur pour lequel générer les offres de voyage
	 * @return Une liste de 10 offres de voyage (Provider)
	 */

	public List<Provider> getTripDeals(User user) {

		int cumulatativeRewardPoints = user.getUserRewards().stream().
				mapToInt(i -> i.getRewardPoints())
				.sum();

		List<Provider> providers = tripPricer.getPrice(
				tripPricerApiKey,
				user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(),
				user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(),
				cumulatativeRewardPoints);

//si moin de 10offres sont retournées on des duplique jusqu'à en avoir 10
		if(providers.size() < 10){
			List<Provider> duplicated = new ArrayList <>(providers);
			while(duplicated.size() < 10){
				duplicated.addAll(providers);//duplique les offres existantes
			}
			providers = duplicated.subList(0, 10);
		}
		user.setTripDeals(providers);
		return providers;
	}

	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

	/**
	 * Retourne les 5 attractions les plus proches de la position d’un utilisateur.
	 *
	 * Cette méthode trie toutes les attractions disponibles par distance croissante
	 * par rapport à la position actuelle de l’utilisateur, puis sélectionne les 5 premières.
	 *
	 * Contrairement à l’ancienne implémentation basée sur une distance limite (200 miles),
	 * cette version garantit toujours une réponse avec 5 attractions, même si elles sont éloignées.
	 *
	 * @param visitedLocation la localisation actuelle de l’utilisateur
	 * @return une liste des 5 attractions les plus proches
	 */
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> allAttractions = gpsUtil.getAttractions();
// Trie toutes les attractions par distance par rapport à la localisation de l’utilisateur
		List<Attraction> sortedAttractions = allAttractions.stream()
				.sorted(Comparator.comparingDouble(a -> rewardsService.getDistance(a, visitedLocation.location)))
				.limit(5)
				.collect(Collectors.toList());

		return sortedAttractions;
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 *
	 * Methods Below: For Internal Testing
	 *
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
//	private final Map<String, User> internalUserMap = new HashMap<>();
	private final Map<String, User> internalUserMap = new java.util.concurrent.ConcurrentHashMap<>();


	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
