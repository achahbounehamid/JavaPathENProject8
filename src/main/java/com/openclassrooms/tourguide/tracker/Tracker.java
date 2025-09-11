package com.openclassrooms.tourguide.tracker;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import java.util.concurrent.atomic.AtomicBoolean;

public class Tracker extends Thread {
	private Logger logger = LoggerFactory.getLogger(Tracker.class);
	private static final long trackingPollingInterval = TimeUnit.MINUTES.toSeconds(5);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final TourGuideService tourGuideService;
//	private boolean stop = false;
private final AtomicBoolean stop = new AtomicBoolean(false);

	public Tracker(TourGuideService tourGuideService) {
		this.tourGuideService = tourGuideService;

		executorService.submit(this);
	}

	/**
	 * Assures to shut down the Tracker thread
	 */
//	------public void stopTracking() {
//		stop = true;
//		executorService.shutdownNow();
//	------}

	public void stopTracking() {
		stop.set(true);                // 1) signale l'arrêt
		executorService.shutdownNow(); // 2) interrompt le thread (réveille le sleep)
		try {
			if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
				executorService.shutdownNow(); // 3) dernier recours
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void run() {
		StopWatch stopWatch = new StopWatch();
		while (true) {
			if (Thread.currentThread().isInterrupted() || stop.get()) {

				logger.debug("Tracker stopping");
				break;
			}

			List<User> users = tourGuideService.getAllUsers();
			logger.debug("Begin Tracker. Tracking " + users.size() + " users.");
			stopWatch.start();
			users.forEach(tourGuideService::trackUserLocation);
			stopWatch.stop();
			logger.debug("Tracker Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
			stopWatch.reset();
			try {
				logger.debug("Tracker sleeping");
				TimeUnit.SECONDS.sleep(trackingPollingInterval);
			} catch (InterruptedException e) {
				// interruption reçue (via shutdownNow) → on sort proprement
				Thread.currentThread().interrupt();
				break;
			}
		}

	}
}
