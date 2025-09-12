package com.openclassrooms.tourguide.tracker;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadFactory;   // <— si besoin
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

public class Tracker extends Thread {
	private static final Logger logger = LoggerFactory.getLogger(Tracker.class);
	private static final long trackingPollingInterval = TimeUnit.MINUTES.toSeconds(5);

	// DAEMON thread factory pour ne pas bloquer la JVM
	private static ThreadFactory daemonFactory = r -> {
		Thread t = new Thread(r, "tracker-exec");
		t.setDaemon(true);
		return t;
	};

	private final ExecutorService executorService = Executors.newSingleThreadExecutor(daemonFactory);
	private final TourGuideService tourGuideService;
	private final AtomicBoolean stop = new AtomicBoolean(false);

	public Tracker(TourGuideService tourGuideService) {
		this.tourGuideService = tourGuideService;
		executorService.submit(this); // lance run()
	}

	/** Arrêt propre */
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
		StopWatch stopWatch = new StopWatch();
		while (true) {
			if (Thread.currentThread().isInterrupted() || stop.get()) {
				logger.debug("Tracker stopping");
				break;
			}
			List<User> users = tourGuideService.getAllUsers();
			logger.debug("Begin Tracker. Tracking {} users.", users.size());
			stopWatch.start();
			users.forEach(tourGuideService::trackUserLocation);
			stopWatch.stop();
			logger.debug("Tracker Time Elapsed: {} seconds.",
					TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
			stopWatch.reset();
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
