package io.aqep.ai;

import io.aqep.shared.AnalysisConfig;
import io.aqep.shared.AnalysisResult;

public interface QualityAiAgent {
    AnalysisResult enrich(AnalysisResult analysis, AnalysisConfig config);
}
