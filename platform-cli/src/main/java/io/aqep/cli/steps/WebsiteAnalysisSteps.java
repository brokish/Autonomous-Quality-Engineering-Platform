package io.aqep.cli.steps;

import io.aqep.ai.QualityAiAgent;
import io.aqep.core.SiteCrawler;
import io.aqep.reporting.QualityReportWriter;
import io.aqep.shared.AnalysisConfig;
import io.aqep.shared.AnalysisResult;
import java.io.IOException;

public final class WebsiteAnalysisSteps {
    private final SiteCrawler crawler;
    private final QualityAiAgent aiAgent;
    private final QualityReportWriter reportWriter;

    public WebsiteAnalysisSteps(SiteCrawler crawler, QualityAiAgent aiAgent, QualityReportWriter reportWriter) {
        this.crawler = crawler;
        this.aiAgent = aiAgent;
        this.reportWriter = reportWriter;
    }

    public AnalysisResult analyzeAndReport(AnalysisConfig config, boolean enableAi) throws IOException {
        AnalysisResult result = crawler.analyze(config);
        if (enableAi) result = aiAgent.enrich(result, config);
        reportWriter.write(result, config.outputDirectory());
        return result;
    }
}
