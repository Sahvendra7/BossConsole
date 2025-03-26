package ai.rever.boss


enum class WorkStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

data class Work(
    val id: String,
    val description: String,
    val status: WorkStatus,
    val createdAt: String,
    val updatedAt: String
) {
    val shortDescription: String
        get() = description.truncate(20)
    val longDescription: String
        get() = description.truncate(120)
}

val works = listOf(
    Work(id = "1", description = "Prior Auth of CPT-Code 301 for Patient John Doe", status = WorkStatus.PENDING, createdAt = "2021-01-01", updatedAt = "2021-01-01"),
    Work(id = "2", description = "Triage of Patient Jane Doe, with CPT-Code 302", status = WorkStatus.COMPLETED, createdAt = "2021-01-02", updatedAt = "2021-01-02"),
    Work(id = "3", description = "Authorization of CPT-Code 303, by doctor Jane Doe", status = WorkStatus.IN_PROGRESS, createdAt = "2021-01-03", updatedAt = "2021-01-03"),
    Work(id = "4", description = "Surgery of CPT-Code, do EV/BV for Patient John Doe", status = WorkStatus.IN_PROGRESS, createdAt = "2021-01-04", updatedAt = "2021-01-04"),
    Work(id = "5", description = "Prior Auth of CPT-Code 301 for Patient John Doe", status = WorkStatus.PENDING, createdAt = "2021-01-05", updatedAt = "2021-01-05"),
    Work(id = "6", description = "Triage of Patient Jane Doe, with CPT-Code 302", status = WorkStatus.COMPLETED, createdAt = "2021-01-06", updatedAt = "2021-01-06"),
    Work(id = "7", description = "Authorization of CPT-Code 303, by doctor Jane Doe", status = WorkStatus.IN_PROGRESS, createdAt = "2021-01-07", updatedAt = "2021-01-07"),
    Work(id = "8", description = "Surgery of CPT-Code, do EV/BV for Patient John Doe", status = WorkStatus.IN_PROGRESS, createdAt = "2021-01-08", updatedAt = "2021-01-08"),
    Work(id = "9", description = "Prior Auth of CPT-Code 301 for Patient John Doe", status = WorkStatus.PENDING, createdAt = "2021-01-09", updatedAt = "2021-01-09"),
    Work(id = "10", description = "Triage of Patient Jane Doe, with CPT-Code 302", status = WorkStatus.COMPLETED, createdAt = "2021-01-10", updatedAt = "2021-01-10"),
    Work(id = "11", description = "Authorization of CPT-Code 303, by doctor Jane Doe", status = WorkStatus.IN_PROGRESS, createdAt = "2021-01-11", updatedAt = "2021-01-11"),
    Work(id = "12", description = "Surgery of CPT-Code, do EV/BV for Patient John Doe", status = WorkStatus.CANCELLED, createdAt = "2021-01-12", updatedAt = "2021-01-12")

)
