package com.example.features.googleform.domain.model

data class QuestionDefinition(
    val key: String,
    val title: String,
    val description: String,
    val isRequired: Boolean,
    val isParagraph: Boolean = false,
    val choices: List<String>? = null
)

object QuestionKeys {
    const val FULL_NAME = "FULL_NAME"
    const val PHONE = "PHONE"
    const val PERMANENT_ADDRESS = "PERMANENT_ADDRESS"
    
    const val EMAIL = "EMAIL"
    const val CURRENT_ADDRESS = "CURRENT_ADDRESS"
    const val DOB = "DOB"
    const val GENDER = "GENDER"
    const val OCCUPATION = "OCCUPATION"
    const val OCCUPATION_OTHER = "OCCUPATION_OTHER"
    const val ORGANIZATION = "ORGANIZATION"
    const val NOTES = "NOTES"
    const val FOOD_PREFERENCE = "FOOD_PREFERENCE"
    const val FATHER_NAME = "FATHER_NAME"
    const val FATHER_PHONE = "FATHER_PHONE"
    const val GUARDIAN_NAME = "GUARDIAN_NAME"
    const val GUARDIAN_RELATIONSHIP = "GUARDIAN_RELATIONSHIP"
    const val GUARDIAN_PHONE = "GUARDIAN_PHONE"
    const val LOCAL_REF_NAME = "LOCAL_REF_NAME"
    const val LOCAL_REF_PHONE = "LOCAL_REF_PHONE"
    const val OFFICE_ADDRESS = "OFFICE_ADDRESS"
    const val OFFICE_PHONE = "OFFICE_PHONE"
    const val VEHICLE_NUMBER = "VEHICLE_NUMBER"
    const val EMERGENCY_NAME = "EMERGENCY_NAME"
    const val EMERGENCY_PHONE = "EMERGENCY_PHONE"
    const val EMERGENCY_RELATION = "EMERGENCY_RELATION"
    const val EXPECTED_JOINING_DATE = "EXPECTED_JOINING_DATE"
    const val EXPECTED_VACATE_DATE = "EXPECTED_VACATE_DATE"
    const val REFUNDABLE_DEPOSIT = "REFUNDABLE_DEPOSIT"

    val REQUIRED_QUESTIONS = listOf(
        QuestionDefinition(
            key = FULL_NAME,
            title = "Full Name",
            description = "Enter your full legal name as per ID proof",
            isRequired = true,
            isParagraph = false
        ),
        QuestionDefinition(
            key = PHONE,
            title = "Mobile Number",
            description = "10-digit primary mobile number for communication",
            isRequired = true,
            isParagraph = false
        ),
        QuestionDefinition(
            key = PERMANENT_ADDRESS,
            title = "Permanent Address",
            description = "Your permanent residential home address with PIN code",
            isRequired = true,
            isParagraph = true
        )
    )

    val OPTIONAL_QUESTIONS = listOf(
        QuestionDefinition(
            key = EMAIL,
            title = "Email Address",
            description = "Email ID for digital rent receipts and notices",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = CURRENT_ADDRESS,
            title = "Current / Local Address",
            description = "Your current local accommodation address if different",
            isRequired = false,
            isParagraph = true
        ),
        QuestionDefinition(
            key = DOB,
            title = "Date of Birth",
            description = "Select Date of Birth (YYYY-MM-DD)",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = GENDER,
            title = "Gender",
            description = "Select your gender",
            isRequired = false,
            isParagraph = false,
            choices = listOf("Male", "Female", "Other")
        ),
        QuestionDefinition(
            key = OCCUPATION,
            title = "Occupation",
            description = "e.g., Software Engineer, Student, Business Analyst",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = OCCUPATION_OTHER,
            title = "Occupation (Other)",
            description = "Specify occupation if other",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = ORGANIZATION,
            title = "Company / College / Office Name",
            description = "Name of workplace, company or educational institution",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = FOOD_PREFERENCE,
            title = "Food Preference",
            description = "Select food preference",
            isRequired = false,
            isParagraph = false,
            choices = listOf("Veg", "Non-Veg", "Both / Any")
        ),
        QuestionDefinition(
            key = FATHER_NAME,
            title = "Father's Name",
            description = "Father's full name",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = FATHER_PHONE,
            title = "Father's Phone Number",
            description = "Father's contact number",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = GUARDIAN_NAME,
            title = "Guardian Name",
            description = "Guardian name if applicable",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = GUARDIAN_RELATIONSHIP,
            title = "Guardian Relationship",
            description = "Select relationship",
            isRequired = false,
            isParagraph = false,
            choices = listOf("Parent", "Guardian", "Relative", "Spouse", "Friend")
        ),
        QuestionDefinition(
            key = GUARDIAN_PHONE,
            title = "Guardian Phone Number",
            description = "Guardian contact number",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = LOCAL_REF_NAME,
            title = "Local Reference Name",
            description = "Local reference name",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = LOCAL_REF_PHONE,
            title = "Local Reference Phone Number",
            description = "Local reference phone number",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = OFFICE_ADDRESS,
            title = "Office / College Address",
            description = "Address of workplace or college",
            isRequired = false,
            isParagraph = true
        ),
        QuestionDefinition(
            key = OFFICE_PHONE,
            title = "Office / College Phone Number",
            description = "Office or college phone number",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = VEHICLE_NUMBER,
            title = "Vehicle Registration Number",
            description = "Vehicle registration number",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = NOTES,
            title = "Additional Information",
            description = "Any special preferences or requests",
            isRequired = false,
            isParagraph = true
        )
    )

    val ALL_QUESTIONS: List<QuestionDefinition> = REQUIRED_QUESTIONS + OPTIONAL_QUESTIONS

    fun normalize(text: String): String = text.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    fun resolveQuestionKey(rawTitle: String, itemIndex: Int? = null): String? {
        val norm = normalize(rawTitle)
        if (norm.isBlank() && itemIndex != null && itemIndex in ALL_QUESTIONS.indices) {
            return ALL_QUESTIONS[itemIndex].key
        }

        // Direct normalized title matches
        ALL_QUESTIONS.find { normalize(it.title) == norm }?.let { return it.key }

        // Comprehensive semantic matching by keywords & common variants
        return when {
            // Father fields
            (norm.contains("father") || norm.contains("dad")) && (norm.contains("phone") || norm.contains("mobile") || norm.contains("contact") || norm.contains("number") || norm.contains("no")) -> FATHER_PHONE
            norm.contains("father") || norm.contains("dad") -> FATHER_NAME

            // Guardian fields
            norm.contains("guardian") && (norm.contains("relat") || norm.contains("kin")) -> GUARDIAN_RELATIONSHIP
            norm.contains("guardian") && (norm.contains("phone") || norm.contains("mobile") || norm.contains("contact") || norm.contains("number") || norm.contains("no")) -> GUARDIAN_PHONE
            norm.contains("guardian") -> GUARDIAN_NAME

            // Emergency contact fields
            (norm.contains("emergency") || norm.contains("sos")) && (norm.contains("relat") || norm.contains("kin")) -> EMERGENCY_RELATION
            (norm.contains("emergency") || norm.contains("sos")) && (norm.contains("phone") || norm.contains("mobile") || norm.contains("contact") || norm.contains("number") || norm.contains("no")) -> EMERGENCY_PHONE
            norm.contains("emergency") || norm.contains("sos") -> EMERGENCY_NAME

            // Local reference fields
            (norm.contains("local") || norm.contains("ref")) && (norm.contains("phone") || norm.contains("mobile") || norm.contains("contact") || norm.contains("number") || norm.contains("no")) -> LOCAL_REF_PHONE
            norm.contains("localref") || norm.contains("localreference") || (norm.contains("reference") && !norm.contains("food")) -> LOCAL_REF_NAME

            // Vehicle
            norm.contains("vehicle") || norm.contains("bike") || norm.contains("car") || norm.contains("regno") || norm.contains("registrationnumber") -> VEHICLE_NUMBER

            // Office / Workplace fields
            norm.contains("office") && (norm.contains("phone") || norm.contains("mobile") || norm.contains("contact") || norm.contains("number") || norm.contains("no")) -> OFFICE_PHONE
            norm.contains("office") && norm.contains("address") -> OFFICE_ADDRESS
            norm.contains("company") || norm.contains("college") || norm.contains("workplace") || norm.contains("organization") || norm.contains("officename") || norm.contains("employer") || norm.contains("institution") -> ORGANIZATION

            // Occupation
            (norm.contains("other") && (norm.contains("occupation") || norm.contains("profession") || norm.contains("job"))) || norm.contains("ifother") -> OCCUPATION_OTHER
            norm.contains("occupation") || norm.contains("profession") || norm.contains("job") || norm.contains("designation") -> OCCUPATION

            // Joining & Vacate Dates
            norm.contains("joining") || norm.contains("movein") || norm.contains("startdate") -> EXPECTED_JOINING_DATE
            norm.contains("vacate") || norm.contains("leaving") || norm.contains("moveout") -> EXPECTED_VACATE_DATE

            // Deposits
            norm.contains("deposit") || norm.contains("securitydeposit") || norm.contains("refundable") -> REFUNDABLE_DEPOSIT

            // Food preference
            norm.contains("food") || norm.contains("diet") || norm.contains("veg") || norm.contains("meal") -> FOOD_PREFERENCE

            // DOB
            norm.contains("dob") || norm.contains("birth") -> DOB

            // Gender
            norm.contains("gender") || norm.contains("sex") -> GENDER

            // Address
            norm.contains("permanent") || norm.contains("homeaddress") || norm.contains("nativeaddress") -> PERMANENT_ADDRESS
            norm.contains("current") || norm.contains("localaddress") || norm.contains("presentaddress") || norm.contains("stayaddress") -> CURRENT_ADDRESS
            norm.contains("address") -> PERMANENT_ADDRESS

            // Email
            norm.contains("email") || norm.contains("mail") -> EMAIL

            // Phone (primary mobile number)
            norm.contains("phone") || norm.contains("mobile") || norm.contains("whatsapp") || norm.contains("contact") -> PHONE

            // Full Name (primary applicant)
            norm.contains("name") -> FULL_NAME

            // Notes / Comments
            norm.contains("note") || norm.contains("comment") || norm.contains("remark") || norm.contains("information") || norm.contains("preference") || norm.contains("request") -> NOTES

            else -> null
        }
    }
}

