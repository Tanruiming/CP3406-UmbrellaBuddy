package au.edu.jcu.assessment.utilityapp.domain

import org.junit.Assert.*
import org.junit.Test

class LocationFixPolicyTest {
    @Test fun acceptsFreshApproximateLocation() {
        assertTrue(isUsableLocationFix(1.3, 103.8, 3000f, 10_000))
    }
    @Test fun rejectsStaleOrFutureLocationFixes() {
        assertFalse(isUsableLocationFix(1.3, 103.8, 20f, 120_001))
        assertFalse(isUsableLocationFix(1.3, 103.8, 20f, -1))
    }
    @Test fun rejectsInvalidCoordinatesAndAccuracy() {
        assertFalse(isUsableLocationFix(Double.NaN, 103.8, 20f, 0))
        assertFalse(isUsableLocationFix(91.0, 103.8, 20f, 0))
        assertFalse(isUsableLocationFix(1.3, 181.0, 20f, 0))
        assertFalse(isUsableLocationFix(1.3, 103.8, Float.NaN, 0))
        assertFalse(isUsableLocationFix(1.3, 103.8, 0f, 0))
    }
}
