# Supabase Integration for BOSS

This directory contains the Supabase integration for the BOSS application, providing cloud database, authentication, real-time subscriptions, and storage capabilities.

## Features

- **Authentication**: User sign-up, sign-in, and session management
- **Database Operations**: CRUD operations with type-safe Kotlin models
- **Real-time Subscriptions**: Subscribe to database changes
- **Storage**: File upload, download, and management
- **Secure Configuration**: Local storage of Supabase credentials

## Setup

### 1. Create a Supabase Project

1. Go to [supabase.com](https://supabase.com) and create an account
2. Create a new project
3. Note your project URL and anonymous key from the project settings

### 2. Configure BOSS

1. Launch BOSS and navigate to the Supabase panel (right sidebar, top section)
2. Click the settings icon
3. Enter your Supabase URL and anonymous key
4. Click "Save & Connect"

Your credentials are stored locally in `~/.boss/supabase_settings.json`

### 3. Set Up Database Tables

For the demo to work, create a `tasks` table in your Supabase project:

```sql
CREATE TABLE tasks (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    project_id TEXT NOT NULL,
    assigned_to TEXT,
    created_by TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    due_date TIMESTAMPTZ,
    priority TEXT DEFAULT 'medium',
    status TEXT DEFAULT 'todo',
    tags TEXT[] DEFAULT '{}'
);

-- Enable Row Level Security
ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;

-- Create a policy that allows all operations for now (adjust based on your auth setup)
CREATE POLICY "Allow all operations on tasks" ON tasks
    FOR ALL USING (true);
```

## Usage

### Basic Operations

```kotlin
// Initialize Supabase
SupabaseConfig.initialize(url, anonKey)

// Or initialize from saved settings
SupabaseSettingsManager.initializeFromSavedSettings()

// Get the service instance
val service = supabaseService

// Insert data
val task = Task(title = "New Task", projectId = "project-1", createdBy = "user-1")
val result = service.insert("tasks", task)

// Query data
val tasks = service.select<Task>("tasks") {
    eq("status", "todo")
    order("created_at", ascending = false)
}

// Update data
service.update<Task>("tasks", 
    update = buildJsonObject {
        put("status", JsonPrimitive("done"))
    }
) {
    eq("id", taskId)
}

// Delete data
service.delete("tasks") {
    eq("id", taskId)
}
```

### Real-time Subscriptions

```kotlin
val channel = service.subscribeToTable("tasks",
    onInsert = { record ->
        println("New task inserted: $record")
    },
    onUpdate = { record ->
        println("Task updated: $record")
    },
    onDelete = { oldRecord ->
        println("Task deleted: $oldRecord")
    }
)

// Don't forget to unsubscribe when done
channel.unsubscribe()
```

### Storage Operations

```kotlin
// Upload file
val fileData = "Hello, World!".toByteArray()
service.uploadFile("documents", "hello.txt", fileData)

// Download file
val downloaded = service.downloadFile("documents", "hello.txt")

// Get public URL
val url = service.getPublicUrl("documents", "hello.txt")
```

## Architecture

### Core Components

1. **SupabaseConfig.kt**: Singleton configuration and client management
2. **SupabaseService.kt**: High-level service wrapper for common operations
3. **SupabaseSettings.kt**: Settings persistence and management
4. **SupabaseModels.kt**: Data models matching your Supabase schema
5. **SupabaseSettingsDialog.kt**: UI for configuring Supabase credentials
6. **SupabaseDemo.kt**: Demo panel showing task management functionality

### Security Considerations

- Credentials are stored locally in the user's home directory
- Use Row Level Security (RLS) in Supabase for data protection
- Never expose service keys in client applications
- Use the anonymous key for client-side operations

## Extending the Integration

### Adding New Models

1. Define your model in `SupabaseModels.kt`:
```kotlin
@Serializable
data class YourModel(
    val id: String? = null,
    val name: String,
    @SerialName("created_at")
    val createdAt: Instant? = null
)
```

2. Create corresponding table in Supabase
3. Use the service methods with your new model type

### Custom Operations

Extend `SupabaseService.kt` with your custom operations:

```kotlin
suspend fun customOperation(): Result<YourModel> {
    return try {
        // Your custom logic using the Supabase client
        val result = client.from("your_table").select().decodeSingle<YourModel>()
        Result.success(result)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

## Troubleshooting

1. **Connection Issues**: Verify your URL and key are correct
2. **Permission Errors**: Check your RLS policies in Supabase
3. **Type Mismatches**: Ensure your Kotlin models match your database schema
4. **Real-time Not Working**: Enable real-time for your tables in Supabase dashboard

## Dependencies

The integration uses:
- `io.github.jan-tennert.supabase:bom:3.2.2`
- Ktor client for networking
- Kotlinx serialization for JSON handling
- Kotlinx datetime for timestamp handling