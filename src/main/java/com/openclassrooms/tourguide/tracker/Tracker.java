package com.openclassrooms.tourguide.tracker;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread de suivi périodique des utilisateurs.
 *
 * <p>Rôle :
 * <ul>
 *   <li>À intervalle fixe, itère sur tous les utilisateurs et appelle
 *       {@link TourGuideService#trackUserLocation(User)}.</li>
 *   <li>Fonctionne en arrière-plan sur un thread <i>daemon</i> (ne bloque pas l’arrêt de la JVM).</li>
 *   <li>Arrêt propre via {@link #stopTracking()} (utilisé par les tests).</li>
 * </ul>
 *
 * <p>Concurrence & robustesse :
 * <ul>
 *   <li>Exécuté dans un {@link ExecutorService} mono-thread dédié (daemon) pour éviter de bloquer la JVM.</li>
 *   <li>Une erreur sur un utilisateur n’arrête pas la boucle complète (try/catch par utilisateur).</li>
 *   <li>Intervalle de polling : 5 minutes (en secondes), ajustable en recompilant si besoin.</li>
 * </ul>
 */
public class Tracker extends Thread {
	private static final Logger logger = LoggerFactory.getLogger(Tracker.class);

	// Intervalle entre deux passes de tracking (en secondes). Ici: 5 minutes
	private static final long trackingPollingInterval = TimeUnit.MINUTES.toSeconds(5);

	// Factory de threads DAEMON pour ne pas empêcher l'arrêt de la JVM
	private static final ThreadFactory daemonFactory = r -> {
		Thread t = new Thread(r, "tracker-exec");
		t.setDaemon(true);
		return t;
	};

	// Exécuteur mono-thread daemon qui pilote l'exécution de run()
	private final ExecutorService executorService = Executors.newSingleThreadExecutor(daemonFactory);

	private final TourGuideService tourGuideService;
	private final AtomicBoolean stop = new AtomicBoolean(false);

	/**
	 * Construit le Tracker et lance immédiatement son exécution en arrière-plan.
	 * @param tourGuideService service applicatif à appeler à intervalle régulier
	 */
	public Tracker(TourGuideService tourGuideService) {
		this.tourGuideService = tourGuideService;
		// Lance run() sur le thread daemon de l'exécuteur (pas de .start() nécessaire)
		executorService.submit(this);
	}

	/**
	 * Demande un arrêt propre : positionne le flag, interrompt le thread d'exécution
	 * et attend brièvement la terminaison.
	 */
	public void stopTracking() {
		stop.set(true);
		executorService.shutdownNow();
		try {
			executorService.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override public void run() {
		// Boucle principale : stop/interrupt -> sortie
		while (true) {
			if (Thread.currentThread().isInterrupted() || stop.get()) {
				logger.debug("Tracker stopping");
				break;
			}

			List<User> users = tourGuideService.getAllUsers();
			logger.debug("Begin Tracker. Tracking {} users.", users.size());

			StopWatch stopWatch = new StopWatch();
			stopWatch.start();
			// Une erreur sur un user n'interrompt pas la passe
			for (User u : users) {
				try {
					tourGuideService.trackUserLocation(u);
				} catch (Exception ex) {
					logger.warn("Tracking failed for user {}: {}", u.getUserName(), ex.toString());
				}
			}
			stopWatch.stop();

			logger.debug("Tracker Time Elapsed: {} seconds.",
					TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));

			// Attente avant la prochaine passe
			try {
				logger.debug("Tracker sleeping");
				TimeUnit.SECONDS.sleep(trackingPollingInterval);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}
}
