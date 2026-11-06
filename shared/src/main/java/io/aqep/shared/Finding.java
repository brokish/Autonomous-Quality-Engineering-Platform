package io.aqep.shared;

public record Finding(String category, Severity severity, String pageUrl, String message, String evidence) {
    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }
}
