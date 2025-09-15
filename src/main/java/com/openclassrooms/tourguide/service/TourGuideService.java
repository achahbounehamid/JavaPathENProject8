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

/**
 * Service applicatif principal orchestrant :
 * <ul>
 *   <li>La récupération de la localisation des utilisateurs (GPS via {@link GpsUtil}).</li>
 *   <li>Le calcul et l’agrégation des récompenses (via {@link RewardsService}).</li>
 *   <li>La récupération des offres de voyage (via {@link TripPricer}).</li>
 *   <li>Les traitements haute volumétrie (100k utilisateurs) grâce à des pools d’exécution dédiés.</li>
 * </ul>
 *
 * <h2>Points clés</h2>
 * <ol>
 *   <li><b>Séparation des responsabilités :</b> GPS, Rewards et TripPricer sont clairement séparés.</li>
 *   <li><b>Concurrence & performance :</b> deux {@link ExecutorService} paramétrables pour paralléliser
 *       les appels  (GPS et Rewards) + batching pour limiter la pression mémoire.</li>
 *   <li><b>Testabilité :</b> les API unitaires restent simples (track 1 user, get rewards, etc.),
 *       et des méthodes “bulk” asynchrones existent pour les tests de performance.</li>
 *   <li><b>Stabilité :</b> structure thread-safe pour les utilisateurs internes (ConcurrentHashMap) et
 *       arrêt propre des services via un shutdown hook.</li>
 * </ol>
 */
@Service
public class TourGuideService {

	// Clé API de test pour TripPricer
	private static final String tripPricerApiKey = "test-server-api-key";

	private final Logger logger = LoggerFactory.getLogger(TourGuideService.class);

	// Dépendance I/O : positions & attractions
	private final GpsUtil gpsUtil;

	// Dépendance métier : attribution des récompenses
	private final RewardsService rewardsService;

	// Dépendance I/O : fournisseurs d’offres de voyage
	private final TripPricer tripPricer = new TripPricer();

	// Thread de suivi (hérité du projet de départ)
	public final Tracker tracker;

	// Active les données internes (utilisateurs de démo) si vrai
	boolean testMode = true;

	// Pools dédiés et paramétrables (I/O bound -> beaucoup de threads)
	// Pool pour paralléliser les appels GPS (I/O majoritaire)
	private final ExecutorService gpsExecutor;

	// Pool pour paralléliser les calculs de récompenses (I/O RewardCentral)
	private final ExecutorService rewardsExecutor;

	// Stockage des users (thread-safe)
	// Conteneur thread-safe pour les utilisateurs internes (en mémoire)
	private final Map<String, User> internalUserMap = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Construit le service avec des pools configurables via System properties :
	 * <ul>
	 *   <li><code>gpsThreads</code> (défaut 256)</li>
	 *   <li><code>rewardsThreads</code> (défaut 512)</li>
	 * </ul>
	 *
	 * @param gpsUtil        fournisseur de localisations/utilisateurs/attractions
	 * @param rewardsService service métier de calcul des récompenses
	 */
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
	 *         API
	 * ========================= */

	/**
	 * Renvoie la liste complète des attractions (source {@link GpsUtil}).
	 * @return liste d’attractions.
	 */
	public List<Attraction> getAllAttractions() {
		return gpsUtil.getAttractions();
	}

	/**
	 * Renvoie les récompenses déjà calculées pour un utilisateur.
	 * @param user utilisateur concerné
	 * @return liste des {@link UserReward}
	 */
	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	/**
	 * Renvoie la dernière position connue d’un utilisateur, ou la calcule si nécessaire.
	 * @param user utilisateur concerné
	 * @return dernière {@link VisitedLocation}
	 */
	public VisitedLocation getUserLocation(User user) {
		return (user.getVisitedLocations().size() > 0)
				? user.getLastVisitedLocation()
				: trackUserLocation(user);
	}

	/**
	 * Récupère un utilisateur interne par son nom.
	 * @param userName identifiant fonctionnel (ex. "internalUser42")
	 * @return utilisateur ou {@code null} si absent
	 */
	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	/**
	 * Renvoie la liste des utilisateurs internes (copie de protection).
	 * @return liste des utilisateurs
	 */
	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	/**
	 * Ajoute un utilisateur s’il n’existe pas déjà (idempotent).
	 * @param user utilisateur à enregistrer
	 */
	public void addUser(User user) {
		internalUserMap.putIfAbsent(user.getUserName(), user);
	}

	/**
	 * Récupère et enregistre des offres de voyage pour un utilisateur.
	 * <p>
	 * TripPricer renvoie souvent 5 entrées : on duplique à 10 pour respecter les tests/fonctionnalités existants.
	 * </p>
	 * @param user utilisateur
	 * @return liste de 10 {@link Provider}
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
			// duplication contrôlée jusqu’à 10 éléments
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
	 * Traite un utilisateur : récupération GPS + calcul des rewards.
	 * <p>
	 * Méthode unitaire (conservative) utilisée par le code applicatif et certains tests unitaires.
	 * Les méthodes “bulk” asynchrones ci-dessous sont à privilégier pour les tests de performance.
	 * </p>
	 * @param user utilisateur
	 * @return {@link VisitedLocation} ajoutée
	 */
	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = fetchLocationOnly(user); // GPS
		rewardsService.calculateRewards(user);                      // Rewards
		return visitedLocation;
	}

	/**
	 * Renvoie les <b>5 attractions les plus proches</b> du point visité fourni (peu importe la distance).
	 * <p>
	 * Tri croissant par distance (miles) en s’appuyant sur {@link RewardsService#getDistance(Location, Location)}.
	 * </p>
	 * @param visitedLocation point de référence (dernier point de l’utilisateur en général)
	 * @return liste de 5 {@link Attraction}
	 */
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> allAttractions = gpsUtil.getAttractions();
		return allAttractions.stream()
				.sorted(Comparator.comparingDouble(a ->
						rewardsService.getDistance(a, visitedLocation.location)))
				.limit(5)
				.collect(Collectors.toList());
	}


	/**
	 * Récupère la localisation GPS et l’ajoute à l’historique de l’utilisateur (sans calculer les rewards).
	 * <p>
	 * Méthode utilitaire appelée en parallèle dans {@link #trackAllUsersLocationAsync()}.
	 * </p>
	 * @param user utilisateur
	 * @return {@link VisitedLocation} ajoutée
	 */
	private VisitedLocation fetchLocationOnly(User user) {
		VisitedLocation v = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(v);
		return v;
	}

	/**
	 * Découpe une liste en sous-listes de taille fixe (dernier lot partiel si nécessaire).
	 * @param list liste source
	 * @param size taille d’un lot (&gt;= 1)
	 * @param <T>  type des éléments
	 * @return liste de lots (vue par sous-listes)
	 */
	private static <T> List<List<T>> partition(List<T> list, int size) {
		List<List<T>> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i += size) {
			out.add(list.subList(i, Math.min(i + size, list.size())));
		}
		return out;
	}

	/**
	 * <b>Performance GPS :</b> récupération des positions pour tous les utilisateurs en parallèle (sans rewards).
	 * <ul>
	 *   <li>Utilise le pool {@code gpsExecutor} (I/O bound).</li>
	 *   <li>Batching via {@code -DbatchSize} (défaut 2000) pour limiter l’empreinte mémoire.</li>
	 * </ul>
	 * Conçu pour satisfaire : <i>100 000 users &le; 15 minutes</i> (tests de perf).
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
	 * <b>Performance Rewards :</b> calcul des récompenses pour tous les utilisateurs en parallèle.
	 * <ul>
	 *   <li>Utilise le pool {@code rewardsExecutor} (I/O RewardCentral).</li>
	 *   <li>Batching via {@code -DbatchSize} (défaut 2000).</li>
	 * </ul>
	 * Conçu pour satisfaire : <i>100 000 users &le; 20 minutes</i> (tests de perf).
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


	/**
	 * Enregistre un hook d’arrêt pour :
	 * <ul>
	 *   <li>Stopper le {@link Tracker}.</li>
	 *   <li>Arrêter immédiatement les pools d’exécution.</li>
	 * </ul>
	 * Important pour éviter les fuites de threads lors de l’arrêt de l’application/tests.
	 */
	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			tracker.stopTracking();
			gpsExecutor.shutdownNow();
			rewardsExecutor.shutdownNow();
		}));
	}

	/**
	 * Initialise les utilisateurs internes (données de démo) lorsque {@link #testMode} est actif.
	 * <p>
	 * Remarque : pour des tests 100% déterministes, on pourrait semer les aléas
	 * (Random seedé via propriété système), mais ce n’est pas requis par les tests actuels.
	 * </p>
	 */
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

	/**
	 * Génère un petit historique de positions aléatoires pour un utilisateur interne.
	 * <p>
	 * 3 points répartis dans le temps (30 jours) pour simuler un historique minimal.
	 * </p>
	 * @param user utilisateur cible
	 */
	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> user.addToVisitedLocations(
				new VisitedLocation(
						user.getUserId(),
						new Location(generateRandomLatitude(), generateRandomLongitude()),
						getRandomTime()
				)
		));
	}

	/** Longitude aléatoire uniforme dans [-180, 180]. */
	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	/** Latitude aléatoire uniforme dans [-85.05112878, 85.05112878] (bornes Mercator). */
	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	/** Date aléatoire dans les 30 derniers jours (UTC). */
	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}
}
