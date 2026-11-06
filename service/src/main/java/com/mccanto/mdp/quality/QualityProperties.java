package com.mccanto.mdp.quality;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mdp.data.quality")
public class QualityProperties {

	private String reportPath = "../pipeline/warehouse/_quality/latest.json";

	public String getReportPath() {
		return reportPath;
	}

	public void setReportPath(String reportPath) {
		this.reportPath = reportPath;
	}

}
