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
data class ChoiceOption(
    @Json(name = "value") val value: String
)

@JsonClass(generateAdapter = true)
data class ChoiceQuestion(
    @Json(name = "type") val type: String = "DROP_DOWN",
    @Json(name = "options") val options: List<ChoiceOption>? = null,
    @Json(name = "shuffle") val shuffle: Boolean = false
)

@JsonClass(generateAdapter = true)
data class Question(
    @Json(name = "questionId") val questionId: String? = null,
    @Json(name = "required") val required: Boolean = false,
    @Json(name = "textQuestion") val textQuestion: TextQuestion? = null,
    @Json(name = "choiceQuestion") val choiceQuestion: ChoiceQuestion? = null,
    @Json(name = "dateQuestion") val dateQuestion: Any? = null,
    @Json(name = "timeQuestion") val timeQuestion: Any? = null,
    @Json(name = "scaleQuestion") val scaleQuestion: Any? = null,
    @Json(name = "fileUploadQuestion") val fileUploadQuestion: Any? = null,
    @Json(name = "rowQuestion") val rowQuestion: Any? = null
)

@JsonClass(generateAdapter = true)
data class QuestionItem(
    @Json(name = "question") val question: Question? = null,
    @Json(name = "image") val image: Any? = null
)

@JsonClass(generateAdapter = true)
data class QuestionGroupItem(
    @Json(name = "questions") val questions: List<Question>? = null,
    @Json(name = "image") val image: Any? = null,
    @Json(name = "grid") val grid: Any? = null
)

@JsonClass(generateAdapter = true)
data class FormItem(
    @Json(name = "itemId") val itemId: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "questionItem") val questionItem: QuestionItem? = null,
    @Json(name = "questionGroupItem") val questionGroupItem: QuestionGroupItem? = null,
    @Json(name = "pageBreakItem") val pageBreakItem: Any? = null,
    @Json(name = "textItem") val textItem: Any? = null,
    @Json(name = "imageItem") val imageItem: Any? = null
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
data class FormResponseSubmission(
    @Json(name = "responseId") val responseId: String,
    @Json(name = "createTime") val createTime: String? = null,
    @Json(name = "lastSubmittedTime") val lastSubmittedTime: String? = null,
    @Json(name = "answers") val answers: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class ListFormResponsesResponse(
    @Json(name = "responses") val responses: List<FormResponseSubmission>? = null,
    @Json(name = "nextPageToken") val nextPageToken: String? = null
)
