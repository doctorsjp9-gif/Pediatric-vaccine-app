package com.example.ui.util

import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.ui.components.calculateAgeFromDob
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Represents a single vaccine dose specification within a pediatric immunization milestone.
 */
data class ScheduledVaccineSpec(
    val code: String,
    val displayName: String,
    val shortName: String,
    val doseLabel: String,
    val diseaseProtection: String,
    val matchKeywords: List<String>,
    val doseKeywords: List<String> = emptyList(),
    val previousDoseCode: String? = null,
    val minIntervalDaysFromPrevious: Int = 28
)

/**
 * Represents an age milestone in the recommended pediatric immunization schedule (IAP / NIS / WHO).
 */
data class ImmunizationMilestoneSpec(
    val id: String,
    val title: String,
    val badgeLabel: String,
    val ageOffsetDays: Int,
    val ageOffsetMonths: Int? = null,
    val description: String,
    val vaccines: List<ScheduledVaccineSpec>
)

/**
 * Status of an individual scheduled vaccine or milestone for a specific patient.
 */
enum class ScheduleItemStatus {
    COMPLETED,
    OVERDUE,
    DUE_SOON,
    UPCOMING
}

/**
 * Evaluated status of a single vaccine within a milestone for a given patient.
 */
data class EvaluatedScheduledVaccine(
    val spec: ScheduledVaccineSpec,
    val status: ScheduleItemStatus,
    val recommendedDueDateMillis: Long,
    val calculatedDueDateMillis: Long,
    val matchedRecord: PatientVaccineEntry? = null,
    val isCatchUpAdjusted: Boolean = false
)

/**
 * Evaluated status of an entire milestone for a given patient.
 */
data class EvaluatedMilestone(
    val spec: ImmunizationMilestoneSpec,
    val recommendedDueDateMillis: Long,
    val calculatedDueDateMillis: Long,
    val formattedDueDate: String,
    val relativeDueLabel: String,
    val status: ScheduleItemStatus,
    val vaccines: List<EvaluatedScheduledVaccine>,
    val completedCount: Int,
    val totalCount: Int
)

/**
 * Result of resolving or estimating a pediatric patient's Date of Birth from `dobOrAge`.
 */
data class DobResolution(
    val dobMillis: Long,
    val isExactDob: Boolean,
    val formattedDob: String,
    val calculatedAgeText: String,
    val ageInDays: Int,
    val ageInWeeks: Int,
    val ageInMonths: Int
)

/**
 * Complete evaluated immunization schedule for a pediatric patient.
 */
data class PatientScheduleSummary(
    val dobResolution: DobResolution,
    val milestones: List<EvaluatedMilestone>,
    val totalRecommendedDoses: Int,
    val completedDosesCount: Int,
    val overdueDosesCount: Int,
    val dueSoonDosesCount: Int,
    val nextUpcomingMilestone: EvaluatedMilestone?,
    val nextRecommendedVaccineNames: List<String>,
    val nextRecommendedDueDateMillis: Long?,
    val nextRecommendedDueDateLabel: String
)

/**
 * Pediatric Vaccine Scheduling Engine that calculates upcoming due dates for pediatric patients
 * based on their Date of Birth (DOB), recommended immunization schedules (IAP / NIS),
 * and previously administered vaccine dates (including catch-up minimum intervals).
 */
object PediatricVaccineScheduleEngine {

    private val dateFormats = listOf(
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()),
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()),
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    )

    val MILESTONE_SPECS: List<ImmunizationMilestoneSpec> = listOf(
        ImmunizationMilestoneSpec(
            id = "birth",
            title = "At Birth (Within 24 Hours)",
            badgeLabel = "Birth",
            ageOffsetDays = 0,
            ageOffsetMonths = 0,
            description = "Essential newborn immunization immediately after birth",
            vaccines = listOf(
                ScheduledVaccineSpec(
                    code = "BCG",
                    displayName = "BCG",
                    shortName = "BCG",
                    doseLabel = "At Birth",
                    diseaseProtection = "Tuberculosis",
                    matchKeywords = listOf("bcg")
                ),
                ScheduledVaccineSpec(
                    code = "OPV-0",
                    displayName = "OPV-0 (Oral Polio)",
                    shortName = "OPV-0",
                    doseLabel = "At Birth",
                    diseaseProtection = "Poliomyelitis",
                    matchKeywords = listOf("opv-0", "opv 0", "opv0", "oral polio"),
                    doseKeywords = listOf("birth", "0", "dose 0")
                ),
                ScheduledVaccineSpec(
                    code = "HepB-1",
                    displayName = "Hepatitis B (Birth Dose)",
                    shortName = "Hepatitis B-0",
                    doseLabel = "At Birth",
                    diseaseProtection = "Hepatitis B",
                    matchKeywords = listOf("hepatitis b-0", "hep b-0", "hepb-0", "hepatitis b", "hep b"),
                    doseKeywords = listOf("birth", "0", "dose 0", "dose 1")
                )
            )
        ),
        ImmunizationMilestoneSpec(
            id = "6_weeks",
            title = "6 Weeks (1.5 Months)",
            badgeLabel = "6 Wks",
            ageOffsetDays = 42,
            ageOffsetMonths = null,
            description = "First primary infant series at 6 weeks of age",
            vaccines = listOf(
                ScheduledVaccineSpec(
                    code = "Penta-1",
                    displayName = "Pentavalent-1 / Hexavalent-1 (DTwP/DTaP-1)",
                    shortName = "Pentavalent-1",
                    doseLabel = "Dose 1",
                    diseaseProtection = "Diphtheria, Tetanus, Pertussis, Hib, Hep B",
                    matchKeywords = listOf("pentavalent-1", "pentavalent 1", "hexavalent-1", "dtwp-1", "dtap-1", "pentavalent", "hexavalent"),
                    doseKeywords = listOf("dose 1", "1", "6w", "6 weeks")
                ),
                ScheduledVaccineSpec(
                    code = "Rota-1",
                    displayName = "Rotavirus-1 (RVV-1)",
                    shortName = "Rotavirus-1",
                    doseLabel = "Dose 1",
                    diseaseProtection = "Severe Rotavirus Diarrhea",
                    matchKeywords = listOf("rotavirus-1", "rotavirus 1", "rota-1", "rvv-1", "rotavirus"),
                    doseKeywords = listOf("dose 1", "1", "6w")
                ),
                ScheduledVaccineSpec(
                    code = "PCV-1",
                    displayName = "PCV-1 (Pneumococcal Conjugate)",
                    shortName = "PCV-1",
                    doseLabel = "Dose 1",
                    diseaseProtection = "Pneumococcal Pneumonia & Meningitis",
                    matchKeywords = listOf("pcv-1", "pcv 1", "pneumococcal-1", "pcv"),
                    doseKeywords = listOf("dose 1", "1", "6w")
                ),
                ScheduledVaccineSpec(
                    code = "IPV-1",
                    displayName = "IPV-1 (Injectable Polio)",
                    shortName = "IPV-1",
                    doseLabel = "Dose 1",
                    diseaseProtection = "Poliomyelitis",
                    matchKeywords = listOf("ipv-1", "ipv 1", "ipv"),
                    doseKeywords = listOf("dose 1", "1", "6w")
                )
            )
        ),
        ImmunizationMilestoneSpec(
            id = "10_weeks",
            title = "10 Weeks (2.5 Months)",
            badgeLabel = "10 Wks",
            ageOffsetDays = 70,
            ageOffsetMonths = null,
            description = "Second primary series (minimum 4 weeks after 6-week doses)",
            vaccines = listOf(
                ScheduledVaccineSpec(
                    code = "Penta-2",
                    displayName = "Pentavalent-2 / Hexavalent-2 (DTwP/DTaP-2)",
                    shortName = "Pentavalent-2",
                    doseLabel = "Dose 2",
                    diseaseProtection = "Diphtheria, Tetanus, Pertussis, Hib, Hep B",
                    matchKeywords = listOf("pentavalent-2", "pentavalent 2", "hexavalent-2", "dtwp-2", "dtap-2", "pentavalent", "hexavalent"),
                    doseKeywords = listOf("dose 2", "2", "10w", "10 weeks"),
                    previousDoseCode = "Penta-1",
                    minIntervalDaysFromPrevious = 28
                ),
                ScheduledVaccineSpec(
                    code = "Rota-2",
                    displayName = "Rotavirus-2 (RVV-2)",
                    shortName = "Rotavirus-2",
                    doseLabel = "Dose 2",
                    diseaseProtection = "Severe Rotavirus Diarrhea",
                    matchKeywords = listOf("rotavirus-2", "rotavirus 2", "rota-2", "rvv-2", "rotavirus"),
                    doseKeywords = listOf("dose 2", "2", "10w"),
                    previousDoseCode = "Rota-1",
                    minIntervalDaysFromPrevious = 28
                ),
                ScheduledVaccineSpec(
                    code = "PCV-2",
                    displayName = "PCV-2 (Pneumococcal Conjugate)",
                    shortName = "PCV-2",
                    doseLabel = "Dose 2",
                    diseaseProtection = "Pneumococcal Pneumonia & Meningitis",
                    matchKeywords = listOf("pcv-2", "pcv 2", "pneumococcal-2", "pcv"),
                    doseKeywords = listOf("dose 2", "2", "10w"),
                    previousDoseCode = "PCV-1",
                    minIntervalDaysFromPrevious = 28
                ),
                ScheduledVaccineSpec(
                    code = "IPV-2",
                    displayName = "IPV-2 (Injectable Polio)",
                    shortName = "IPV-2",
                    doseLabel = "Dose 2",
                    diseaseProtection = "Poliomyelitis",
                    matchKeywords = listOf("ipv-2", "ipv 2", "ipv"),
                    doseKeywords = listOf("dose 2", "2", "10w"),
                    previousDoseCode = "IPV-1",
                    minIntervalDaysFromPrevious = 28
                )
            )
        ),
        ImmunizationMilestoneSpec(
            id = "14_weeks",
            title = "14 Weeks (3.5 Months)",
            badgeLabel = "14 Wks",
            ageOffsetDays = 98,
            ageOffsetMonths = null,
            description = "Third primary infant series (minimum 4 weeks after 10-week doses)",
            vaccines = listOf(
                ScheduledVaccineSpec(
                    code = "Penta-3",
                    displayName = "Pentavalent-3 / Hexavalent-3 (DTwP/DTaP-3)",
                    shortName = "Pentavalent-3",
                    doseLabel = "Dose 3",
                    diseaseProtection = "Diphtheria, Tetanus, Pertussis, Hib, Hep B",
                    matchKeywords = listOf("pentavalent-3", "pentavalent 3", "hexavalent-3", "dtwp-3", "dtap-3", "pentavalent", "hexavalent"),
                    doseKeywords = listOf("dose 3", "3", "14w", "14 weeks"),
                    previousDoseCode = "Penta-2",
                    minIntervalDaysFromPrevious = 28
                ),
                ScheduledVaccineSpec(
                    code = "Rota-3",
                    displayName = "Rotavirus-3 (RVV-3)",
                    shortName = "Rotavirus-3",
                    doseLabel = "Dose 3",
                    diseaseProtection = "Severe Rotavirus Diarrhea",
                    matchKeywords = listOf("rotavirus-3", "rotavirus 3", "rota-3", "rvv-3", "rotavirus"),
                    doseKeywords = listOf("dose 3", "3", "14w"),
                    previousDoseCode = "Rota-2",
                    minIntervalDaysFromPrevious = 28
                ),
                ScheduledVaccineSpec(
                    code = "PCV-3",
                    displayName = "PCV-3 (Pneumococcal Conjugate)",
                    shortName = "PCV-3",
                    doseLabel = "Dose 3",
                    diseaseProtection = "Pneumococcal Pneumonia & Meningitis",
                    matchKeywords = listOf("pcv-3", "pcv 3", "pneumococcal-3", "pcv"),
                    doseKeywords = listOf("dose 3", "3", "14w"),
                    previousDoseCode = "PCV-2",
                    minIntervalDaysFromPrevious = 28
                ),
                ScheduledVaccineSpec(
                    code = "IPV-3",
                    displayName = "IPV-3 (Injectable Polio)",
                    shortName = "IPV-3",
                    doseLabel = "Dose 3",
                    diseaseProtection = "Poliomyelitis",
                    matchKeywords = listOf("ipv-3", "ipv 3", "ipv"),
                    doseKeywords = listOf("dose 3", "3", "14w"),
                    previousDoseCode = "IPV-2",
                    minIntervalDaysFromPrevious = 28
                )
            )
        ),
        ImmunizationMilestoneSpec(
            id = "6_months",
            title = "6 Months",
            badgeLabel = "6 Mos",
            ageOffsetDays = 182,
            ageOffsetMonths = 6,
            description = "Influenza & Oral Polio protection at 6 months",
            vaccines = listOf(
                ScheduledVaccineSpec(
                    code = "Flu-1",
                    displayName = "Influenza-1 (IIV-1)",
                    shortName = "Influenza-1",
                    doseLabel = "Dose 1",
                    diseaseProtection = "Seasonal Influenza (Flu)",
                    matchKeywords = listOf("influenza-1", "influenza 1", "flu-1", "influenza", "flu"),
                    doseKeywords = listOf("dose 1", "1")
                ),
                ScheduledVaccineSpec(
                    code = "OPV-1",
                    displayName = "OPV-1 (Oral Polio)",
                    shortName = "OPV-1",
                    doseLabel = "Dose 1",
                    diseaseProtection = "Poliomyelitis",
                    matchKeywords = listOf("opv-1", "opv 1", "opv"),
                    doseKeywords = listOf("dose 1", "1", "6m")
                )
            )
        ),
        ImmunizationMilestoneSpec(
            id = "7_months",
            title = "7 Months",
            badgeLabel = "7 Mos",
            ageOffsetDays = 212,
            ageOffsetMonths = 7,
            description = "Second dose of Influenza vaccine (4 weeks after Dose 1)",
            vaccines = listOf(
                ScheduledVaccineSpec(
                    code = "Flu-2",
                    displayName = "Influenza-2 (IIV-2)",
                    shortName = "Influenza-2",
                    doseLabel = "Dose 2",
                    diseaseProtection = "Seasonal Influenza (Flu)",
                    matchKeywords = listOf("influenza-2", "influenza 2", "flu-2", "influenza", "flu"),
                    doseKeywords = listOf("dose 2", "2"),
                    previousDoseCode = "Flu-1",
                    minIntervalDaysFromPrevious = 28
                )
            )
        ),
        ImmunizationMilestoneSpec(
            id = "9_months",
            title = "9 Months",
            badgeLabel = "9 Mos",
            ageOffsetDays = 274,
            ageOffsetMonths = 9,
            description = "MMR-1 & Typhoid Conjugate protection",
            vaccines = listOf(
                ScheduledVaccineSpec(
                    code = "MMR-1",
                    displayName = "MMR-1 (Measles, Mumps, Rubella)",
                    shortName = "MMR-1",
                    doseLabel = "Dose 1",
                    diseaseProtection = "Measles, Mumps & Rubella",
                    matchKeywords = listOf("mmr-1", "mmr 1", "mmr", "mr-1", "measles"),
                    doseKeywords = listOf("dose 1", "1", "9m")
                ),
                ScheduledVaccineSpec(
                    code = "TCV-1",
                    displayName = "Typhoid Conjugate Vaccine (TCV)",
                    shortName = "Typhoid (TCV)",
                    doseLabel = "Dose 1",
                    diseaseProtection = "Typhoid Fever",
                    matchKeywords = listOf("typhoid", "tcv", "typhbar"),
                    doseKeywords = listOf("dose 1", "1", "9m")
                )
            )
        ),
        ImmunizationMilestoneSpec(
            id = "12_months",
            title = "12 Months (1 Year)",
            badgeLabel = "12 Mos",
            ageOffsetDays = 365,
            ageOffsetMonths = 12,
            description = "Hepatitis A first dose at 1 year of age",
            vaccines = listOf(
                ScheduledVaccineSpec(
                    code = "HepA-1",
                    displayName = "Hepatitis A-1",
                    shortName = "Hepatitis A-1",
                    doseLabel = "Dose 1",
                    diseaseProtection = "Hepatitis A Liver Infection",
                    matchKeywords = listOf("hepatitis a-1", "hepatitis a 1", "hep a-1", "hepatitis a", "hepa"),
                    doseKeywords = listOf("dose 1", "1", "12m")
                )
            )
        ),
        ImmunizationMilestoneSpec(
            id = "15_months",
            title = "15 Months",
            badgeLabel = "15 Mos",
            ageOffsetDays = 456,
            ageOffsetMonths = 15,
            description = "MMR-2, Chickenpox (Varicella-1), and PCV Booster",
            vaccines = listOf(
                ScheduledVaccineSpec(
                    code = "MMR-2",
                    displayName = "MMR-2 (Measles, Mumps, Rubella)",
                    shortName = "MMR-2",
                    doseLabel = "Dose 2",
                    diseaseProtection = "Measles, Mumps & Rubella",
                    matchKeywords = listOf("mmr-2", "mmr 2", "mmr"),
                    doseKeywords = listOf("dose 2", "2", "15m"),
                    previousDoseCode = "MMR-1",
                    minIntervalDaysFromPrevious = 90
                ),
                ScheduledVaccineSpec(
                    code = "Varicella-1",
                    displayName = "Varicella-1 (Chickenpox)",
                    shortName = "Varicella-1",
                    doseLabel = "Dose 1",
                    diseaseProtection = "Varicella (Chickenpox)",
                    matchKeywords = listOf("varicella-1", "varicella 1", "varicella", "chickenpox"),
                    doseKeywords = listOf("dose 1", "1", "15m")
                ),
                ScheduledVaccineSpec(
                    code = "PCV-Booster",
                    displayName = "PCV Booster",
                    shortName = "PCV Booster",
                    doseLabel = "Booster 1",
                    diseaseProtection = "Pneumococcal Disease Booster",
                    matchKeywords = listOf("pcv booster", "pcv-booster", "pcv"),
                    doseKeywords = listOf("booster", "booster 1", "dose 4", "15m"),
                    previousDoseCode = "PCV-3",
                    minIntervalDaysFromPrevious = 60
                )
            )
        ),
        ImmunizationMilestoneSpec(
            id = "18_months",
            title = "16–18 Months (1.5 Years)",
            badgeLabel = "18 Mos",
            ageOffsetDays = 515,
            ageOffsetMonths = 18,
            description = "First DTP, Hib, IPV Boosters & Hepatitis A-2",
            vaccines = listOf(
                ScheduledVaccineSpec(
                    code = "DTP-Booster-1",
                    displayName = "DTwP/DTaP Booster-1 (Pentavalent/Hexa Booster)",
                    shortName = "DTP Booster-1",
                    doseLabel = "Booster 1",
                    diseaseProtection = "Diphtheria, Tetanus, Pertussis, Hib",
                    matchKeywords = listOf("dtp booster", "dtwp booster", "dtap booster", "pentavalent booster", "hexavalent booster"),
                    doseKeywords = listOf("booster", "booster 1", "dose 1", "18m"),
                    previousDoseCode = "Penta-3",
                    minIntervalDaysFromPrevious = 180
                ),
                ScheduledVaccineSpec(
                    code = "IPV-Booster-1",
                    displayName = "IPV Booster-1",
                    shortName = "IPV Booster",
                    doseLabel = "Booster 1",
                    diseaseProtection = "Poliomyelitis Booster",
                    matchKeywords = listOf("ipv booster", "ipv-booster", "ipv"),
                    doseKeywords = listOf("booster", "booster 1", "dose 4", "18m"),
                    previousDoseCode = "IPV-3",
                    minIntervalDaysFromPrevious = 180
                ),
                ScheduledVaccineSpec(
                    code = "HepA-2",
                    displayName = "Hepatitis A-2 (Inactivated)",
                    shortName = "Hepatitis A-2",
                    doseLabel = "Dose 2",
                    diseaseProtection = "Long-term Hepatitis A Immunity",
                    matchKeywords = listOf("hepatitis a-2", "hepatitis a 2", "hep a-2", "hepatitis a", "hepa"),
                    doseKeywords = listOf("dose 2", "2", "booster", "18m"),
                    previousDoseCode = "HepA-1",
                    minIntervalDaysFromPrevious = 180
                )
            )
        ),
        ImmunizationMilestoneSpec(
            id = "4_6_years",
            title = "4–6 Years (School Entry)",
            badgeLabel = "4-6 Yrs",
            ageOffsetDays = 1642,
            ageOffsetMonths = 54,
            description = "Pre-school boosters for DTP, OPV/IPV, MMR-3 & Varicella-2",
            vaccines = listOf(
                ScheduledVaccineSpec(
                    code = "DTP-Booster-2",
                    displayName = "DTwP/DTaP Booster-2",
                    shortName = "DTP Booster-2",
                    doseLabel = "Booster 2",
                    diseaseProtection = "Diphtheria, Tetanus, Pertussis",
                    matchKeywords = listOf("dtp booster-2", "dtp booster 2", "dtwp booster 2", "dtap booster 2"),
                    doseKeywords = listOf("booster 2", "2", "5y"),
                    previousDoseCode = "DTP-Booster-1",
                    minIntervalDaysFromPrevious = 365
                ),
                ScheduledVaccineSpec(
                    code = "MMR-3",
                    displayName = "MMR-3",
                    shortName = "MMR-3",
                    doseLabel = "Dose 3",
                    diseaseProtection = "Measles, Mumps, Rubella",
                    matchKeywords = listOf("mmr-3", "mmr 3", "mmr"),
                    doseKeywords = listOf("dose 3", "3", "booster 2"),
                    previousDoseCode = "MMR-2",
                    minIntervalDaysFromPrevious = 180
                ),
                ScheduledVaccineSpec(
                    code = "Varicella-2",
                    displayName = "Varicella-2 (Chickenpox Booster)",
                    shortName = "Varicella-2",
                    doseLabel = "Dose 2",
                    diseaseProtection = "Varicella (Chickenpox)",
                    matchKeywords = listOf("varicella-2", "varicella 2", "varicella", "chickenpox"),
                    doseKeywords = listOf("dose 2", "2", "booster"),
                    previousDoseCode = "Varicella-1",
                    minIntervalDaysFromPrevious = 90
                )
            )
        ),
        ImmunizationMilestoneSpec(
            id = "10_12_years",
            title = "9–12 Years (Adolescent)",
            badgeLabel = "9-12 Yrs",
            ageOffsetDays = 3650,
            ageOffsetMonths = 120,
            description = "Adolescent Tdap booster and HPV series",
            vaccines = listOf(
                ScheduledVaccineSpec(
                    code = "Tdap",
                    displayName = "Tdap / Td Adolescent Booster",
                    shortName = "Tdap",
                    doseLabel = "Booster",
                    diseaseProtection = "Tetanus, Diphtheria, Pertussis",
                    matchKeywords = listOf("tdap", "td booster")
                ),
                ScheduledVaccineSpec(
                    code = "HPV-1",
                    displayName = "HPV-1 (Human Papillomavirus)",
                    shortName = "HPV-1",
                    doseLabel = "Dose 1",
                    diseaseProtection = "HPV Protection",
                    matchKeywords = listOf("hpv", "cervavac", "gardasil")
                )
            )
        )
    )

    /**
     * Resolves a pediatric patient's Date of Birth (DOB) from `dobOrAge` string.
     * Supports exact formatted dates (e.g. "6 Months (DOB: 15 Apr 2026)" or "15 Apr 2026")
     * as well as age-based strings (e.g. "6 Months", "1.5 Months (6w)", "At Birth", "2 Years").
     */
    fun resolvePatientDob(
        dobOrAge: String,
        referenceMillis: Long = System.currentTimeMillis()
    ): DobResolution {
        val raw = dobOrAge.trim()
        val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // 1. Try extracting explicit DOB string after "DOB:" or anywhere in the text
        val dobRegex = Regex("""(?:DOB\s*:\s*)?(\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4}|\d{1,2}[/-]\d{1,2}[/-]\d{4}|\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)
        val match = dobRegex.find(raw)
        if (match != null) {
            val candidate = match.groupValues[1].trim()
            for (fmt in dateFormats) {
                try {
                    val parsed = fmt.parse(candidate)
                    if (parsed != null && parsed.time <= System.currentTimeMillis() + 86400000L) {
                        val dobMillis = normalizeToMorningMillis(parsed.time)
                        return buildDobResolution(dobMillis, isExactDob = true, displayFormat = displayFormat)
                    }
                } catch (_: Exception) {
                }
            }
        }

        // 2. Fallback: Estimate DOB from age string (e.g., "At Birth", "6 Weeks", "1.5 Months (6w)", "6 Months", "2 Years")
        val cal = Calendar.getInstance().apply { timeInMillis = referenceMillis }
        val lower = raw.lowercase()

        when {
            lower.contains("birth") || lower.contains("newborn") -> {
                // 0 days ago
            }
            lower.contains("6w") || lower.contains("1.5 month") || lower.contains("6 week") -> {
                cal.add(Calendar.DAY_OF_YEAR, -42)
            }
            lower.contains("10w") || lower.contains("2.5 month") || lower.contains("10 week") -> {
                cal.add(Calendar.DAY_OF_YEAR, -70)
            }
            lower.contains("14w") || lower.contains("3.5 month") || lower.contains("14 week") -> {
                cal.add(Calendar.DAY_OF_YEAR, -98)
            }
            else -> {
                val weeksMatch = Regex("""(\d+)\s*(?:weeks?|wks?|w)\b""").find(lower)
                val yearsMatch = Regex("""(\d+)\s*(?:years?|yrs?|y)\b""").find(lower)
                val monthsMatch = Regex("""(\d+)\s*(?:months?|mos?|m)\b""").find(lower)
                val daysMatch = Regex("""(\d+)\s*(?:days?|d)\b""").find(lower)

                if (yearsMatch != null || monthsMatch != null || weeksMatch != null || daysMatch != null) {
                    val yrs = yearsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val mos = monthsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val wks = weeksMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val dys = daysMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                    if (yrs > 0) cal.add(Calendar.YEAR, -yrs)
                    if (mos > 0) cal.add(Calendar.MONTH, -mos)
                    if (wks > 0) cal.add(Calendar.DAY_OF_YEAR, -(wks * 7))
                    if (dys > 0) cal.add(Calendar.DAY_OF_YEAR, -dys)
                } else {
                    val anyNum = Regex("""(\d+)""").find(lower)?.groupValues?.get(1)?.toIntOrNull()
                    if (anyNum != null) {
                        cal.add(Calendar.MONTH, -anyNum)
                    } else {
                        cal.add(Calendar.MONTH, -6)
                    }
                }
            }
        }

        val estimatedDob = normalizeToMorningMillis(cal.timeInMillis)
        return buildDobResolution(estimatedDob, isExactDob = false, displayFormat = displayFormat)
    }

    private fun buildDobResolution(
        dobMillis: Long,
        isExactDob: Boolean,
        displayFormat: SimpleDateFormat
    ): DobResolution {
        val now = System.currentTimeMillis()
        val diffMillis = max(0L, now - dobMillis)
        val ageInDays = (diffMillis / (24L * 60 * 60 * 1000)).toInt()
        val ageInWeeks = ageInDays / 7

        val dobCal = Calendar.getInstance().apply { timeInMillis = dobMillis }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        var years = nowCal.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
        var months = nowCal.get(Calendar.MONTH) - dobCal.get(Calendar.MONTH)
        if (nowCal.get(Calendar.DAY_OF_MONTH) < dobCal.get(Calendar.DAY_OF_MONTH)) {
            months--
        }
        val totalMonths = max(0, years * 12 + months)

        return DobResolution(
            dobMillis = dobMillis,
            isExactDob = isExactDob,
            formattedDob = displayFormat.format(Date(dobMillis)),
            calculatedAgeText = calculateAgeFromDob(dobMillis),
            ageInDays = ageInDays,
            ageInWeeks = ageInWeeks,
            ageInMonths = totalMonths
        )
    }

    private fun normalizeToMorningMillis(millis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Calculates the recommended milestone date from a given Date of Birth.
     */
    fun calculateMilestoneDateFromDob(dobMillis: Long, spec: ImmunizationMilestoneSpec): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dobMillis
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (spec.ageOffsetMonths != null && spec.ageOffsetMonths > 0) {
            cal.add(Calendar.MONTH, spec.ageOffsetMonths)
        } else if (spec.ageOffsetDays > 0) {
            cal.add(Calendar.DAY_OF_YEAR, spec.ageOffsetDays)
        }
        return cal.timeInMillis
    }

    /**
     * Evaluates the complete pediatric immunization schedule for a patient based on their DOB
     * and all previously given vaccines (with catch-up interval calculation).
     */
    fun evaluatePatientSchedule(
        patient: PatientRegistration,
        overrideDobMillis: Long? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): PatientScheduleSummary {
        val dobResolution = if (overrideDobMillis != null) {
            val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            buildDobResolution(overrideDobMillis, isExactDob = true, displayFormat = fmt)
        } else {
            resolvePatientDob(
                dobOrAge = patient.dobOrAge,
                referenceMillis = if (patient.dateGivenMillis > 0) patient.dateGivenMillis else nowMillis
            )
        }

        val takenEntries = PatientVaccineRecordHelper.getAllTakenVaccines(patient)
        val usedEntryIds = mutableSetOf<String>()
        val completedBySpecCode = mutableMapOf<String, PatientVaccineEntry>()
        val calculatedDateBySpecCode = mutableMapOf<String, Long>()

        val todayStartMillis = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val dueSoonEndMillis = todayStartMillis + (14L * 24 * 60 * 60 * 1000)
        val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val evaluatedMilestones = MILESTONE_SPECS.map { milestoneSpec ->
            val baseMilestoneDueMillis = calculateMilestoneDateFromDob(dobResolution.dobMillis, milestoneSpec)

            val evaluatedVaccines = milestoneSpec.vaccines.map { vacSpec ->
                val matchedEntry = findMatchingTakenEntry(vacSpec, takenEntries, usedEntryIds)
                if (matchedEntry != null) {
                    usedEntryIds.add(matchedEntry.id)
                    completedBySpecCode[vacSpec.code] = matchedEntry
                }

                // Calculate due date factoring in minimum interval from previous dose if previous dose was given late
                var effectiveDueMillis = baseMilestoneDueMillis
                var isCatchUpAdjusted = false

                val prevCode = vacSpec.previousDoseCode
                if (prevCode != null) {
                    val prevTaken = completedBySpecCode[prevCode]
                    val prevCalculated = calculatedDateBySpecCode[prevCode]
                    val minNextFromPrev = when {
                        prevTaken != null -> prevTaken.dateAdministeredMillis + (vacSpec.minIntervalDaysFromPrevious * 24L * 60 * 60 * 1000)
                        prevCalculated != null -> prevCalculated + (vacSpec.minIntervalDaysFromPrevious * 24L * 60 * 60 * 1000)
                        else -> baseMilestoneDueMillis
                    }
                    if (minNextFromPrev > effectiveDueMillis + 86400000L) {
                        effectiveDueMillis = normalizeToMorningMillis(minNextFromPrev)
                        isCatchUpAdjusted = true
                    }
                }

                calculatedDateBySpecCode[vacSpec.code] = effectiveDueMillis

                val status = when {
                    matchedEntry != null -> ScheduleItemStatus.COMPLETED
                    effectiveDueMillis < todayStartMillis -> ScheduleItemStatus.OVERDUE
                    effectiveDueMillis <= dueSoonEndMillis -> ScheduleItemStatus.DUE_SOON
                    else -> ScheduleItemStatus.UPCOMING
                }

                EvaluatedScheduledVaccine(
                    spec = vacSpec,
                    status = status,
                    recommendedDueDateMillis = baseMilestoneDueMillis,
                    calculatedDueDateMillis = effectiveDueMillis,
                    matchedRecord = matchedEntry,
                    isCatchUpAdjusted = isCatchUpAdjusted
                )
            }

            val completedCount = evaluatedVaccines.count { it.status == ScheduleItemStatus.COMPLETED }
            val pendingVaccines = evaluatedVaccines.filter { it.status != ScheduleItemStatus.COMPLETED }
            val milestoneCalculatedMillis = pendingVaccines.minOfOrNull { it.calculatedDueDateMillis }
                ?: evaluatedVaccines.maxOfOrNull { it.calculatedDueDateMillis }
                ?: baseMilestoneDueMillis

            val milestoneStatus = when {
                completedCount == evaluatedVaccines.size -> ScheduleItemStatus.COMPLETED
                pendingVaccines.any { it.status == ScheduleItemStatus.OVERDUE } -> ScheduleItemStatus.OVERDUE
                pendingVaccines.any { it.status == ScheduleItemStatus.DUE_SOON } -> ScheduleItemStatus.DUE_SOON
                else -> ScheduleItemStatus.UPCOMING
            }

            val formattedDate = displayFormat.format(Date(milestoneCalculatedMillis))
            val relativeLabel = formatRelativeDueLabel(milestoneCalculatedMillis, todayStartMillis, formattedDate)

            EvaluatedMilestone(
                spec = milestoneSpec,
                recommendedDueDateMillis = baseMilestoneDueMillis,
                calculatedDueDateMillis = milestoneCalculatedMillis,
                formattedDueDate = formattedDate,
                relativeDueLabel = relativeLabel,
                status = milestoneStatus,
                vaccines = evaluatedVaccines,
                completedCount = completedCount,
                totalCount = evaluatedVaccines.size
            )
        }

        val allEvaluatedVaccines = evaluatedMilestones.flatMap { it.vaccines }
        val totalRecommended = allEvaluatedVaccines.size
        val completedTotal = allEvaluatedVaccines.count { it.status == ScheduleItemStatus.COMPLETED }
        val overdueTotal = allEvaluatedVaccines.count { it.status == ScheduleItemStatus.OVERDUE }
        val dueSoonTotal = allEvaluatedVaccines.count { it.status == ScheduleItemStatus.DUE_SOON }

        // Next upcoming milestone is the first milestone that is not 100% completed.
        // However, if the child is older than 6 weeks and Birth milestone wasn't explicitly logged,
        // prioritize the milestone closest to or immediately following the child's current age/last given dose,
        // unless Birth is the only uncompleted milestone for a newborn.
        val uncompletedMilestones = evaluatedMilestones.filter { it.status != ScheduleItemStatus.COMPLETED }
        val nextUpcomingMilestone = if (dobResolution.ageInDays > 35 && takenEntries.isNotEmpty()) {
            // Find the first uncompleted milestone whose ageOffsetDays > highest completed milestone's ageOffsetDays,
            // or fallback to first uncompleted milestone
            val highestCompletedOffset = evaluatedMilestones
                .filter { it.completedCount > 0 }
                .maxOfOrNull { it.spec.ageOffsetDays } ?: -1
            uncompletedMilestones.firstOrNull { it.spec.ageOffsetDays > highestCompletedOffset }
                ?: uncompletedMilestones.firstOrNull()
        } else {
            uncompletedMilestones.firstOrNull()
        }

        val nextVaccineNames = nextUpcomingMilestone
            ?.vaccines
            ?.filter { it.status != ScheduleItemStatus.COMPLETED }
            ?.map { it.spec.shortName }
            ?: emptyList()

        val nextDueMillis = nextUpcomingMilestone?.calculatedDueDateMillis
        val nextDueLabel = nextUpcomingMilestone?.relativeDueLabel ?: "All Scheduled Doses Completed"

        return PatientScheduleSummary(
            dobResolution = dobResolution,
            milestones = evaluatedMilestones,
            totalRecommendedDoses = totalRecommended,
            completedDosesCount = completedTotal,
            overdueDosesCount = overdueTotal,
            dueSoonDosesCount = dueSoonTotal,
            nextUpcomingMilestone = nextUpcomingMilestone,
            nextRecommendedVaccineNames = nextVaccineNames,
            nextRecommendedDueDateMillis = nextDueMillis,
            nextRecommendedDueDateLabel = nextDueLabel
        )
    }

    /**
     * Matches a scheduled vaccine specification against the patient's administered vaccine records.
     */
    private fun findMatchingTakenEntry(
        spec: ScheduledVaccineSpec,
        takenEntries: List<PatientVaccineEntry>,
        usedEntryIds: Set<String>
    ): PatientVaccineEntry? {
        val available = takenEntries.filter { it.id !in usedEntryIds }
        if (available.isEmpty()) return null

        // 1. Exact or specific code/name match (e.g., "Pentavalent-1", "OPV-0", "MMR-2")
        val exactMatch = available.firstOrNull { entry ->
            val vLower = entry.vaccineName.trim().lowercase()
            val dLower = entry.doseName.trim().lowercase()
            vLower == spec.code.lowercase() ||
                    vLower == spec.shortName.lowercase() ||
                    vLower == spec.displayName.lowercase() ||
                    (spec.matchKeywords.firstOrNull()?.let { firstKw ->
                        firstKw.contains("-") && vLower.contains(firstKw)
                    } == true) ||
                    (spec.matchKeywords.any { kw -> vLower.contains(kw) } &&
                            spec.doseKeywords.isNotEmpty() &&
                            spec.doseKeywords.any { dKw -> dLower == dKw || dLower.contains(dKw) })
        }
        if (exactMatch != null) return exactMatch

        // 2. Fallback: Match by general vaccine family name in chronological order
        return available.firstOrNull { entry ->
            val vLower = entry.vaccineName.trim().lowercase()
            spec.matchKeywords.any { kw -> vLower.contains(kw) }
        }
    }

    fun formatRelativeDueLabel(
        targetMillis: Long,
        todayStartMillis: Long,
        formattedDate: String
    ): String {
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = targetMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val daysDiff = ((targetCal.timeInMillis - todayStartMillis) / (24L * 60 * 60 * 1000)).toInt()
        return when {
            daysDiff < 0 -> "$formattedDate (${abs(daysDiff)}d Overdue)"
            daysDiff == 0 -> "Today ($formattedDate)"
            daysDiff == 1 -> "Tomorrow ($formattedDate)"
            daysDiff in 2..6 -> "$formattedDate (In $daysDiff days)"
            daysDiff in 7..13 -> "$formattedDate (In 1 week)"
            daysDiff in 14..27 -> "$formattedDate (In ${daysDiff / 7} weeks)"
            daysDiff in 28..364 -> "$formattedDate (In ${daysDiff / 30} mos)"
            else -> "$formattedDate (In ${daysDiff / 365} yrs)"
        }
    }

    /**
     * Formats the patient's personalized DOB-based immunization schedule into a WhatsApp message (supports Hindi & English).
     */
    fun formatScheduleForWhatsApp(
        patient: PatientRegistration,
        clinicProfile: ClinicProfile,
        summary: PatientScheduleSummary
    ): String {
        val clinicName = clinicProfile.clinicName.ifBlank { "Dr Priyank Vaccine Clinic" }
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val useHindi = AppLanguageManager.currentAppLanguage == AppLanguage.HINDI ||
            AppLanguageManager.currentWhatsAppLanguage != ReminderLanguage.ENGLISH

        val sb = StringBuilder()
        if (useHindi) {
            sb.append("*$clinicName* 🩺\n")
            sb.append("*बच्चे का टीकाकरण चार्ट और आगामी तिथियां (IMMUNIZATION SCHEDULE)*\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("👶 *बच्चे का नाम:* ${patient.patientName} (${patient.gender})\n")
            sb.append("🎂 *जन्म तिथि (DOB):* ${summary.dobResolution.formattedDob} (${summary.dobResolution.calculatedAgeText})\n")
            sb.append("👨‍👩‍👦 *अभिभावक:* ${patient.parentName}\n")
            sb.append("✅ *प्रगति:* ${summary.completedDosesCount} / ${summary.totalRecommendedDoses} टीके पूर्ण\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\n\n")

            if (summary.nextUpcomingMilestone != null) {
                val next = summary.nextUpcomingMilestone
                sb.append("🔔 *अगला निर्धारित टीकाकरण (NEXT DUE VACCINE):*\n")
                sb.append("• *चरण (Stage):* ${next.spec.title}\n")
                sb.append("• *निर्धारित तिथि (Due Date):* ${next.relativeDueLabel}\n")
                sb.append("• *देय टीके (Vaccines):* ${summary.nextRecommendedVaccineNames.joinToString(", ")}\n\n")
                sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            }

            sb.append("📅 *जन्म तिथि के अनुसार संपूर्ण टीकाकरण तालिका:*\n\n")
            for (milestone in summary.milestones) {
                val icon = when (milestone.status) {
                    ScheduleItemStatus.COMPLETED -> "✅"
                    ScheduleItemStatus.OVERDUE -> "⚠️"
                    ScheduleItemStatus.DUE_SOON -> "🔔"
                    ScheduleItemStatus.UPCOMING -> "🗓️"
                }
                sb.append("$icon *${milestone.spec.title}* — निर्धारित तिथि: ${milestone.formattedDueDate}\n")
                for (v in milestone.vaccines) {
                    if (v.matchedRecord != null) {
                        val givenDate = dateFormat.format(Date(v.matchedRecord.dateAdministeredMillis))
                        sb.append("   ✔ ${v.spec.shortName} (दिया गया: $givenDate)\n")
                    } else {
                        val dueStr = dateFormat.format(Date(v.calculatedDueDateMillis))
                        sb.append("   ⏳ ${v.spec.shortName} (देय तिथि: $dueStr)\n")
                    }
                }
                sb.append("\n")
            }

            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("📍 *क्लिनिक का पता:* ${clinicProfile.address}\n")
            sb.append("📞 *संपर्क / WhatsApp:* ${clinicProfile.phone}\n")
            sb.append("👨‍⚕️ *डॉक्टर:* ${clinicProfile.doctorName}\n")
            sb.append("_समय पर टीकाकरण बच्चे को गंभीर बीमारियों से सुरक्षित रखता है!_ 🌟")
            return sb.toString()
        }

        sb.append("*$clinicName* 🩺\n")
        sb.append("*PEDIATRIC IMMUNIZATION SCHEDULE & DUE DATES*\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("👶 *Child Name:* ${patient.patientName} (${patient.gender})\n")
        sb.append("🎂 *Date of Birth:* ${summary.dobResolution.formattedDob} (${summary.dobResolution.calculatedAgeText})\n")
        sb.append("👨‍👩‍👦 *Parent:* ${patient.parentName}\n")
        sb.append("✅ *Progress:* ${summary.completedDosesCount} / ${summary.totalRecommendedDoses} Recommended Doses Completed\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\n\n")

        if (summary.nextUpcomingMilestone != null) {
            val next = summary.nextUpcomingMilestone
            sb.append("🔔 *NEXT UPCOMING VACCINATION DUE:*\n")
            sb.append("• *Stage:* ${next.spec.title}\n")
            sb.append("• *Due Date:* ${next.relativeDueLabel}\n")
            sb.append("• *Vaccines Due:* ${summary.nextRecommendedVaccineNames.joinToString(", ")}\n\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        }

        sb.append("📅 *COMPLETE AGE-WISE SCHEDULE (FROM DOB):*\n\n")
        for (milestone in summary.milestones) {
            val icon = when (milestone.status) {
                ScheduleItemStatus.COMPLETED -> "✅"
                ScheduleItemStatus.OVERDUE -> "⚠️"
                ScheduleItemStatus.DUE_SOON -> "🔔"
                ScheduleItemStatus.UPCOMING -> "🗓️"
            }
            sb.append("$icon *${milestone.spec.title}* — Due: ${milestone.formattedDueDate}\n")
            for (v in milestone.vaccines) {
                if (v.matchedRecord != null) {
                    val givenDate = dateFormat.format(Date(v.matchedRecord.dateAdministeredMillis))
                    sb.append("   ✔ ${v.spec.shortName} (Given: $givenDate)\n")
                } else {
                    val dueStr = dateFormat.format(Date(v.calculatedDueDateMillis))
                    sb.append("   ⏳ ${v.spec.shortName} (Due: $dueStr)\n")
                }
            }
            sb.append("\n")
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("📍 *Clinic:* ${clinicProfile.address}\n")
        sb.append("📞 *Contact:* ${clinicProfile.phone}\n")
        sb.append("👨‍⚕️ *Doctor:* ${clinicProfile.doctorName}\n")
        return sb.toString()
    }
}
