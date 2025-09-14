package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tripPricer.Provider;
import tripPricer.TripPricer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class TourGuideService {

	private static final String tripPricerApiKey = "test-server-api-key";

	private final Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	// Pools dédiés et paramétrables (I/O bound -> beaucoup de threads)
	private final ExecutorService gpsExecutor;
	private final ExecutorService rewardsExecutor;

	// Stockage des users (thread-safe)
	private final Map<String, User> internalUserMap = new java.util.concurrent.ConcurrentHashMap<>();

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

		// Paramétrage via -DgpsThreads, -DrewardsThreads (valeurs par défaut adaptées I/O)
		int gpsThreads = Integer.getInteger("gpsThreads", 256);
		int rewardsThreads = Integer.getInteger("rewardsThreads", 512);
		this.gpsExecutor = Executors.newFixedThreadPool(gpsThreads);
		this.rewardsExecutor = Executors.newFixedThreadPool(rewardsThreads);

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

	/* =========================
	 *         API utils
	 * ========================= */
	public List<Attraction> getAllAttractions() {
		return gpsUtil.getAttractions();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		return (user.getVisitedLocations().size() > 0)
				? user.getLastVisitedLocation()
				: trackUserLocation(user);
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		internalUserMap.putIfAbsent(user.getUserName(), user);
	}

	/**
	 * Retourne 10 providers (TripPricer en renvoie souvent 5 → duplication contrôlée)
	 */
	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards()
				.stream()
				.mapToInt(UserReward::getRewardPoints)
				.sum();

		List<Provider> providers = tripPricer.getPrice(
				tripPricerApiKey,
				user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(),
				user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(),
				cumulatativeRewardPoints
		);

		if (providers.size() < 10) {
			List<Provider> duplicated = new ArrayList<>(providers);
			while (duplicated.size() < 10) {
				duplicated.addAll(providers);
			}
			providers = duplicated.subList(0, 10);
		}
		user.setTripDeals(providers);
		return providers;
	}

	/**
	 * API normale : GPS + Rewards pour 1 utilisateur
	 */
	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = fetchLocationOnly(user); // GPS
		rewardsService.calculateRewards(user);                      // Rewards
		return visitedLocation;
	}

	/**
	 * Retourne les 5 attractions les plus proches (peu importe la distance)
	 */
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> allAttractions = gpsUtil.getAttractions();
		return allAttractions.stream()
				.sorted(Comparator.comparingDouble(a ->
						rewardsService.getDistance(a, visitedLocation.location)))
				.limit(5)
				.collect(Collectors.toList());
	}

	/* =========================
	 *     Perf haute volumétrie
	 * ========================= */

	// GPS ONLY (utilisé par les tests de perf GPS)
	private VisitedLocation fetchLocationOnly(User user) {
		VisitedLocation v = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(v);
		return v;
	}

	// Partition utilitaire (batching)
	private static <T> List<List<T>> partition(List<T> list, int size) {
		List<List<T>> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i += size) {
			out.add(list.subList(i, Math.min(i + size, list.size())));
		}
		return out;
	}

	/**
	 * Perf GPS: parallélisé + batching, GPS only (pas de rewards)
	 */
	public void trackAllUsersLocationAsync() {
		List<User> users = getAllUsers();
		int batch = Integer.getInteger("batchSize", 2000);

		for (List<User> chunk : partition(users, batch)) {
			List<CompletableFuture<Void>> futures = chunk.stream()
					.map(u -> CompletableFuture.runAsync(() -> fetchLocationOnly(u), gpsExecutor))
					.collect(Collectors.toList());
			futures.forEach(CompletableFuture::join);
		}
	}

	/**
	 * Perf Rewards: parallélisé + batching
	 */
	public void calculateAllRewardsAsync() {
		List<User> users = getAllUsers();
		int batch = Integer.getInteger("batchSize", 2000);

		for (List<User> chunk : partition(users, batch)) {
			List<CompletableFuture<Void>> futures = chunk.stream()
					.map(u -> CompletableFuture.runAsync(() -> rewardsService.calculateRewards(u), rewardsExecutor))
					.collect(Collectors.toList());
			futures.forEach(CompletableFuture::join);
		}
	}

	/* =========================
	 *      Lifecycle / Init
	 * ========================= */

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			tracker.stopTracking();
			gpsExecutor.shutdownNow();
			rewardsExecutor.shutdownNow();
		}));
	}

	// Users internes pour les tests
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
		IntStream.range(0, 3).forEach(i -> user.addToVisitedLocations(
				new VisitedLocation(
						user.getUserId(),
						new Location(generateRandomLatitude(), generateRandomLongitude()),
						getRandomTime()
				)
		));
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
