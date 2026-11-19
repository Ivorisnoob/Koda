package com.ivor.ivormusic.data

import androidx.annotation.StringRes
import com.ivor.ivormusic.R

/**
 * The topics video Home's Explore row opens (#358), each a page YouTube
 * serves to anyone, signed in or not.
 *
 * YouTube retired its Trending page in 2025 (`FEtrending`, `FEexplore` and
 * the old `FEgaming`/`FEnews`/`FEsports` all answer HTTP 400). What its own
 * Explore menu opens now is the Hype leaderboard and a topic channel per
 * subject. Verified November 2026, anonymous WEB browse: every id below
 * answered with videos - `gridVideoRenderer` (Hype), `videoRenderer` (News,
 * Sports, Live, Learning, Fashion), `lockupViewModel` (Music) and
 * `videoCardRenderer` (Gaming); see `parseExploreVideos`.
 *
 * Nothing is stored by these, so the constants may be reordered freely.
 */
enum class ExploreTopic(val browseId: String, @StringRes val labelRes: Int) {
    HYPE("FEhype_leaderboard", R.string.explore_hype),
    MUSIC("UC-9-kyTW8ZkZNDHQJ6FgpwQ", R.string.explore_music),
    GAMING("UCOpNcN46UbXVtpKMrmU4Abg", R.string.explore_gaming),
    NEWS("UCYfdidRxbB8Qhf0Nx7ioOYw", R.string.explore_news),
    SPORTS("UCEgdi0XIXXZ-qJOFPf4JSKw", R.string.explore_sports),
    LIVE("UC4R8DWoMoI7CAwX8_LjQHig", R.string.explore_live),
    LEARNING("UCtFRv9O2AHqOZjjynzrv-xg", R.string.explore_learning),
    FASHION("UCrpQ4p1Ql_hG8rKXIKM1MOQ", R.string.explore_fashion),
}
