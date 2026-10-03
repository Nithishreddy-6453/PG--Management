package com.example.features.googleform.domain.model

data class QuestionDefinition(
    val key: String,
    val title: String,
    val description: String,
    val isRequired: Boolean,
    val isParagraph: Boolean = false
)

object QuestionKeys {
    const val FULL_NAME = "FULL_NAME"
    const val PHONE = "PHONE"
    const val EMERGENCY_NAME = "EMERGENCY_NAME"
    const val EMERGENCY_PHONE = "EMERGENCY_PHONE"
    const val EMERGENCY_RELATION = "EMERGENCY_RELATION"
    const val PERMANENT_ADDRESS = "PERMANENT_ADDRESS"
    
    const val EMAIL = "EMAIL"
    const val CURRENT_ADDRESS = "CURRENT_ADDRESS"
    const val OCCUPATION = "OCCUPATION"
    const val ORGANIZATION = "ORGANIZATION"
    const val EXPECTED_JOINING_DATE = "EXPECTED_JOINING_DATE"
    const val NOTES = "NOTES"

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
            key = EMERGENCY_NAME,
            title = "Emergency Contact Name",
            description = "Name of parent, guardian or close relative",
            isRequired = true,
            isParagraph = false
        ),
        QuestionDefinition(
            key = EMERGENCY_PHONE,
            title = "Emergency Contact Mobile Number",
            description = "Contact number to reach in case of emergencies",
            isRequired = true,
            isParagraph = false
        ),
        QuestionDefinition(
            key = EMERGENCY_RELATION,
            title = "Relationship to Emergency Contact",
            description = "e.g., Father, Mother, Sibling, Spouse, Friend",
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
            key = OCCUPATION,
            title = "Occupation / Profile",
            description = "e.g., Software Engineer, Student, Business Analyst",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = ORGANIZATION,
            title = "Company / College Name",
            description = "Name of workplace or educational institution",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = EXPECTED_JOINING_DATE,
            title = "Expected Joining Date",
            description = "Estimated move-in date (YYYY-MM-DD)",
            isRequired = false,
            isParagraph = false
        ),
        QuestionDefinition(
            key = NOTES,
            title = "Additional Information",
            description = "Any special preferences, food habits, or requests",
            isRequired = false,
            isParagraph = true
        )
    )

    val ALL_QUESTIONS: List<QuestionDefinition> = REQUIRED_QUESTIONS + OPTIONAL_QUESTIONS
}
