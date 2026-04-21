package com.waph1.markitnotes.data.model

data class AppConfig(
    // Filename -> Color
    var fileColors: HashMap<String, Long> = HashMap(),
    // Filenames
    var pinnedFiles: HashSet<String> = HashSet(),
    // Filename -> Timestamp
    var customTimestamps: HashMap<String, Long> = HashMap(),
)
