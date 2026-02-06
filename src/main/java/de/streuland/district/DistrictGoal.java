package de.streuland.district;

/**
 * Goal definition for district progression.
 */
public interface DistrictGoal {
    String getId();

    String getDescription();

    boolean isCompleted(District district);
}
