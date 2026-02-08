# LinkedIn Job Tracker Pro

A modern Android app built with Jetpack Compose, Room Database, and JSoup for tracking LinkedIn job applications.

## Features

### 📱 Core Functionality
- **Share Intent Integration**: Share LinkedIn job postings directly from LinkedIn app
- **Web Scraping**: Automatically scrapes job descriptions using JSoup
- **Local Storage**: Persists jobs using Room Database
- **Search**: Filter jobs by company name
- **Status Management**: Track application progress with color-coded chips
- **Swipe to Delete**: Easy job removal with swipe gesture

### 🎨 UI Features
- **Material 3 Design**: Modern UI with Material Design 3 components
- **Expandable Cards**: Tap to expand/collapse job details
- **SearchBar**: Quick search functionality in the top app bar
- **Loading Overlay**: Visual feedback during web scraping
- **Color-Coded Status**:
  - 🟢 Green: Offer
  - 🔴 Red: Rejected
  - 🟡 Yellow: Interviewing
  - 🔵 Blue: Applied
  - ⚪ Gray: Saved

## Architecture

### MVVM Pattern
- **Model**: `JobEntity` (Room Entity)
- **ViewModel**: `JobViewModel` (manages state and business logic)
- **View**: Jetpack Compose UI screens

### Project Structure
```
app/src/main/java/com/thewalkersoft/linkedin_job_tracker/
├── data/
│   ├── JobEntity.kt          # Room entity with job data
│   ├── JobDao.kt             # Database access object
│   └── JobDatabase.kt        # Room database with TypeConverters
├── scraper/
│   └── JobScraper.kt         # JSoup web scraping logic
├── viewmodel/
│   └── JobViewModel.kt       # ViewModel with StateFlows
├── ui/
│   ├── components/
│   │   ├── JobCard.kt        # Expandable job card
│   │   └── LoadingOverlay.kt # Scraping indicator
│   ├── screens/
│   │   └── JobListScreen.kt  # Main screen with search & list
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
└── MainActivity.kt           # Entry point
```

## Data Model

### JobEntity
```kotlin
@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val companyName: String,
    val jobUrl: String,
    val jobDescription: String,
    val status: JobStatus = JobStatus.SAVED,
    val timestamp: Long = System.currentTimeMillis()
)
```

### JobStatus Enum
- SAVED
- APPLIED
- INTERVIEWING
- OFFER
- REJECTED

## Database Operations

### JobDao
- `getAllJobs()`: Flow<List<JobEntity>>
- `searchJobsByCompany(query: String)`: Flow<List<JobEntity>>
- `upsertJob(job: JobEntity)`: Insert or update
- `deleteJob(jobId: Long)`: Remove job

## Web Scraping

### Scraper Logic
The `JobScraper` uses JSoup with:
- **User-Agent**: Mozilla/5.0 (Windows NT 10.0; Win64; x64)
- **Timeout**: 10 seconds
- **Selectors**:
  1. `.description__text`
  2. `meta[name=description]`
  3. `.show-more-less-html__markup`
  4. `div[class*=description]`

## State Management

### ViewModel StateFlows
- `jobs`: StateFlow<List<JobEntity>>
- `searchQuery`: StateFlow<String>
- `isScraping`: StateFlow<Boolean>

### Intent Handling
Parses shared text from LinkedIn:
- Extracts company name from text patterns
- Extracts URL using regex
- Automatically scrapes and saves job

## How to Use

### 1. Setup
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Run the app

### 2. Adding Jobs
1. Open LinkedIn app
2. Navigate to a job posting
3. Tap "Share" button
4. Select "LinkedIn Job Tracker Pro"
5. App automatically scrapes and saves the job

### 3. Managing Jobs
- **Search**: Use the search bar to filter by company
- **Expand**: Tap a card to see full description
- **Change Status**: Tap the status chip to update
- **Delete**: Swipe left on a card to remove

## Permissions

### AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### Intent Filter
```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

## Dependencies

### Gradle (libs.versions.toml)
```toml
[versions]
room = "2.8.4"
jsoup = "1.22.1"
compose-bom = "2024.09.00"

[libraries]
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }
```

## Technical Details

### Compose Features Used
- `@OptIn(ExperimentalMaterial3Api::class)`
- `SearchBar` - Top app bar with search
- `SwipeToDismissBox` - Swipe to delete
- `LazyColumn` - Efficient list rendering
- `animateContentSize()` - Smooth expand/collapse
- `FilterChip` - Status selection
- `CircularProgressIndicator` - Loading state

### Coroutines
- `viewModelScope` - ViewModel lifecycle-aware scope
- `Dispatchers.IO` - Background thread for network/database
- `Flow` - Reactive data streams
- `StateFlow` - State management

### Room Features
- `@Upsert` - Insert or update operation
- `@TypeConverters` - Enum to String conversion
- `Flow` - Observable queries
- Schema export enabled

## Testing

### Example Test Cases
1. Share a LinkedIn job URL
2. Verify job is scraped and saved
3. Search for company name
4. Change job status
5. Swipe to delete job
6. Expand/collapse card

## Future Enhancements

### Potential Features
- [ ] Export to CSV
- [ ] Calendar reminders for interviews
- [ ] Notes for each job
- [ ] Contact tracking
- [ ] Analytics dashboard
- [ ] Dark theme customization
- [ ] Backup/restore

## Troubleshooting

### Common Issues

#### Jobs not saving
- Check INTERNET permission in manifest
- Verify intent filter is correctly configured
- Check logcat for scraping errors

#### Scraping fails
- LinkedIn may have changed their HTML structure
- Update selectors in JobScraper.kt
- Check network connectivity

#### Database errors
- Clear app data and reinstall
- Check Room schema version
- Verify TypeConverters are registered

## License

This project is for educational purposes.

## Author

TheWalkerSoft

---

**Built with ❤️ using Jetpack Compose**

