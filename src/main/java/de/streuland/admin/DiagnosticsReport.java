package de.streuland.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class DiagnosticsReport {
    private final List<DiagnosticsIssue> issues = new ArrayList<>();

    public void addIssue(DiagnosticsIssue issue) {
        issues.add(issue);
    }

    public List<DiagnosticsIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public int getTotalIssues() {
        return issues.size();
    }

    public Map<DiagnosticsIssueType, Integer> issueCounts() {
        Map<DiagnosticsIssueType, Integer> counts = new EnumMap<>(DiagnosticsIssueType.class);
        for (DiagnosticsIssue issue : issues) {
            counts.merge(issue.getType(), 1, Integer::sum);
        }
        return counts;
    }
}
