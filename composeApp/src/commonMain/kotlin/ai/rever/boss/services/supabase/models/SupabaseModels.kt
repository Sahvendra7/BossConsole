package ai.rever.boss.services.supabase.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Example data models for Supabase integration
 * These models should match your Supabase database schema
 */

@Serializable
data class User(
    val id: String,
    val email: String,
    @SerialName("created_at")
    val createdAt: Instant? = null,
    @SerialName("updated_at")
    val updatedAt: Instant? = null,
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
data class Project(
    val id: String? = null,
    val name: String,
    val description: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("created_at")
    val createdAt: Instant? = null,
    @SerialName("updated_at")
    val updatedAt: Instant? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE
)

@Serializable
enum class ProjectStatus {
    @SerialName("active")
    ACTIVE,
    @SerialName("archived")
    ARCHIVED,
    @SerialName("deleted")
    DELETED
}

@Serializable
data class Task(
    val id: String? = null,
    val title: String,
    val description: String? = null,
    @SerialName("project_id")
    val projectId: String,
    @SerialName("assigned_to")
    val assignedTo: String? = null,
    @SerialName("created_by")
    val createdBy: String,
    @SerialName("created_at")
    val createdAt: Instant? = null,
    @SerialName("updated_at")
    val updatedAt: Instant? = null,
    @SerialName("due_date")
    val dueDate: Instant? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
    val tags: List<String> = emptyList()
)

@Serializable
enum class TaskPriority {
    @SerialName("low")
    LOW,
    @SerialName("medium")
    MEDIUM,
    @SerialName("high")
    HIGH,
    @SerialName("urgent")
    URGENT
}

@Serializable
enum class TaskStatus {
    @SerialName("todo")
    TODO,
    @SerialName("in_progress")
    IN_PROGRESS,
    @SerialName("review")
    REVIEW,
    @SerialName("done")
    DONE,
    @SerialName("cancelled")
    CANCELLED
}

@Serializable
data class FileMetadata(
    val id: String? = null,
    @SerialName("file_name")
    val fileName: String,
    @SerialName("file_size")
    val fileSize: Long,
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("storage_path")
    val storagePath: String,
    @SerialName("bucket_name")
    val bucketName: String,
    @SerialName("uploaded_by")
    val uploadedBy: String,
    @SerialName("project_id")
    val projectId: String? = null,
    @SerialName("task_id")
    val taskId: String? = null,
    @SerialName("created_at")
    val createdAt: Instant? = null
)

@Serializable
data class Activity(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("action_type")
    val actionType: String,
    val description: String,
    @SerialName("entity_type")
    val entityType: String? = null,
    @SerialName("entity_id")
    val entityId: String? = null,
    @SerialName("created_at")
    val createdAt: Instant? = null,
    val metadata: Map<String, String> = emptyMap()
)