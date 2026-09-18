package com.ivor.ivormusic.data

import android.content.Context

/**
 * The song as the video it came from, for "Watch as video".
 *
 * **Durations are in different units on the two sides** - [Song.duration] is
 * milliseconds and [VideoItem.duration] is seconds - and nothing would catch a
 * mix-up: both are Long, so a video handed a duration a thousand times too
 * large simply draws a seek bar that never appears to move.
 */
fun Song.toVideoItem(): VideoItem = VideoItem(
    videoId = id,
    title = title,
    channelName = artist,
    thumbnailUrl = highResThumbnailUrl ?: thumbnailUrl,
    duration = duration / 1000,
    viewCount = "",
)

/**
 * Whether this song has a video behind it to watch.
 *
 * The control is conditional because the reverse handover is not symmetric
 * with "Listen as music": every video has audio, and not every song has a
 * video. An 11-character id is a YouTube video id, which is what a stream's
 * and a *download's* id both are - a downloaded track keeps the id it was
 * downloaded from, so it stays watchable given a connection. A device file's
 * id is a MediaStore row with nothing on YouTube behind it.
 *
 * Local Only disqualifies everything, because the control would otherwise open
 * a player that is forbidden from fetching anything.
 */
fun Song.hasWatchableVideo(context: Context): Boolean =
    id.length == 11 &&
        !LocalVideo.isDeviceVideoId(id) &&
        !ThemePreferences.isLocalOnly(context)
