package net.runelite.client.plugins.microbot.aiautonomous.ai.memory;

import java.time.Instant;

public class SkillMemory {
    private String skillName;
    private int totalAttempts;
    private int successfulAttempts;
    private double averageSuccessRate;
    private Instant lastUsed;
    private Instant firstUsed;

    public SkillMemory(String skillName) {
        this.skillName = skillName;
        this.totalAttempts = 0;
        this.successfulAttempts = 0;
        this.averageSuccessRate = 0.0;
        this.firstUsed = Instant.now();
        this.lastUsed = Instant.now();
    }

    public void addAttempt(boolean successful) {
        totalAttempts++;
        if (successful) {
            successfulAttempts++;
        }
        updateSuccessRate();
        lastUsed = Instant.now();
    }

    private void updateSuccessRate() {
        if (totalAttempts > 0) {
            averageSuccessRate = (double) successfulAttempts / totalAttempts;
        }
    }

    public double getSuccessRate() {
        return averageSuccessRate;
    }

    public boolean isExperienced() {
        return totalAttempts >= 10;
    }

    public boolean isEffective() {
        return isExperienced() && averageSuccessRate > 0.7;
    }

    // Getters and setters
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }

    public int getTotalAttempts() { return totalAttempts; }
    public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }

    public int getSuccessfulAttempts() { return successfulAttempts; }
    public void setSuccessfulAttempts(int successfulAttempts) { this.successfulAttempts = successfulAttempts; }

    public double getAverageSuccessRate() { return averageSuccessRate; }
    public void setAverageSuccessRate(double averageSuccessRate) { this.averageSuccessRate = averageSuccessRate; }

    public Instant getLastUsed() { return lastUsed; }
    public void setLastUsed(Instant lastUsed) { this.lastUsed = lastUsed; }

    public Instant getFirstUsed() { return firstUsed; }
    public void setFirstUsed(Instant firstUsed) { this.firstUsed = firstUsed; }

    @Override
    public String toString() {
        return String.format("SkillMemory{skill='%s', attempts=%d, successRate=%.2f, experienced=%s}",
                skillName, totalAttempts, averageSuccessRate, isExperienced());
    }
}