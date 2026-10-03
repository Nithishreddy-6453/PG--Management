package com.example.features.googleform.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Creation Info model containing ONLY 'title'.
 * Google Forms API strictly requires that only 'title' is provided in POST /v1/forms.
 */
@JsonClass(generateAdapter = true)
data class CreateFormInfo(
    @Json(name = "title") val title: String
)

@JsonClass(generateAdapter = true)
data class CreateFormRequest(
    @Json(name = "info") val info: CreateFormInfo
)

/**
 * Update Form Info model for batchUpdate
 */
@JsonClass(generateAdapter = true)
data class FormDescriptionInfo(
    @Json(name = "description") val description: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateFormInfoRequest(
    @Json(name = "info") val info: FormDescriptionInfo,
    @Json(name = "updateMask") val updateMask: String = "description"
)

@JsonClass(generateAdapter = true)
data class TextQuestion(
    @Json(name = "paragraph") val paragraph: Boolean = false
)

@JsonClass(generateAdapter = true)
data class Question(
    @Json(name = "questionId") val questionId: String? = null,
    @Json(name = "required") val required: Boolean = false,
    @Json(name = "textQuestion") val textQuestion: TextQuestion? = TextQuestion()
)

@JsonClass(generateAdapter = true)
data class QuestionItem(
    @Json(name = "question") val question: Question
)

@JsonClass(generateAdapter = true)
data class FormItem(
    @Json(name = "itemId") val itemId: String? = null,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "questionItem") val questionItem: QuestionItem? = null
)

@JsonClass(generateAdapter = true)
data class CreateItemLocation(
    @Json(name = "index") val index: Int
)

@JsonClass(generateAdapter = true)
data class CreateItemRequest(
    @Json(name = "item") val item: FormItem,
    @Json(name = "location") val location: CreateItemLocation
)

@JsonClass(generateAdapter = true)
data class FormRequestItem(
    @Json(name = "updateFormInfo") val updateFormInfo: UpdateFormInfoRequest? = null,
    @Json(name = "createItem") val createItem: CreateItemRequest? = null
)

@JsonClass(generateAdapter = true)
data class BatchUpdateRequest(
    @Json(name = "requests") val requests: List<FormRequestItem>,
    @Json(name = "includeFormInResponse") val includeFormInResponse: Boolean = true
)

@JsonClass(generateAdapter = true)
data class CreateItemReply(
    @Json(name = "itemId") val itemId: String? = null,
    @Json(name = "questionId") val questionId: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class BatchUpdateReply(
    @Json(name = "createItem") val createItem: CreateItemReply? = null
)

@JsonClass(generateAdapter = true)
data class FormInfo(
    @Json(name = "title") val title: String? = null,
    @Json(name = "documentTitle") val documentTitle: String? = null,
    @Json(name = "description") val description: String? = null
)

@JsonClass(generateAdapter = true)
data class FormResponse(
    @Json(name = "formId") val formId: String,
    @Json(name = "info") val info: FormInfo? = null,
    @Json(name = "items") val items: List<FormItem>? = null,
    @Json(name = "revisionId") val revisionId: String? = null,
    @Json(name = "responderUri") val responderUri: String? = null
)

@JsonClass(generateAdapter = true)
data class BatchUpdateResponse(
    @Json(name = "form") val form: FormResponse? = null,
    @Json(name = "replies") val replies: List<BatchUpdateReply>? = null
)

@JsonClass(generateAdapter = true)
data class TextAnswerValue(
    @Json(name = "value") val value: String? = null
)

@JsonClass(generateAdapter = true)
data class TextAnswers(
    @Json(name = "answers") val answers: List<TextAnswerValue>? = null
)

@JsonClass(generateAdapter = true)
data class FormAnswer(
    @Json(name = "questionId") val questionId: String? = null,
    @Json(name = "textAnswers") val textAnswers: TextAnswers? = null
)

@JsonClass(generateAdapter = true)
data class FormResponseSubmission(
    @Json(name = "responseId") val responseId: String,
    @Json(name = "createTime") val createTime: String? = null,
    @Json(name = "lastSubmittedTime") val lastSubmittedTime: String? = null,
    @Json(name = "answers") val answers: Map<String, FormAnswer>? = null
)

@JsonClass(generateAdapter = true)
data class ListFormResponsesResponse(
    @Json(name = "responses") val responses: List<FormResponseSubmission>? = null,
    @Json(name = "nextPageToken") val nextPageToken: String? = null
)
