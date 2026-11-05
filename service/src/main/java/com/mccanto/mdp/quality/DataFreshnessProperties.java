package com.mccanto.mdp.quality;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mdp.data.freshness")
public class DataFreshnessProperties {

	private Duration threshold = Duration.ofMinutes(60);

	private String watermarkPath = "../pipeline/warehouse/_watermarks/shipments.json";

	public Duration getThreshold() {
		return threshold;
	}

	public void setThreshold(Duration threshold) {
		this.threshold = threshold;
	}

	public String getWatermarkPath() {
		return watermarkPath;
	}

	public void setWatermarkPath(String watermarkPath) {
		this.watermarkPath = watermarkPath;
	}

}
